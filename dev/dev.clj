(ns dev
  (:require [clj-reload.core :as reload]
            [clj-kondo.core :as clj-kondo]))

(reload/init
  {:dirs ["src" "dev" "test"]})

(defn reload
  "Reloads and compiles the Clojure namespaces."
  []
  (reload/reload))

(defn lint
  "Lint the entire project (src and test directories)."
  []
  (-> (clj-kondo/run! {:lint ["src" "test" "dev"]})
      (clj-kondo/print!)))