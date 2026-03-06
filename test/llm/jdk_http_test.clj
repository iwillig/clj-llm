(ns llm.jdk-http-test
  (:require [clojure.test :refer [deftest is]]
            [llm.transport.jdk-http :as jdk-http])
  (:import (java.io ByteArrayInputStream)))

(deftest slurp-stream-test
  (let [input (ByteArrayInputStream. (.getBytes "data: {\"choices\":[{\"text\":\"Hi\"}]}\n\ndata: [DONE]\n" "UTF-8"))]
    (is (= "data: {\"choices\":[{\"text\":\"Hi\"}]}\n\ndata: [DONE]\n"
           (#'llm.transport.jdk-http/slurp-stream input)))))
