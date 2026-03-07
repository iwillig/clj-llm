(ns llm.default-tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm.default-tools :as default-tools]
            [llm.tools :as tools]))

(deftest built-in-tools-test
  (let [tool-map (into {}
                       (map (fn [tool]
                              [(tools/tool-name tool) tool]))
                       (default-tools/tools))]
    (testing "version tool metadata"
      (is (= "llm_version"
             (tools/tool-name (get tool-map "llm_version"))))
      (is (= "Return the current version of clj-llm."
             (tools/tool-description (get tool-map "llm_version")))))
    (testing "version tool invocation"
      (is (= "0.1.0"
             (tools/invoke (get tool-map "llm_version") {}))))
    (testing "time tool invocation"
      (is (string? (:local (tools/invoke (get tool-map "llm_time") {}))))
      (is (string? (:utc (tools/invoke (get tool-map "llm_time") {})))))))
