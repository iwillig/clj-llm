(ns llm.protocols)

(defprotocol Transport
  "Protocol for low-level HTTP transport implementations."
  (get-json [this request]
    "GET a JSON request and return a decoded response map.")
  (post-json [this request]
    "POST a JSON request and return a decoded response map.")
  (post-json-stream [this request on-event]
    "POST a JSON request and invoke `on-event` for each decoded stream event."))

(defprotocol CompletionProvider
  "Protocol for providers that can execute text completions."
  (complete [this request]
    "Execute a completion request and return a normalized response map.")
  (complete-text [this request]
    "Execute a completion request and return only response text.")
  (complete-stream [this request on-event]
    "Execute a streaming completion request and invoke `on-event` for each event."))
