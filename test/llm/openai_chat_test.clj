(ns llm.openai-chat-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm.default-tools :as default-tools]
            [llm.openai-chat :as openai-chat]
            [llm.protocols :as protocols]))

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
        body (openai-chat/request->chat-body
              {:model "model-a"
               :messages [{:role "user" :content "hi"}]
               :tools [tool]
               :stream? false})]
    (is (= "model-a" (:model body)))
    (is (= [{:role "user" :content "hi"}] (:messages body)))
    (is (= "llm_version"
           (get-in body [:tools 0 :function :name])))))

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
