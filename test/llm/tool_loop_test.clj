(ns llm.tool-loop-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm.default-tools :as default-tools]
            [llm.protocols :as protocols]
            [llm.tool-loop :as tool-loop]
            [llm.types :as types]))

(defrecord StubToolProvider [responses calls])

(extend-type StubToolProvider
  protocols/CompletionProvider
  (complete [_ request]
    (swap! (:calls request) conj (dissoc request :calls))
    (let [response (first @(:responses request))]
      (swap! (:responses request) subvec 1)
      response))
  (complete-text [_ _]
    (throw (ex-info "Not implemented" {})))
  (complete-stream [_ _ _]
    (throw (ex-info "Not implemented" {}))))

(deftest run-tool-loop-test
  (let [responses (atom [(types/map->CompletionResponse
                          {:provider :stub
                           :response ""
                           :tool-calls [(types/map->ToolCall
                                         {:id "call_1"
                                          :name "llm_version"
                                          :arguments "{}"})]})
                         (types/map->CompletionResponse
                          {:provider :stub
                           :response "Version is 0.1.0"
                           :tool-calls []})])
        calls (atom [])
        provider (reify protocols/CompletionProvider
                   (complete [_ request]
                     (swap! calls conj (dissoc request :responses :calls))
                     (let [response (first @responses)]
                       (swap! responses subvec 1)
                       response))
                   (complete-text [_ _] nil)
                   (complete-stream [_ _ _] nil))
        response (tool-loop/run-tool-loop provider
                                          {:prompt "what version are you?"
                                           :max-tool-rounds 2}
                                          (default-tools/tools))]
    (testing "returns final response"
      (is (= "Version is 0.1.0" (:response response))))
    (testing "sends follow-up messages including tool result"
      (is (= 2 (count @calls)))
      (is (= "tool"
             (get-in (second @calls) [:messages 2 :role])))
      (is (= "0.1.0"
             (get-in (second @calls) [:messages 2 :content]))))))
