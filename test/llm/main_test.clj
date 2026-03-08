(ns llm.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm.cli :as cli]
            [llm.main :as main]
            [mockfn.macros :refer [verifying]]
            [mockfn.matchers :refer [exactly]]))

(deftest main-dispatches-to-cli-test
  (testing "delegates args to cli/run-cli"
    (let [args ["prompt" "hi"]]
      (is (= 0
             (verifying [(cli/run-cli args) 0 (exactly 1)]
               (apply main/-main args)))))))
