(ns llm.openai-chat
  "OpenAI-compatible chat provider with tool support."
  (:require [jsonista.core :as json]
            [llm.config :as config]
            [llm.protocols :as protocols]
            [llm.tools :as tools]
            [llm.transport.jdk-http :as jdk-http]
            [llm.types :as types]))

(def default-base-url config/default-base-url)
(def default-api-key config/default-api-key)
(def default-model config/default-model)

(def json-object-mapper
  (json/object-mapper {}))

(defrecord OpenAIChatClient [base-url api-key transport])
(defrecord OpenAIChatProvider [client default-model])

(defn make-client
  "Create an OpenAI-compatible chat client."
  ([]
   (->OpenAIChatClient default-base-url
                       default-api-key
                       (jdk-http/make-transport)))
  ([base-url api-key]
   (->OpenAIChatClient base-url
                       api-key
                       (jdk-http/make-transport)))
  ([base-url api-key transport]
   (->OpenAIChatClient base-url api-key transport)))

(defn make-provider
  "Create an OpenAI-compatible chat completion provider."
  ([]
   (->OpenAIChatProvider (make-client) default-model))
  ([opts]
   (let [{:keys [base-url api-key model]} (config/resolve-provider-config opts)
         transport (or (:transport opts)
                       (jdk-http/make-transport))]
     (->OpenAIChatProvider
      (make-client base-url api-key transport)
      model))))

(defn chat-completion-url
  "Build the OpenAI-compatible chat completions endpoint URL."
  [{:keys [base-url]}]
  (str base-url "/chat/completions"))

(defn tool->openai-spec
  "Convert a tool implementation into an OpenAI-compatible tool spec."
  [tool]
  (tools/tool->spec tool))

(defn request->chat-body
  "Convert a completion request into an OpenAI-compatible chat request body."
  [{:keys [messages model tools tool-choice stream? options]}]
  (cond-> {:model model
           :messages messages
           :stream (boolean stream?)}
    (seq tools)
    (assoc :tools (mapv tool->openai-spec tools))

    tool-choice
    (assoc :tool_choice tool-choice)

    (contains? options :temperature)
    (assoc :temperature (Float/parseFloat (str (:temperature options))))

    (contains? options :top_p)
    (assoc :top_p (Float/parseFloat (str (:top_p options))))))

(defn request->http-request
  "Build the low-level HTTP request map for chat completions."
  [{:keys [client] :as provider}
   {:keys [model] :as request}]
  {:url (chat-completion-url client)
   :headers {"authorization" (str "Bearer " (:api-key client))}
   :body (request->chat-body
          (assoc request :model (or model (:default-model provider))))})

(defn normalize-tool-call
  "Normalize an assistant tool call into a ToolCall record."
  [raw]
  (types/map->ToolCall
   {:id (:id raw)
    :name (get-in raw [:function :name])
    :arguments (get-in raw [:function :arguments])
    :raw raw}))

(defn normalize-chat-response
  "Normalize an OpenAI-compatible chat response."
  [raw]
  (let [message (get-in raw [:choices 0 :message])
        tool-calls (mapv normalize-tool-call (:tool_calls message))]
    (types/map->CompletionResponse
     {:provider :openai-chat
      :model (:model raw)
      :response (or (:content message) "")
      :done true
      :done-reason (get-in raw [:choices 0 :finish_reason])
      :created-at (:created raw)
      :total-duration nil
      :raw raw
      :tool-calls tool-calls
      :messages [(types/map->ChatMessage
                  {:role (:role message)
                   :content (:content message)
                   :tool-calls tool-calls
                   :raw message})]})))

(extend-type OpenAIChatProvider
  protocols/CompletionProvider
  (complete [provider request]
    (-> (request->http-request provider (assoc request :stream? false))
        ((fn [http-request]
           (protocols/post-json (get-in provider [:client :transport])
                                http-request)))
        normalize-chat-response))
  (complete-text [provider request]
    (:response (protocols/complete provider request)))
  (complete-stream [_provider _request _on-event]
    (throw (ex-info "Streaming chat tool support is not implemented"
                    {}))))
