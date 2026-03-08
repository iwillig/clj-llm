(ns llm.cli
  "CLI wiring for the llm command."
  (:require [cli-matic.core :as cli]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jsonista.core :as json]
            [llm.model-catalog :as model-catalog]
            [llm.openai-chat :as openai-chat]
            [llm.openai-compat :as openai-compat]
            [llm.protocols :as protocols]
            [llm.tool-loop :as tool-loop]
            [llm.tool-registry :as tool-registry]
            [llm.types :as types])
  (:import (java.io BufferedInputStream PushbackInputStream)))

(set! *warn-on-reflection* true)

(def pretty-json-object-mapper
  (json/object-mapper {:pretty true}))

(def schema-json-object-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(def prompt-shorthand-exclusions
  #{"prompt" "models" "-?" "--help"})

(defn stdin-available?
  "Return true when stdin has buffered input ready to read."
  []
  (let [input-stream System/in]
    (cond
      (instance? PushbackInputStream input-stream)
      (pos? (.available ^PushbackInputStream input-stream))

      (instance? BufferedInputStream input-stream)
      (pos? (.available ^BufferedInputStream input-stream))

      :else
      (pos? (.available input-stream)))))

(defn read-stdin
  "Read stdin when buffered input is available and return text or nil."
  []
  (when (stdin-available?)
    (let [input (slurp *in*)]
      (when-not (str/blank? input)
        input))))

(defn resolve-prompt
  "Resolve the final prompt from stdin and positional input."
  [{:keys [prompt stdin]}]
  (let [prompt-text (not-empty prompt)
        stdin-text (not-empty stdin)]
    (cond
      (and stdin-text prompt-text) (str stdin-text "\n\n" prompt-text)
      stdin-text stdin-text
      prompt-text prompt-text
      :else nil)))

(defn option-pairs->map
  "Convert repeated CLI option values into a request option map."
  [option-values]
  (when (seq option-values)
    (let [pairs (partition 2 option-values)]
      (into {}
            (map (fn [[k v]] [(keyword k) v]))
            pairs))))

(def schema-type-map
  {"str" "string"
   "string" "string"
   "int" "integer"
   "integer" "integer"
   "float" "number"
   "number" "number"
   "bool" "boolean"
   "boolean" "boolean"})

(defn- parse-schema-field
  [field-spec]
  (let [trimmed (str/trim field-spec)
        [_ raw-name raw-type raw-description]
        (re-matches #"^([A-Za-z0-9_-]+)(?:\s+([A-Za-z0-9_-]+))?(?:\s*:\s*(.*))?$"
                    trimmed)
        name (some-> raw-name str/trim)
        type (or (some-> raw-type str/lower-case schema-type-map)
                 "string")
        description (some-> raw-description str/trim not-empty)]
    (when (seq name)
      [name (cond-> {:type type}
              description
              (assoc :description description))])))

(defn concise-schema?
  "Return true when the schema value looks like concise schema DSL."
  [schema-value]
  (and (seq schema-value)
       (let [trimmed (str/trim schema-value)]
         (and (not (str/starts-with? trimmed "{"))
              (not (str/starts-with? trimmed "["))))))

(defn concise-schema->json-schema
  "Convert concise schema DSL to a JSON-schema-compatible map."
  [schema-value]
  (let [fields (->> (str/split schema-value #",|\n")
                    (map parse-schema-field)
                    (remove nil?)
                    vec)]
    {:type "object"
     :properties (into {} fields)
     :required (mapv first fields)}))

(defn wrap-multi-schema
  "Wrap an item schema in the object-with-items array shape used by schema-multi."
  [schema]
  {:type "object"
   :properties {:items {:type "array"
                        :items schema}}
   :required ["items"]})

(defn parse-schema
  "Parse a schema from concise DSL, inline JSON, or a JSON file path."
  [schema-value]
  (when (seq schema-value)
    (let [trimmed (str/trim schema-value)
          schema-source (if (.exists (io/file trimmed))
                          (slurp trimmed)
                          trimmed)]
      (if (concise-schema? schema-source)
        (concise-schema->json-schema schema-source)
        (json/read-value schema-source schema-json-object-mapper)))))

(defn ->completion-request
  "Convert CLI options into a normalized completion request record."
  [{:keys [model system stream raw prompt option stdin tool tool-max-rounds schema schema-multi]}]
  (types/map->CompletionRequest
   {:prompt (resolve-prompt {:prompt prompt
                             :stdin stdin})
    :model model
    :system system
    :stream? stream
    :raw? raw
    :options (option-pairs->map option)
    :tools tool
    :max-tool-rounds tool-max-rounds
    :schema (cond
              (seq schema-multi) (-> schema-multi parse-schema wrap-multi-schema)
              (seq schema) (parse-schema schema)
              :else nil)}))

(defn resolve-cli-model
  "Resolve the effective model id from an exact id, alias, or query terms."
  [{:keys [host model query] :as _opts}]
  (or (model-catalog/resolve-model {:base-url host
                                    :model model
                                    :query-terms query})
      model
      openai-compat/default-model))

(defn print-stream-event
  "Print a normalized stream event to stdout."
  [{:keys [type text]}]
  (case type
    :text-delta (print text)
    :done (println)
    nil))

(defn run-plain-prompt-command
  "Execute a prompt request without tool support."
  [{:keys [json host stream] :as opts}]
  (let [resolved-model (resolve-cli-model opts)
        provider (openai-compat/make-provider {:base-url host
                                               :model resolved-model})
        request (->completion-request (assoc opts
                                             :stdin (read-stdin)
                                             :model resolved-model))]
    (if stream
      (do
        (protocols/complete-stream provider request print-stream-event)
        0)
      (let [result (protocols/complete provider request)]
        (if json
          (println (json/write-value-as-string result pretty-json-object-mapper))
          (println (:response result)))
        0))))

(defn run-tool-prompt-command
  "Execute a prompt request with tool support."
  [{:keys [json host] :as opts}]
  (let [resolved-model (resolve-cli-model opts)
        provider (openai-chat/make-provider {:base-url host
                                             :model resolved-model})
        request (->completion-request (assoc opts
                                             :stdin (read-stdin)
                                             :model resolved-model))
        available-tools (tool-registry/resolve-tools (:tools request))
        result (tool-loop/run-tool-loop provider request available-tools)]
    (if json
      (println (json/write-value-as-string result pretty-json-object-mapper))
      (println (:response result)))
    0))

(defn run-schema-prompt-command
  "Execute a prompt request with schema-constrained output."
  [{:keys [json host] :as opts}]
  (let [resolved-model (resolve-cli-model opts)
        descriptor (some #(when (= resolved-model (:id %)) %)
                         (model-catalog/list-models {:base-url host}))
        _ (when-not (model-catalog/model-supports-feature? descriptor :schemas)
            (throw (ex-info "Model does not support schemas"
                            {:model resolved-model
                             :features (:features descriptor)})))
        provider (openai-chat/make-provider {:base-url host
                                             :model resolved-model})
        request (->completion-request (assoc opts
                                             :stdin (read-stdin)
                                             :model resolved-model))
        result (protocols/complete provider request)]
    (if json
      (println (json/write-value-as-string result pretty-json-object-mapper))
      (println (:response result)))
    0))

(defn run-prompt-command
  "Execute the prompt command."
  [opts]
  (let [request (->completion-request (assoc opts :stdin (read-stdin)))]
    (when-not (:prompt request)
      (throw (ex-info "Missing prompt"
                      {:opts opts})))
    (cond
      (seq (:tools request))
      (run-tool-prompt-command opts)

      (:schema request)
      (run-schema-prompt-command opts)

      :else
      (run-plain-prompt-command opts))))

(defn run-models-command
  "List models from the configured OpenAI-compatible endpoint."
  [{:keys [json host query model options]}]
  (let [descriptors (model-catalog/search-models {:base-url host
                                                  :query-terms query})
        filtered-descriptors (if (seq model)
                               (filterv (fn [descriptor]
                                          (contains? (set model) (:id descriptor)))
                                        descriptors)
                               descriptors)]
    (if json
      (println (json/write-value-as-string
                (mapv model-catalog/descriptor->map filtered-descriptors)
                pretty-json-object-mapper))
      (doseq [descriptor filtered-descriptors]
        (println (if options
                   (model-catalog/format-model-details descriptor)
                   (model-catalog/format-model-summary descriptor)))))
    0))

(def shared-opts
  [{:option "model"
    :short "m"
    :as "Model name"
    :type :string
    :default openai-compat/default-model}
   {:option "host"
    :short "h"
    :as "Base URL"
    :type :string
    :default openai-compat/default-base-url}
   {:option "json"
    :as "Print full JSON response"
    :type :with-flag
    :default false}])

(def prompt-opts
  [{:option "system"
    :short "s"
    :as "System prompt"
    :type :string}
   {:option "stream"
    :as "Stream response chunks as they arrive"
    :type :with-flag
    :default true}
   {:option "raw"
    :as "Disable provider prompt templating when supported"
    :type :with-flag}
   {:option "option"
    :short "o"
    :as "Model option name/value pair"
    :type :string
    :multiple true}
   {:option "query"
    :short "q"
    :as "Search term used to resolve a model id"
    :type :string
    :multiple true}
   {:option "tool"
    :short "T"
    :as "Enable a tool by name"
    :type :string
    :multiple true}
   {:option "tool-max-rounds"
    :as "Maximum number of tool execution rounds"
    :type :int
    :default 8}
   {:option "schema"
    :as "Inline JSON schema, concise schema DSL, or path to a JSON schema file"
    :type :string}
   {:option "schema-multi"
    :as "Concise schema DSL or JSON schema for multiple items"
    :type :string}
   {:option "prompt"
    :as "Prompt text"
    :short 0
    :type :string}])

(def models-opts
  [{:option "model"
    :short "m"
    :as "Filter by exact model id"
    :type :string
    :multiple true}
   {:option "query"
    :short "q"
    :as "Filter models by query terms"
    :type :string
    :multiple true}
   {:option "options"
    :as "Show supported model options"
    :type :with-flag
    :default false}])

(def cli-config
  {:command "clj-llm"
   :description "Small OpenAI-compatible CLI proof of concept"
   :version "0.1.0"
   :opts [{:option "host"
           :short "h"
           :as "Base URL"
           :type :string
           :default openai-compat/default-base-url}
          {:option "json"
           :as "Print full JSON response"
           :type :with-flag
           :default false}]
   :subcommands [{:command "models"
                  :description "List available models"
                  :opts (into [{:option "host"
                                :short "h"
                                :as "Base URL"
                                :type :string
                                :default openai-compat/default-base-url}
                               {:option "json"
                                :as "Print full JSON response"
                                :type :with-flag
                                :default false}]
                              models-opts)
                  :runs run-models-command}
                 {:command "prompt"
                  :description "Execute a prompt"
                  :opts (into shared-opts prompt-opts)
                  :runs run-prompt-command}]})

(defn prompt-shorthand-args
  [args]
  (let [first-arg (first args)
        subcommand? (contains? prompt-shorthand-exclusions first-arg)]
    (if (and (seq args)
             (not subcommand?)
             (not (str/starts-with? first-arg "-")))
      (into ["prompt"] args)
      args)))

(defn run-cli
  "Run the CLI with the supplied arguments."
  [args]
  (cli/run-cmd (prompt-shorthand-args args) cli-config))
