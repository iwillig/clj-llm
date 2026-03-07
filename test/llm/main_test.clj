(ns llm.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm.cli :as cli]
            [llm.main :as main]))

(deftest main-dispatches-to-cli-test
  (testing "delegates args to cli/run-cli"
    (let [args ["prompt" "hi"]
          calls (atom [])]
      (with-redefs [cli/run-cli (fn [passed-args]
                                  (swap! calls conj passed-args)
                                  0)]
        (is (= 0 (apply main/-main args)))
        (is (= [args] @calls))))))
