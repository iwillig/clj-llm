(ns llm-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [llm :as llm]
            [llm.model-catalog :as model-catalog]
            [llm.protocols :as protocols]
            [llm.tools :as tools]
            [llm.types :as types]))

(deftest model-test
  (testing "builds default model config"
    (is (string? (:model (llm/model))))
    (is (string? (:base-url (llm/model))))
    (is (string? (:api-key (llm/model)))))
  (testing "accepts a model id string"
    (with-redefs [model-catalog/resolve-model (fn [_] "gpt-test")]
      (is (= "gpt-test"
             (:model (llm/model "gpt-test"))))))
  (testing "resolves aliases in model strings"
    (with-redefs [model-catalog/resolve-model (fn [_] "gpt-4o-mini")]
      (is (= "gpt-4o-mini"
             (:model (llm/model "4o-mini"))))))
  (testing "accepts an opts map"
    (with-redefs [model-catalog/resolve-model (fn [_] "gpt-test")]
      (is (= {:base-url "http://example.test/v1"
              :api-key "secret"
              :model "gpt-test"}
             (llm/model {:base-url "http://example.test/v1"
                         :api-key "secret"
                         :model "gpt-test"})))))
  (testing "resolves query terms in opts maps"
    (with-redefs [model-catalog/resolve-model (fn [_] "gpt-4o-mini")]
      (is (= "gpt-4o-mini"
             (:model (llm/model {:base-url "http://example.test/v1"
                                 :api-key "secret"
                                 :query-terms ["4o" "mini"]})))))))

(deftest tool-test
  (let [upper-tool (llm/tool {:name "upper"
                              :description "Convert text to uppercase."
                              :parameters {:type "object"
                                           :properties {:text {:type "string"}}
                                           :required ["text"]}
                              :invoke (fn [{:keys [text]}]
                                        (str/upper-case text))})]
    (is (= "upper" (tools/tool-name upper-tool)))
    (is (= "Convert text to uppercase."
           (tools/tool-description upper-tool)))
    (is (= {:text "PANDA"}
           {:text (tools/invoke upper-tool {:text "panda"})}))
    (is (= "upper"
           (get-in (tools/tool->spec upper-tool) [:function :name])))))

(deftest prompt-without-tools-test
  (let [requests (atom [])
        provider (reify protocols/CompletionProvider
                   (complete [_ request]
                     (swap! requests conj request)
                     (types/map->CompletionResponse
                      {:provider :stub
                       :response "plain response"}))
                   (complete-text [_ _] nil)
                   (complete-stream [_ _ _] nil))]
    (with-redefs [llm.openai-compat/make-provider (fn [_] provider)]
      (let [response (llm/prompt {:model "gpt-test"}
                                 "Say hi"
                                 {:system "Be concise"
                                  :stream false})]
        (is (= "plain response" (:response response)))
        (is (= "Say hi" (:prompt (first @requests))))
        (is (= "Be concise" (:system (first @requests))))))))

(deftest prompt-with-tools-test
  (let [calls (atom [])
        response (types/map->CompletionResponse
                  {:provider :stub
                   :response "tool response"})
        upper-tool (llm/tool {:name "upper"
                              :description "Convert text to uppercase."
                              :parameters {:type "object"
                                           :properties {:text {:type "string"}}
                                           :required ["text"]}
                              :invoke (fn [{:keys [text]}]
                                        (str/upper-case text))})]
    (with-redefs [llm.openai-chat/make-provider (fn [_] :chat-provider)
                  llm.tool-loop/run-tool-loop (fn [provider request available-tools]
                                                (swap! calls conj [provider request available-tools])
                                                response)]
      (is (= "tool response"
             (:response (llm/prompt {:model "gpt-test"}
                                    "Convert panda"
                                    {:tools [upper-tool]}))))
      (is (= :chat-provider (ffirst @calls)))
      (is (= "Convert panda" (get-in (first @calls) [1 :prompt])))
      (is (= [upper-tool] (get-in (first @calls) [2]))))))

(deftest prompt-text-test
  (with-redefs [llm/prompt (fn
                             ([prompt] (types/map->CompletionResponse {:response (str prompt)}))
                             ([model-or-prompt prompt] (types/map->CompletionResponse {:response prompt}))
                             ([model-config prompt opts] (types/map->CompletionResponse {:response (:system opts)})))]
    (is (= "hi" (llm/prompt-text "hi")))
    (is (= "hello" (llm/prompt-text {:model "gpt-test"} "hello")))
    (is (= "brief" (llm/prompt-text {:model "gpt-test"} "hello" {:system "brief"})))))

(deftest list-models-test
  (with-redefs [llm.openai-compat/list-models (fn [opts]
                                                {:opts opts
                                                 :data [{:id "gpt-test"}]})]
    (is (= [{:id "gpt-test"}]
           (:data (llm/list-models))))
    (is (= {:model "gpt-test"}
           (:opts (llm/list-models {:model "gpt-test"}))))))

(deftest conversation-test
  (testing "creates a conversation with an optional system message"
    (let [conversation (llm/conversation {:model "gpt-test"}
                                         {:system "Be concise"})]
      (is (= "gpt-test" (get-in conversation [:model :model])))
      (is (= [{:role "system"
               :content "Be concise"}]
             (:messages conversation))))))

(deftest converse-without-tools-test
  (let [calls (atom [])
        provider (reify protocols/CompletionProvider
                   (complete [_ request]
                     (swap! calls conj request)
                     (types/map->CompletionResponse
                      {:provider :stub
                       :response "Hello back"}))
                   (complete-text [_ _] nil)
                   (complete-stream [_ _ _] nil))
        conversation (llm/conversation {:model "gpt-test"}
                                       {:system "Be concise"})]
    (with-redefs [llm.openai-chat/make-provider (fn [_] provider)]
      (let [{:keys [conversation response]}
            (llm/converse conversation "Hello")]
        (is (= "Hello back" (:response response)))
        (is (= 3 (count (:messages conversation))))
        (is (= "user" (get-in @calls [0 :messages 1 :role])))
        (is (= "Hello" (get-in @calls [0 :messages 1 :content])))))))

(deftest converse-with-tools-test
  (let [calls (atom [])
        conversation (llm/conversation {:model "gpt-test"}
                                       {:tools [:tool-a]})
        response (types/map->CompletionResponse
                  {:provider :stub
                   :response "Used tool"})]
    (with-redefs [llm.openai-chat/make-provider (fn [_] :chat-provider)
                  llm.tool-loop/run-tool-loop (fn [provider request available-tools]
                                                (swap! calls conj [provider request available-tools])
                                                response)]
      (let [{:keys [conversation response]}
            (llm/converse conversation "Use the tool")]
        (is (= "Used tool" (:response response)))
        (is (= 2 (count (:messages conversation))))
        (is (= :chat-provider (ffirst @calls)))
        (is (= [:tool-a] (get-in (first @calls) [2])))))))
