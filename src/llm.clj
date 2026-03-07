(ns llm
  "High-level public API for prompting models and running tool-enabled conversations."
  (:require [llm.config :as config]
            [llm.openai-chat :as openai-chat]
            [llm.openai-compat :as openai-compat]
            [llm.protocols :as protocols]
            [llm.tool-loop :as tool-loop]
            [llm.tools :as tools]
            [llm.types :as types]))

(defrecord FunctionTool [name description parameters invoke-fn])

(extend-type FunctionTool
  tools/Tool
  (tool-name [this]
    (:name this))
  (tool-description [this]
    (:description this))
  (tool-parameters [this]
    (:parameters this))
  (invoke [this args]
    ((:invoke-fn this) args))
  (tool->spec [this]
    {:type "function"
     :function {:name (tools/tool-name this)
                :description (tools/tool-description this)
                :parameters (tools/tool-parameters this)}}))

(defn model
  "Build a normalized model configuration map for the public API."
  ([]
   (config/resolve-provider-config {}))
  ([model-or-opts]
   (if (string? model-or-opts)
     (config/resolve-provider-config {:model model-or-opts})
     (config/resolve-provider-config model-or-opts))))

(defn tool
  "Create a model-callable tool from plain data and an invoke function."
  [{:keys [name description parameters invoke]}]
  (when-not (seq name)
    (throw (ex-info "Tool name is required" {})))
  (when-not (string? description)
    (throw (ex-info "Tool description is required"
                    {:name name})))
  (when-not (map? parameters)
    (throw (ex-info "Tool parameters must be a map"
                    {:name name
                     :parameters parameters})))
  (when-not (fn? invoke)
    (throw (ex-info "Tool invoke must be a function"
                    {:name name
                     :invoke invoke})))
  (->FunctionTool name description parameters invoke))

(defn list-models
  "List models from the configured endpoint."
  ([]
   (openai-compat/list-models {}))
  ([model-or-opts]
   (openai-compat/list-models (if (string? model-or-opts)
                                {:model model-or-opts}
                                model-or-opts))))

(defn- request-from-prompt
  [prompt {:keys [system stream raw options tools tool-choice max-tool-rounds messages]}]
  (types/map->CompletionRequest
   {:prompt prompt
    :system system
    :stream? stream
    :raw? raw
    :options options
    :tools tools
    :tool-choice tool-choice
    :max-tool-rounds max-tool-rounds
    :messages messages}))

(defn- completion-provider
  [model-config]
  (openai-compat/make-provider model-config))

(defn- chat-provider
  [model-config]
  (openai-chat/make-provider model-config))

(defn prompt
  "Execute a prompt using the high-level public API.

  When `:tools` are provided, this uses the chat provider and tool loop.
  Otherwise it uses the plain completion provider.
  "
  ([prompt-text]
   (prompt (model) prompt-text nil))
  ([model-or-prompt prompt-text]
   (if (or (map? model-or-prompt)
           (string? model-or-prompt))
     (prompt (if (string? model-or-prompt)
               (model model-or-prompt)
               (model model-or-prompt))
             prompt-text
             nil)
     (prompt (model) model-or-prompt prompt-text)))
  ([model-config prompt-text opts]
   (let [resolved-model (model model-config)
         request (assoc (request-from-prompt prompt-text opts)
                        :model (:model resolved-model))]
     (if (seq (:tools request))
       (tool-loop/run-tool-loop (chat-provider resolved-model)
                                request
                                (:tools request))
       (protocols/complete (completion-provider resolved-model)
                           request)))))

(defn prompt-text
  "Execute a prompt and return only the response text."
  ([prompt-value]
   (:response (prompt prompt-value)))
  ([model-or-prompt prompt-or-opts]
   (:response (prompt model-or-prompt prompt-or-opts)))
  ([model-config prompt-value opts]
   (:response (prompt model-config prompt-value opts))))

(defn chain
  "Execute a prompt with optional tools using the high-level API.

  This is currently a convenience wrapper over `prompt`.
  "
  ([model-config prompt-text opts]
   (prompt model-config prompt-text opts)))

(defn conversation
  "Create an immutable conversation value."
  ([]
   (conversation (model) nil))
  ([model-or-opts]
   (conversation model-or-opts nil))
  ([model-or-opts {:keys [system tools] :as opts}]
   {:model (model model-or-opts)
    :messages (cond-> []
                system
                (conj {:role "system"
                       :content system}))
    :tools tools
    :opts (dissoc opts :system :tools)}))

(defn converse
  "Send a prompt in the context of an immutable conversation.

  Returns a map with `:conversation` and `:response`.
  "
  ([conversation prompt-text]
   (converse conversation prompt-text nil))
  ([conversation prompt-text opts]
   (let [messages (conj (:messages conversation)
                        {:role "user"
                         :content prompt-text})
         request (types/map->CompletionRequest
                  (merge (:opts conversation)
                         opts
                         {:prompt prompt-text
                          :messages messages
                          :tools (or (:tools opts)
                                     (:tools conversation))
                          :model (get-in conversation [:model :model])}))
         response (if (seq (:tools request))
                    (tool-loop/run-tool-loop (chat-provider (:model conversation))
                                             request
                                             (:tools request))
                    (protocols/complete (chat-provider (:model conversation))
                                        request))
         assistant-message {:role "assistant"
                            :content (:response response)}]
     {:conversation (assoc conversation
                           :messages (conj messages assistant-message))
      :response response})))
