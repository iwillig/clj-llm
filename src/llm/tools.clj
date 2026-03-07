(ns llm.tools
  "Protocol and helpers for model-callable tools.")

(defprotocol Tool
  "Protocol for LLM-callable tools."
  (tool-name [this]
    "Return the tool name exposed to the model.")
  (tool-description [this]
    "Return the model-facing tool description.")
  (tool-parameters [this]
    "Return a JSON-schema-like parameters map.")
  (invoke [this args]
    "Execute the tool with parsed argument map and return a result.")
  (tool->spec [this]
    "Return the provider-facing tool spec map."))

(defn result->content
  "Convert a tool result into text content for a tool response message.

  Strings are returned as-is. Other values are converted using `pr-str`.
  "
  [result]
  (if (string? result)
    result
    (pr-str result)))
