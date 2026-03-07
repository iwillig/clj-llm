(ns llm.tool-integration-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [llm.config :as config]
            [llm.default-tools :as default-tools]
            [llm.openai-chat :as openai-chat]
            [llm.openai-compat :as openai-compat]
            [llm.protocols :as protocols]
            [llm.tool-loop :as tool-loop]))

(def integration-model
  (or (System/getenv "LLM_TOOL_TEST_MODEL")
      "qwen3.5:2b"))

(defn backend-available?
  "Return true when the configured OpenAI-compatible backend is reachable."
  []
  (try
    (boolean (:data (openai-compat/list-models {:base-url config/default-base-url
                                                :model integration-model})))
    (catch Exception _
      false)))

(defn tool-calling-supported?
  "Return true when the integration model can emit tool calls."
  []
  (try
    (let [response (protocols/complete
                    (openai-chat/make-provider {:base-url config/default-base-url
                                                :model integration-model})
                    {:messages [{:role "user"
                                 :content "Use the llm_version tool to tell me the current clj-llm version."}]
                     :tools [(first (default-tools/tools))]})]
      (seq (:tool-calls response)))
    (catch Exception _
      false)))

(deftest tool-calling-integration-test
  (if-not (backend-available?)
    (testing "backend unavailable"
      (is true "Skipping tool integration test because backend is unavailable"))
    (if-not (tool-calling-supported?)
      (testing "tool calling unavailable"
        (is true "Skipping tool integration test because model did not emit tool calls"))
      (testing "tool loop works against the real backend"
        (let [response (tool-loop/run-tool-loop
                        (openai-chat/make-provider {:base-url config/default-base-url
                                                    :model integration-model})
                        {:prompt "Use the llm_version tool to tell me the current clj-llm version."
                         :max-tool-rounds 3}
                        [(first (default-tools/tools))])]
          (is (string? (:response response)))
          (is (str/includes? (str/lower-case (:response response)) "0.1.0")))))))
