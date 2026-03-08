(ns llm.model-catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm.model-catalog :as model-catalog]
            [llm.openai-compat]))

(def raw-models
  [{:id "gpt-4o-mini"}
   {:id "gpt-4o"}
   {:id "gpt-4.1-mini"}
   {:id "llama3.2:latest"}])

(deftest raw-model->descriptor-test
  (let [descriptor (model-catalog/raw-model->descriptor {:id "gpt-4o-mini"})]
    (is (= "gpt-4o-mini" (:id descriptor)))
    (is (= :openai-compatible (:provider descriptor)))
    (is (= "openai-compatible" (:provider-key descriptor)))
    (is (= "OpenAI Chat" (:family descriptor)))
    (is (= ["4o-mini"] (:aliases descriptor)))
    (is (some #{:tools} (:features descriptor)))
    (is (some #{:schemas} (:features descriptor)))
    (is (some #{:vision} (:features descriptor)))
    (is (= ["temperature"
            "max_tokens"
            "top_p"
            "frequency_penalty"
            "presence_penalty"
            "stop"
            "seed"
            "json_object"]
           (map :name (:options descriptor))))))

(deftest matches-query-terms-test
  (let [descriptor (model-catalog/raw-model->descriptor {:id "gpt-4o-mini"})]
    (is (true? (model-catalog/matches-query-terms? descriptor ["4o" "mini"])))
    (is (false? (model-catalog/matches-query-terms? descriptor ["4o" "audio"])))))

(deftest sort-models-test
  (let [descriptors (mapv model-catalog/raw-model->descriptor raw-models)]
    (is (= ["gpt-4o" "gpt-4o-mini" "gpt-4.1-mini" "llama3.2:latest"]
           (map :id (model-catalog/sort-models descriptors))))))

(deftest catalog-search-and-resolve-test
  (let [catalog (model-catalog/->OpenAICompatCatalog {:transport :stub})]
    (with-redefs [llm.openai-compat/list-models (fn [_]
                                                  {:data raw-models})]
      (testing "search matches all terms"
        (is (= ["gpt-4o-mini"]
               (map :id
                    (model-catalog/search-model-descriptors
                     catalog
                     {:query-terms ["4o" "mini"]})))))
      (testing "resolve exact id"
        (is (= "gpt-4o"
               (model-catalog/resolve-model-id catalog {:model "gpt-4o"}))))
      (testing "resolve alias"
        (is (= "gpt-4o-mini"
               (model-catalog/resolve-model-id catalog {:model "4o-mini"}))))
      (testing "resolve shortest query match"
        (is (= "gpt-4o"
               (model-catalog/resolve-model-id catalog {:query-terms ["4o"]}))))
      (testing "unknown query throws"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"No models matched query"
             (model-catalog/resolve-model-id catalog {:query-terms ["does-not-exist"]})))))))

(deftest model-family-and-option-selection-test
  (let [reasoning (model-catalog/raw-model->descriptor {:id "o1-mini"})
        audio (model-catalog/raw-model->descriptor {:id "gpt-4o-audio-preview"})
        ollama (model-catalog/raw-model->descriptor {:id "llama3.2:latest"})]
    (is (= "OpenAI Reasoning" (:family reasoning)))
    (is (= ["max_tokens" "stop" "seed"]
           (map :name (:options reasoning))))
    (is (some #{:audio-input} (:features audio)))
    (is (= ["audio/mpeg" "audio/wav"]
           (:attachment-types audio)))
    (is (= "Ollama" (:family ollama)))
    (is (= "ollama" (:provider-key ollama)))
    (is (= ["LLM_BASE_URL" "LLM_MODEL" "OLLAMA_MODEL"]
           (:env-vars ollama)))))

(deftest descriptor->map-test
  (let [descriptor (model-catalog/raw-model->descriptor {:id "gpt-4o-mini"})
        descriptor-map (model-catalog/descriptor->map descriptor)]
    (is (= "gpt-4o-mini" (:id descriptor-map)))
    (is (= "openai-compatible" (:provider_key descriptor-map)))
    (is (= ["4o-mini"] (:aliases descriptor-map)))
    (is (some #{"tools"} (:features descriptor-map)))
    (is (map? (:raw descriptor-map)))))

(deftest model-supports-feature-test
  (let [schema-model (model-catalog/raw-model->descriptor {:id "gpt-4o-mini"})
        plain-model (model-catalog/raw-model->descriptor {:id "gpt-3.5-turbo"})]
    (is (true? (model-catalog/model-supports-feature? schema-model :schemas)))
    (is (false? (model-catalog/model-supports-feature? plain-model :schemas)))))

(deftest formatters-test
  (let [descriptor (model-catalog/raw-model->descriptor {:id "gpt-4o-mini"})]
    (is (= "OpenAI Chat: gpt-4o-mini (aliases: 4o-mini)"
           (model-catalog/format-model-summary descriptor)))
    (is (.contains (model-catalog/format-model-details descriptor)
                   "Provider key:"))
    (is (.contains (model-catalog/format-model-details descriptor)
                   "Features:"))
    (is (.contains (model-catalog/format-model-details descriptor)
                   "Options:"))))
