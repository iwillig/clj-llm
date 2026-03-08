(ns llm.model-catalog
  "Normalized model catalog and resolution helpers."
  (:require [clojure.string :as str]
            [llm.openai-compat :as openai-compat]))

(set! *warn-on-reflection* true)

(defrecord ModelOption [name type description])
(defrecord ModelDescriptor [id provider provider-key family aliases
                            features options attachment-types env-vars raw])

(defprotocol ModelCatalog
  (list-model-descriptors [this opts]
    "Return normalized model descriptors for the catalog source.")
  (search-model-descriptors [this opts]
    "Return normalized model descriptors filtered by query terms.")
  (resolve-model-id [this opts]
    "Resolve a concrete model ID from an exact id, alias, or query terms."))

(def option-definitions
  {::temperature
   (->ModelOption "temperature"
                  "float"
                  "What sampling temperature to use.")

   ::max-tokens
   (->ModelOption "max_tokens"
                  "int"
                  "Maximum number of tokens to generate.")

   ::top-p
   (->ModelOption "top_p"
                  "float"
                  "Use nucleus sampling instead of temperature.")

   ::frequency-penalty
   (->ModelOption "frequency_penalty"
                  "float"
                  "Penalize repeated tokens based on frequency.")

   ::presence-penalty
   (->ModelOption "presence_penalty"
                  "float"
                  "Penalize repeated topics based on presence.")

   ::stop
   (->ModelOption "stop"
                  "string|array"
                  "Stop generating when this string or sequence is encountered.")

   ::seed
   (->ModelOption "seed"
                  "int"
                  "Seed used to encourage deterministic sampling.")

   ::n
   (->ModelOption "n"
                  "int"
                  "Number of completions to generate.")

   ::json-object
   (->ModelOption "json_object"
                  "boolean"
                  "Output a valid JSON object when the provider supports it.")})

(def option-sets
  {:completion-basic [::temperature
                      ::max-tokens
                      ::top-p
                      ::frequency-penalty
                      ::presence-penalty
                      ::stop
                      ::seed
                      ::n]
   :chat-structured [::temperature
                     ::max-tokens
                     ::top-p
                     ::frequency-penalty
                     ::presence-penalty
                     ::stop
                     ::seed
                     ::json-object]
   :reasoning-basic [::max-tokens
                     ::stop
                     ::seed]})

(def alias-overrides
  {"gpt-4o" ["4o"]
   "gpt-4o-mini" ["4o-mini"]
   "gpt-4.1" ["4.1"]
   "gpt-4.1-mini" ["4.1-mini"]
   "gpt-4.1-nano" ["4.1-nano"]
   "gpt-4" ["4" "gpt4"]
   "gpt-3.5-turbo" ["3.5" "chatgpt"]
   "gpt-3.5-turbo-16k" ["3.5-16k" "chatgpt-16k"]
   "chatgpt-4o-latest" ["chatgpt-4o"]})

(defn- option-set
  [k]
  (mapv option-definitions (get option-sets k [])))

(defn- openai-family
  [model-id]
  (cond
    (re-find #"^o[134]" model-id) "OpenAI Reasoning"
    (or (str/includes? model-id "gpt")
        (str/includes? model-id "chatgpt")) "OpenAI Chat"
    (str/includes? model-id "llama") "Ollama"
    (str/includes? model-id "qwen") "Ollama"
    :else "OpenAI Compatible"))

(defn- model-features
  [model-id]
  (cond
    (re-find #"^o[134]" model-id)
    #{:streaming :tools}

    (re-find #"audio" model-id)
    #{:streaming :audio-input}

    (or (re-find #"^(gpt-4o|gpt-4\.1)" model-id)
        (re-find #"chatgpt-4o" model-id))
    #{:streaming :tools :schemas :vision}

    (or (re-find #"^gpt-4" model-id)
        (re-find #"^gpt-3\.5" model-id))
    #{:streaming}

    :else
    #{:streaming}))

(defn- attachment-types
  [model-id]
  (cond
    (re-find #"audio" model-id)
    ["audio/mpeg" "audio/wav"]

    (or (re-find #"^(gpt-4o|gpt-4\.1)" model-id)
        (re-find #"chatgpt-4o" model-id))
    ["application/pdf"
     "image/gif"
     "image/jpeg"
     "image/png"
     "image/webp"]

    :else []))

(defn- model-options
  [model-id]
  (cond
    (re-find #"^o[134]" model-id)
    (option-set :reasoning-basic)

    (or (re-find #"^(gpt-4o|gpt-4\.1)" model-id)
        (re-find #"chatgpt-4o" model-id)
        (re-find #"audio" model-id))
    (option-set :chat-structured)

    (or (re-find #"^gpt-4" model-id)
        (re-find #"^gpt-3\.5" model-id))
    (option-set :completion-basic)

    :else
    (option-set :completion-basic)))

(defn- provider-key
  [model-id]
  (if (= "Ollama" (openai-family model-id))
    "ollama"
    "openai-compatible"))

(defn- provider-env-vars
  [model-id]
  (if (= "Ollama" (openai-family model-id))
    ["LLM_BASE_URL" "LLM_MODEL" "OLLAMA_MODEL"]
    ["LLM_API_KEY" "LLM_BASE_URL" "LLM_MODEL"]))

(defn raw-model->descriptor
  "Convert a provider model map into a normalized descriptor."
  [{:keys [id] :as raw}]
  (let [provider-key-value (provider-key id)]
    (->ModelDescriptor id
                       :openai-compatible
                       provider-key-value
                       (openai-family id)
                       (get alias-overrides id [])
                       (sort (model-features id))
                       (model-options id)
                       (attachment-types id)
                       (provider-env-vars id)
                       (assoc raw :provider-key provider-key-value))))

(defn matches-query-terms?
  "Return true when the descriptor matches all query terms."
  [descriptor query-terms]
  (let [haystack (->> (concat [(:id descriptor)
                               (name (:provider descriptor))
                               (:provider-key descriptor)
                               (:family descriptor)]
                              (:aliases descriptor)
                              (:env-vars descriptor)
                              (map :name (:options descriptor))
                              (map name (:features descriptor))
                              (:attachment-types descriptor))
                      (remove nil?)
                      (map str)
                      (map str/lower-case))
        terms (map str/lower-case query-terms)]
    (every? (fn [term]
              (some #(str/includes? % term) haystack))
            terms)))

(defn sort-models
  "Sort descriptors for deterministic display and resolution."
  [descriptors]
  (sort-by (juxt #(count (:id %)) :id) descriptors))

(defrecord OpenAICompatCatalog [provider-opts]
  ModelCatalog
  (list-model-descriptors [_ opts]
    (->> (openai-compat/list-models (merge provider-opts opts))
         :data
         (map raw-model->descriptor)
         sort-models
         vec))
  (search-model-descriptors [this {:keys [query-terms] :as opts}]
    (let [descriptors (list-model-descriptors this opts)]
      (if (seq query-terms)
        (->> descriptors
             (filter #(matches-query-terms? % query-terms))
             sort-models
             vec)
        descriptors)))
  (resolve-model-id [this {:keys [model query-terms] :as opts}]
    (let [descriptors (list-model-descriptors this opts)]
      (cond
        (seq model)
        (let [exact-match (some #(when (= model (:id %)) %) descriptors)
              alias-match (some #(when (some #{model} (:aliases %)) %) descriptors)]
          (or (:id exact-match)
              (:id alias-match)
              model))

        (seq query-terms)
        (let [matches (search-model-descriptors this {:query-terms query-terms})]
          (or (:id (first matches))
              (throw (ex-info "No models matched query"
                              {:query-terms query-terms}))))

        :else nil))))

(defn catalog
  "Create the default model catalog implementation."
  ([]
   (->OpenAICompatCatalog {}))
  ([provider-opts]
   (->OpenAICompatCatalog provider-opts)))

(defn list-models
  "List normalized model descriptors."
  ([]
   (list-models {}))
  ([opts]
   (list-model-descriptors (catalog opts) opts)))

(defn search-models
  "Search normalized model descriptors by query terms."
  [opts]
  (search-model-descriptors (catalog opts) opts))

(defn resolve-model
  "Resolve a concrete model id from an exact id, alias, or query terms."
  [opts]
  (resolve-model-id (catalog opts) opts))

(defn descriptor->map
  "Convert a model descriptor record into a JSON-friendly map." 
  [descriptor]
  {:id (:id descriptor)
   :provider (:provider descriptor)
   :provider_key (:provider-key descriptor)
   :family (:family descriptor)
   :aliases (:aliases descriptor)
   :features (mapv name (:features descriptor))
   :options (mapv (fn [{:keys [name type description]}]
                    {:name name
                     :type type
                     :description description})
                  (:options descriptor))
   :attachment_types (:attachment-types descriptor)
   :env_vars (:env-vars descriptor)
   :raw (:raw descriptor)})

(defn format-model-summary
  "Format a one-line human-readable model summary."
  [descriptor]
  (str (:family descriptor)
       ": "
       (:id descriptor)
       (when (seq (:aliases descriptor))
         (str " (aliases: "
              (str/join ", " (:aliases descriptor))
              ")"))))

(defn format-model-options
  "Format supported options for human-readable CLI output."
  [descriptor]
  (when (seq (:options descriptor))
    (str/join
     "\n"
     (concat ["  Options:"]
             (map (fn [{:keys [name type description]}]
                    (str "    " name ": " type
                         (when (seq description)
                           (str "\n      " description))))
                  (:options descriptor))))))

(defn format-model-details
  "Format a detailed human-readable model description."
  [descriptor]
  (str/join
   "\n"
   (remove nil?
           [(format-model-summary descriptor)
            (str "  Provider key: " (:provider-key descriptor))
            (when (seq (:features descriptor))
              (str "  Features: "
                   (str/join ", " (map name (:features descriptor)))))
            (when (seq (:attachment-types descriptor))
              (str "  Attachment types: "
                   (str/join ", " (:attachment-types descriptor))))
            (when (seq (:env-vars descriptor))
              (str "  Env vars: "
                   (str/join ", " (:env-vars descriptor))))
            (format-model-options descriptor)])))
