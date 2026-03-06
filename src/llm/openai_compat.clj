(ns llm.openai-compat
  "OpenAI-compatible completions provider implementation."
  (:require [clojure.string :as str]
            [llm.config :as config]
            [llm.protocols :as protocols]
            [llm.transport.jdk-http :as jdk-http]
            [llm.types :as types]))

(def default-base-url config/default-base-url)
(def default-api-key config/default-api-key)
(def default-model config/default-model)

(defrecord OpenAICompatClient [base-url api-key transport])
(defrecord OpenAICompatProvider [client default-model])

(defn make-client
  "Create an OpenAI-compatible client.

  Defaults to the local Ollama OpenAI-compatible API.
  "
  ([]
   (->OpenAICompatClient default-base-url
                         default-api-key
                         (jdk-http/make-transport)))
  ([base-url api-key]
   (->OpenAICompatClient base-url
                         api-key
                         (jdk-http/make-transport)))
  ([base-url api-key transport]
   (->OpenAICompatClient base-url api-key transport)))

(defn make-provider
  "Create an OpenAI-compatible completion provider."
  ([]
   (->OpenAICompatProvider (make-client) default-model))
  ([opts]
   (let [{:keys [base-url api-key model]} (config/resolve-provider-config opts)
         transport (or (:transport opts)
                       (jdk-http/make-transport))]
     (->OpenAICompatProvider
      (make-client base-url api-key transport)
      model))))

(defn- parse-float-option
  [value]
  (when (some? value)
    (Float/parseFloat (str value))))

(defn- parse-stop
  [value]
  (cond
    (nil? value) nil
    (string? value)
    (if (str/includes? value ",")
      (mapv str/trim (str/split value #","))
      value)
    (sequential? value) (vec value)
    :else (str value)))

(defn coerce-options
  "Coerce string CLI options into OpenAI-compatible request values."
  [options]
  (when (seq options)
    (cond-> {}
      (contains? options :temperature)
      (assoc :temperature (parse-float-option (:temperature options)))

      (contains? options :top_p)
      (assoc :top_p (parse-float-option (:top_p options)))

      (contains? options :max_tokens)
      (assoc :max_tokens (parse-long (str (:max_tokens options))))

      (contains? options :presence_penalty)
      (assoc :presence_penalty (parse-float-option (:presence_penalty options)))

      (contains? options :frequency_penalty)
      (assoc :frequency_penalty (parse-float-option (:frequency_penalty options)))

      (contains? options :stop)
      (assoc :stop (parse-stop (:stop options)))

      (contains? options :n)
      (assoc :n (parse-long (str (:n options))))

      (contains? options :seed)
      (assoc :seed (parse-long (str (:seed options)))))))

(defn completion-url
  "Build the OpenAI-compatible completions endpoint URL."
  [{:keys [base-url]}]
  (str base-url "/completions"))

(defn models-url
  "Build the OpenAI-compatible models endpoint URL."
  [{:keys [base-url]}]
  (str base-url "/models"))

(defn list-models
  "List models from an OpenAI-compatible endpoint."
  ([]
   (list-models {}))
  ([opts]
   (let [{:keys [base-url api-key]} (config/resolve-provider-config opts)
         transport (or (:transport opts)
                       (jdk-http/make-transport))]
     (protocols/get-json transport
                         {:url (models-url {:base-url base-url})
                          :headers {"authorization" (str "Bearer " api-key)}}))))

(defn request->body
  "Convert a completion request into an OpenAI-compatible request body."
  [{:keys [prompt model system stream? raw? options]}]
  (let [coerced-options (coerce-options options)]
    (cond-> {:model model
             :prompt (if system
                       (str system "\n\n" prompt)
                       prompt)
             :stream stream?}
      (:max_tokens coerced-options)
      (assoc :max_tokens (:max_tokens coerced-options))

      (:temperature coerced-options)
      (assoc :temperature (:temperature coerced-options))

      (:top_p coerced-options)
      (assoc :top_p (:top_p coerced-options))

      (:presence_penalty coerced-options)
      (assoc :presence_penalty (:presence_penalty coerced-options))

      (:frequency_penalty coerced-options)
      (assoc :frequency_penalty (:frequency_penalty coerced-options))

      (:stop coerced-options)
      (assoc :stop (:stop coerced-options))

      (:n coerced-options)
      (assoc :n (:n coerced-options))

      (:seed coerced-options)
      (assoc :seed (:seed coerced-options))

      (some? raw?)
      (assoc :raw raw?))))

(defn request->http-request
  "Build the low-level HTTP request map for OpenAI-compatible completions."
  [{:keys [client] :as provider}
   {:keys [model] :as request}]
  {:url (completion-url client)
   :headers {"authorization" (str "Bearer " (:api-key client))}
   :body (request->body (assoc request :model (or model (:default-model provider))))})

(defn normalize-response
  "Normalize an OpenAI-compatible completion response."
  [raw]
  (types/map->CompletionResponse
   {:provider :openai-completions
    :model (:model raw)
    :response (or (get-in raw [:choices 0 :text]) "")
    :done true
    :done-reason (get-in raw [:choices 0 :finish_reason])
    :created-at (:created raw)
    :total-duration nil
    :raw raw}))

(defn normalize-stream-event
  "Normalize an OpenAI-compatible streaming chunk into a stream event map."
  [raw]
  (let [text (or (get-in raw [:choices 0 :text]) "")
        finish-reason (get-in raw [:choices 0 :finish_reason])]
    (if finish-reason
      (types/map->StreamEvent
       {:type :done
        :response (normalize-response raw)
        :raw raw})
      (types/map->StreamEvent
       {:type :text-delta
        :text text
        :raw raw}))))

(extend-type OpenAICompatProvider
  protocols/CompletionProvider
  (complete [provider request]
    (-> (request->http-request provider (assoc request :stream? false))
        ((fn [http-request]
           (protocols/post-json (get-in provider [:client :transport])
                                http-request)))
        normalize-response))
  (complete-text [provider request]
    (:response (protocols/complete provider request)))
  (complete-stream [provider request on-event]
    (protocols/post-json-stream
     (get-in provider [:client :transport])
     (request->http-request provider (assoc request :stream? true))
     (fn [raw]
       (on-event (normalize-stream-event raw))))))
