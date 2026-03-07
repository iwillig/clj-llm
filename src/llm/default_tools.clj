(ns llm.default-tools
  "Built-in tool implementations."
  (:require [llm.tools :as tools])
  (:import (java.time Instant ZonedDateTime ZoneOffset)
           (java.time.format DateTimeFormatter)))

(def ^:private version "0.1.0")

(defrecord LlmVersionTool [])

(defrecord LlmTimeTool [])

(extend-type LlmVersionTool
  tools/Tool
  (tool-name [_]
    "llm_version")
  (tool-description [_]
    "Return the current version of clj-llm.")
  (tool-parameters [_]
    {:type "object"
     :properties {}
     :required []})
  (invoke [_ _args]
    version)
  (tool->spec [this]
    {:type "function"
     :function {:name (tools/tool-name this)
                :description (tools/tool-description this)
                :parameters (tools/tool-parameters this)}}))

(extend-type LlmTimeTool
  tools/Tool
  (tool-name [_]
    "llm_time")
  (tool-description [_]
    "Return the current local and UTC time.")
  (tool-parameters [_]
    {:type "object"
     :properties {}
     :required []})
  (invoke [_ _args]
    {:local (.format DateTimeFormatter/ISO_OFFSET_DATE_TIME
                     (ZonedDateTime/now))
     :utc (.format DateTimeFormatter/ISO_INSTANT
                   (Instant/now))})
  (tool->spec [this]
    {:type "function"
     :function {:name (tools/tool-name this)
                :description (tools/tool-description this)
                :parameters (tools/tool-parameters this)}}))

(defn tools
  "Return the built-in tools supported by clj-llm."
  []
  [(->LlmVersionTool)
   (->LlmTimeTool)])
