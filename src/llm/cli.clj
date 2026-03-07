(ns llm.cli
  "CLI wiring for the llm command."
  (:require [cli-matic.core :as cli]
            [clojure.string :as str]
            [jsonista.core :as json]
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

(defn ->completion-request
  "Convert CLI options into a normalized completion request record."
  [{:keys [model system stream raw prompt option stdin tool tool-max-rounds]}]
  (types/map->CompletionRequest
   {:prompt (resolve-prompt {:prompt prompt
                             :stdin stdin})
    :model model
    :system system
    :stream? stream
    :raw? raw
    :options (option-pairs->map option)
    :tools tool
    :max-tool-rounds tool-max-rounds}))

(defn print-stream-event
  "Print a normalized stream event to stdout."
  [{:keys [type text]}]
  (case type
    :text-delta (print text)
    :done (println)
    nil))

(defn run-plain-prompt-command
  "Execute a prompt request without tool support."
  [{:keys [json host model stream] :as opts}]
  (let [provider (openai-compat/make-provider {:base-url host :model model})
        request (->completion-request (assoc opts :stdin (read-stdin)))]
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
  [{:keys [json host model] :as opts}]
  (let [provider (openai-chat/make-provider {:base-url host :model model})
        request (->completion-request (assoc opts :stdin (read-stdin)))
        available-tools (tool-registry/resolve-tools (:tools request))
        result (tool-loop/run-tool-loop provider request available-tools)]
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
    (if (seq (:tools request))
      (run-tool-prompt-command opts)
      (run-plain-prompt-command opts))))

(defn run-models-command
  "List models from the configured OpenAI-compatible endpoint."
  [{:keys [json host model]}]
  (let [result (openai-compat/list-models {:base-url host :model model})]
    (if json
      (println (json/write-value-as-string result pretty-json-object-mapper))
      (doseq [entry (:data result)]
        (println (:id entry))))
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
   {:option "tool"
    :short "T"
    :as "Enable a tool by name"
    :type :string
    :multiple true}
   {:option "tool-max-rounds"
    :as "Maximum number of tool execution rounds"
    :type :int
    :default 8}
   {:option "prompt"
    :as "Prompt text"
    :short 0
    :type :string}])

(def cli-config
  {:command "clj-llm"
   :description "Small OpenAI-compatible CLI proof of concept"
   :version "0.1.0"
   :opts shared-opts
   :subcommands [{:command "models"
                  :description "List available models"
                  :opts shared-opts
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
