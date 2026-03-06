(ns llm.main
  (:gen-class)
  (:require [llm.cli :as cli]))

(defn -main [& args]
  (cli/run-cli args))
