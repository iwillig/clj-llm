(ns llm.config
  "Configuration helpers for the llm CLI and providers.")

(set! *warn-on-reflection* true)

(def default-base-url
  (or (System/getenv "LLM_BASE_URL")
      "http://127.0.0.1:11434/v1"))

(def default-api-key
  (or (System/getenv "LLM_API_KEY") "ollama"))

(def default-model
  (or (System/getenv "LLM_MODEL")
      (System/getenv "OLLAMA_MODEL")
      "llama3.2:latest"))

(defn resolve-provider-config
  "Resolve provider config from explicit options and environment defaults."
  [{:keys [base-url api-key model]}]
  {:base-url (or base-url default-base-url)
   :api-key (or api-key default-api-key)
   :model (or model default-model)})
