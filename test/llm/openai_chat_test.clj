(ns llm.openai-chat-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm :as llm]
            [llm.cli :as cli]
            [llm.default-tools :as default-tools]
            [llm.model-catalog]
            [llm.openai-chat :as openai-chat]
            [llm.openai-compat]
            [llm.protocols :as protocols]
            [llm.types :as types]
            [mockfn.macros :refer [verifying]]
            [mockfn.matchers :refer [exactly]]))

(defrecord StubTransport [response requests])

(extend-type StubTransport
  protocols/Transport
  (get-json [_ _]
    (throw (ex-info "Not implemented" {})))
  (post-json [this request]
    (when-let [requests (:requests this)]
      (swap! requests conj request))
    (:response this))
  (post-json-stream [_ _ _]
    (throw (ex-info "Not implemented" {}))))

(deftest request->chat-body-test
  (let [tool (first (default-tools/tools))
        schema {:type "object"
                :properties {:name {:type "string"}}
                :required ["name"]}
        body (openai-chat/request->chat-body
              {:model "model-a"
               :messages [{:role "user" :content "hi"}]
               :tools [tool]
               :stream? false
               :schema schema})]
    (is (= "model-a" (:model body)))
    (is (= [{:role "user" :content "hi"}] (:messages body)))
    (is (= "llm_version"
           (get-in body [:tools 0 :function :name])))
    (is (= {:type "json_schema"
            :json_schema {:name "response"
                          :schema schema}}
           (:response_format body)))))

(deftest provider-complete-test
  (let [requests (atom [])
        provider (openai-chat/make-provider
                  {:transport (->StubTransport
                               {:id "chatcmpl-1"
                                :created 1772808777
                                :model "gpt-test"
                                :choices [{:finish_reason "stop"
                                           :message {:role "assistant"
                                                     :content "Done"}}]}
                               requests)})
        response (protocols/complete provider
                                     {:messages [{:role "user"
                                                  :content "hello"}]
                                      :tools [(first (default-tools/tools))]})]
    (testing "normalizes response"
      (is (= "Done" (:response response)))
      (is (= :openai-chat (:provider response))))
    (testing "serializes chat request"
      (is (= "/chat/completions"
             (subs (get-in (first @requests) [:url])
                   (- (count (get-in (first @requests) [:url]))
                      (count "/chat/completions"))))))))

(deftest run-schema-prompt-command-test
  (let [requests (atom [])
        provider (openai-chat/make-provider
                  {:transport (->StubTransport
                               {:id "chatcmpl-1"
                                :created 1772808777
                                :model "gpt-4o-mini"
                                :choices [{:finish_reason "stop"
                                           :message {:role "assistant"
                                                     :content "{\"name\":\"Fido\"}"}}]}
                               requests)})]
    (with-redefs [cli/stdin-available? (constantly false)
                  llm.model-catalog/resolve-model (fn [_] "gpt-4o-mini")
                  llm.model-catalog/list-models (fn [_]
                                                 [(llm.model-catalog/raw-model->descriptor
                                                   {:id "gpt-4o-mini"})])]
      (is (= "{\"name\":\"Fido\"}\n"
             (with-out-str
               (verifying [(openai-chat/make-provider {:base-url "http://example.test/v1"
                                                       :model "gpt-4o-mini"})
                            provider
                            (exactly 1)]
                 (cli/run-schema-prompt-command
                  {:prompt "invent a dog"
                   :stream false
                   :host "http://example.test/v1"
                   :model "gpt-4o-mini"
                   :schema "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}"
                   :json false})))))
      (is (= {:type "json_schema"
              :json_schema {:name "response"
                            :schema {:type "object"
                                     :properties {:name {:type "string"}}
                                     :required ["name"]}}}
             (get-in (first @requests) [:body :response_format]))))))

(deftest llm-prompt-with-schema-uses-chat-provider-test
  (let [calls (atom [])
        provider (reify protocols/CompletionProvider
                   (complete [_ request]
                     (swap! calls conj request)
                     (types/map->CompletionResponse
                      {:provider :stub
                       :response "{\"name\":\"Fido\"}"}))
                   (complete-text [_ _] nil)
                   (complete-stream [_ _ _] nil))]
    (with-redefs [llm.openai-chat/make-provider (fn [_] provider)
                  llm.openai-compat/make-provider (fn [_]
                                                    (throw (ex-info "should not be called"
                                                                    {})))]
      (is (= "{\"name\":\"Fido\"}"
             (:response (llm/prompt {:model "gpt-4o-mini"}
                                    "invent a dog"
                                    {:schema {:type "object"
                                              :properties {:name {:type "string"}}
                                              :required ["name"]}}))))
      (is (= {:type "object"
              :properties {:name {:type "string"}}
              :required ["name"]}
             (:schema (first @calls)))))))
