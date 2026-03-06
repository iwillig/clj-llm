(ns dev
  (:require [clj-reload.core :as reload]))

(reload/init
  {:dirs ["src" "dev" "test"]})

(defn reload
  "Reloads and compiles the Clojure namespaces."
  []
  (reload/reload))
