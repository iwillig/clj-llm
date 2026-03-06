(ns llm.openai-compat-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [llm.cli :as cli]
            [llm.config :as config]
            [llm.openai-compat :as openai-compat]
            [llm.protocols :as protocols]
            [llm.types :as types]))

(defrecord StubTransport [response stream-events requests])

(extend-type StubTransport
  protocols/Transport
  (get-json [this request]
    (when-let [requests (:requests this)]
      (swap! requests conj [:get request]))
    (or (:response this)
        {:object "list"
         :data [{:id "llama3.2:latest"}
                {:id "qwen2.5:0.5b"}] }))
  (post-json [this request]
    (when-let [requests (:requests this)]
      (swap! requests conj [:post request]))
    (or (:response this)
        {:id "cmpl-1"
         :object "text_completion"
         :created 1772808777
         :model "llama3.2:latest"
         :choices [{:text "Stub response"
                    :index 0
                    :finish_reason "stop"}]}))
  (post-json-stream [this _ on-event]
    (doseq [event (:stream-events this)]
      (on-event event))))

(deftest config-test
  (testing "resolves provider config defaults"
    (is (= {:base-url config/default-base-url
            :api-key config/default-api-key
            :model config/default-model}
           (config/resolve-provider-config {})))))

(deftest stdin-available-test
  (testing "returns false when no stdin bytes are available"
    (with-redefs [cli/stdin-available? (constantly false)]
      (is (false? (cli/stdin-available?)))))
  (testing "read-stdin skips reading when no stdin bytes are available"
    (with-redefs [cli/stdin-available? (constantly false)]
      (is (nil? (cli/read-stdin))))))

(deftest resolve-prompt-test
  (is (= "stdin text\n\nprompt text"
         (cli/resolve-prompt {:stdin "stdin text"
                              :prompt "prompt text"})))
  (is (= "stdin text"
         (cli/resolve-prompt {:stdin "stdin text"})))
  (is (= "prompt text"
         (cli/resolve-prompt {:prompt "prompt text"})))
  (is (nil? (cli/resolve-prompt {}))))

(deftest completion-request-test
  (is (= {:prompt "stdin text\n\nprompt text"
          :model "llama3.2:latest"
          :system "be concise"
          :stream? true
          :raw? true
          :options {:temperature "0.2"}}
         (into {}
               (cli/->completion-request
                {:stdin "stdin text"
                 :prompt "prompt text"
                 :model "llama3.2:latest"
                 :system "be concise"
                 :stream true
                 :raw true
                 :option ["temperature" "0.2"]}))))
  (is (= llm.types.CompletionRequest
         (class (cli/->completion-request {:prompt "hello"})))))

(deftest coerce-options-test
  (let [options (openai-compat/coerce-options {:temperature "0.2"
                                               :max_tokens "64"})]
    (is (= java.lang.Float (class (:temperature options))))
    (is (= java.lang.Long (class (:max_tokens options))))))

(deftest list-models-test
  (let [requests (atom [])
        result (openai-compat/list-models
                {:transport (->StubTransport nil nil requests)})]
    (is (= "list" (:object result)))
    (is (= ["llama3.2:latest" "qwen2.5:0.5b"]
           (map :id (:data result))))
    (is (= [[:get {:url (str config/default-base-url "/models")
                   :headers {"authorization"
                             (str "Bearer " config/default-api-key)}}]]
           @requests))))

(deftest request-serialization-test
  (let [body (:body (openai-compat/request->http-request
                     (openai-compat/make-provider)
                     {:prompt "Hello"
                      :stream? false
                      :raw? true
                      :options {:temperature "0.2"
                                :max_tokens "32"}}))
        json-body (json/write-value-as-string body)]
    (is (.contains json-body "\"raw\":true"))
    (is (.contains json-body "\"temperature\":0.2"))
    (is (.contains json-body "\"max_tokens\":32"))
    (is (not (.contains json-body "\"options\":")))))

(deftest normalize-response-test
  (is (= "Hello"
         (:response
          (openai-compat/normalize-response
           {:model "llama3.2:latest"
            :created 1772808777
            :choices [{:text "Hello"
                       :index 0
                       :finish_reason "stop"}]}))))
  (is (= llm.types.CompletionResponse
         (class (openai-compat/normalize-response
                 {:model "llama3.2:latest"
                  :choices [{:text "Hello"}]})))))

(deftest normalize-stream-event-test
  (is (= {:type :text-delta
          :text "Hi"
          :response nil
          :raw {:choices [{:text "Hi"}]}}
         (into {}
               (openai-compat/normalize-stream-event
                {:choices [{:text "Hi"}]}))))
  (is (= llm.types.StreamEvent
         (class (openai-compat/normalize-stream-event
                 {:choices [{:text "Hi"}]}))))
  (is (= :done
         (:type (openai-compat/normalize-stream-event
                 {:model "llama3.2:latest"
                  :choices [{:text ""
                             :finish_reason "stop"}]})))))

(deftest provider-uses-transport-test
  (is (= "Stub response"
         (:response
          (protocols/complete
           (openai-compat/make-provider {:transport (->StubTransport nil nil nil)})
           {:prompt "Hello"})))))

(deftest run-prompt-command-skips-stdin-when-unavailable-test
  (let [requests (atom [])
        provider (openai-compat/make-provider
                  {:transport (->StubTransport nil nil requests)})]
    (with-redefs [cli/stdin-available? (constantly false)
                  openai-compat/make-provider (fn [_] provider)]
      (is (= "Stub response\n"
             (with-out-str
               (cli/run-prompt-command {:prompt "Say hi briefly."
                                        :stream false
                                        :host config/default-base-url
                                        :model config/default-model
                                        :json false}))))
      (is (= [[:post {:url (str config/default-base-url "/completions")
                      :headers {"authorization"
                                (str "Bearer " config/default-api-key)}
                      :body {:model config/default-model
                             :prompt "Say hi briefly."
                             :stream false}}]]
             @requests)))))

(deftest provider-streams-through-transport-test
  (let [events (atom [])]
    (protocols/complete-stream
     (openai-compat/make-provider
      {:transport (->StubTransport nil [{:choices [{:text "Hi"}]}
                                        {:model "llama3.2:latest"
                                         :choices [{:text ""
                                                    :finish_reason "stop"}]}]
                                      nil)})
     {:prompt "Hello"}
     #(swap! events conj %))
    (is (= [:text-delta :done]
           (map :type @events)))
    (is (= llm.types.StreamEvent
           (class (first @events))))))

(deftest prompt-shorthand-args-test
  (is (= ["models"]
         (cli/prompt-shorthand-args ["models"])))
  (is (= ["prompt" "hello"]
         (cli/prompt-shorthand-args ["prompt" "hello"])))
  (is (= ["prompt" "Say hi briefly."]
         (cli/prompt-shorthand-args ["Say hi briefly."]))))

(deftest cli-config-test
  (is (= "clj-llm" (:command cli/cli-config)))
  (is (= 2 (count (:subcommands cli/cli-config))))
  (is (= ["models" "prompt"]
         (map :command (:subcommands cli/cli-config)))))
