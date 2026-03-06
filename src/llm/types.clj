(ns llm.types
  "Shared request, response, and event records for llm providers.")

(defrecord CompletionRequest
    [prompt model system stream? raw? options])

(defrecord CompletionResponse
    [provider model response done done-reason created-at total-duration raw])

(defrecord StreamEvent
    [type text response raw])
