(ns llm.tool-loop
  "Tool execution loop for chat-based providers."
  (:require [jsonista.core :as json]
            [llm.protocols :as protocols]
            [llm.tools :as tools]
            [llm.types :as types]))

(def json-object-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(defn initial-messages
  "Build the initial chat messages for a prompt request."
  [{:keys [system prompt]}]
  (cond-> []
    system
    (conj {:role "system"
           :content system})

    prompt
    (conj {:role "user"
           :content prompt})))

(defn parse-tool-arguments
  "Parse a raw tool argument JSON string into a Clojure map."
  [arguments]
  (cond
    (map? arguments) arguments
    (string? arguments)
    (if (clojure.string/blank? arguments)
      {}
      (json/read-value arguments json-object-mapper))
    (nil? arguments) {}
    :else
    (throw (ex-info "Unsupported tool arguments"
                    {:arguments arguments}))))

(defn assistant-tool-call-message
  "Convert a completion response into an assistant tool-call message."
  [response]
  {:role "assistant"
   :content (not-empty (:response response))
   :tool_calls (mapv (fn [{:keys [id name arguments]}]
                       {:id id
                        :type "function"
                        :function {:name name
                                   :arguments arguments}})
                     (:tool-calls response))})

(defn execute-tool-call
  "Execute a single tool call and return a ToolResult record."
  [tool-map {:keys [id name arguments] :as tool-call}]
  (let [tool (get tool-map name)]
    (try
      (if-not tool
        (types/map->ToolResult
         {:tool-call-id id
          :name name
          :content (str "Tool not found: " name)
          :raw tool-call
          :error? true})
        (types/map->ToolResult
         {:tool-call-id id
          :name name
          :content (tools/result->content
                    (tools/invoke tool (parse-tool-arguments arguments)))
          :raw tool-call
          :error? false}))
      (catch Exception ex
        (types/map->ToolResult
         {:tool-call-id id
          :name name
          :content (tools/result->content
                    {:error (.getMessage ex)
                     :ex-data (ex-data ex)})
          :raw tool-call
          :error? true})))))

(defn tool-result->message
  "Convert a ToolResult into a chat tool message."
  [{:keys [tool-call-id content name error?]}]
  {:role "tool"
   :tool_call_id tool-call-id
   :name name
   :content content
   :error error?})

(defn run-tool-loop
  "Run a prompt with tools until the model stops requesting tool calls."
  [provider request available-tools]
  (let [tool-map (into {}
                       (map (fn [tool]
                              [(tools/tool-name tool) tool]))
                       available-tools)
        max-tool-rounds (or (:max-tool-rounds request) 8)]
    (loop [messages (or (:messages request)
                        (initial-messages request))
           remaining-rounds max-tool-rounds]
      (let [response (protocols/complete provider
                                         (assoc request
                                                :messages messages
                                                :tools available-tools))]
        (if (seq (:tool-calls response))
          (if (pos? remaining-rounds)
            (let [tool-results (mapv #(execute-tool-call tool-map %)
                                     (:tool-calls response))]
              (recur (into messages
                           (concat [(assistant-tool-call-message response)]
                                   (map tool-result->message tool-results)))
                     (dec remaining-rounds)))
            (throw (ex-info "Tool loop exceeded max rounds"
                            {:max-tool-rounds max-tool-rounds})))
          response)))))
