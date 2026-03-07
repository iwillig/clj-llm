(ns llm.tool-registry
  "Tool registry helpers."
  (:require [llm.default-tools :as default-tools]
            [llm.tools :as tools]))

(defn default-tool-map
  "Return a map of built-in tools keyed by tool name."
  []
  (into {}
        (map (fn [tool]
               [(tools/tool-name tool) tool]))
        (default-tools/tools)))

(defn resolve-tools
  "Resolve the requested tool names to tool implementations.

  Throws when any tool name is unknown.
  "
  [tool-names]
  (let [tool-map (default-tool-map)]
    (mapv (fn [tool-name]
            (or (get tool-map tool-name)
                (throw (ex-info "Unknown tool"
                                {:tool-name tool-name
                                 :available-tools (sort (keys tool-map))}))))
          tool-names)))
