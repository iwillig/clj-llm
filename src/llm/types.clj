(ns llm.types
  "Shared request, response, and event records for llm providers.")

(defrecord CompletionRequest
           [prompt model system stream? raw? options
            messages tools tool-choice max-tool-rounds schema])

(defrecord ToolCall
           [id name arguments raw])

(defrecord ToolResult
           [tool-call-id name content raw error?])

(defrecord ChatMessage
           [role content tool-call-id tool-calls raw])

(defrecord CompletionResponse
           [provider model response done done-reason created-at total-duration raw
            tool-calls messages])

(defrecord StreamEvent
           [type text response raw])
