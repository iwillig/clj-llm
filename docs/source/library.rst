Library usage
=============

A core project goal is to mirror the useful shape of Simon Willison's Python
``llm`` project in Clojure: a compact library API plus a command line tool.

The high-level public API lives in the ``llm`` namespace.

Basic prompt usage
------------------

Require the namespace:

.. code-block:: clojure

   (require '[llm :as llm])

Prompt with default configuration:

.. code-block:: clojure

   (llm/prompt-text "What is 2+2?")

Prompt with an explicit model id or alias:

.. code-block:: clojure

   (llm/prompt-text {:model "gpt-4o-mini"}
                    "Summarize this paragraph")

   (llm/prompt-text {:model "4o-mini"}
                    "Summarize this paragraph")

Resolve a model from query terms:

.. code-block:: clojure

   (llm/prompt-text {:query-terms ["4o" "mini"]}
                    "Summarize this paragraph")

Work with response maps
-----------------------

Use ``llm/prompt`` when you want the full normalized response object instead of
just response text:

.. code-block:: clojure

   (llm/prompt {:model "gpt-4o-mini"}
               "Explain tail recursion"
               {:system "Be concise"})

Structured output with schemas
------------------------------

You can request structured output by passing a JSON schema map in the prompt
options.

.. code-block:: clojure

   (llm/prompt {:model "gpt-4o-mini"}
               "Invent a dog"
               {:schema {:type "object"
                         :properties {:name {:type "string"}}
                         :required ["name"]}})

This returns the normal response object, but the request is routed through the
chat provider with a schema-constrained response format.

For convenience, ``llm/prompt-text`` also works with schema options:

.. code-block:: clojure

   (llm/prompt-text {:model "gpt-4o-mini"}
                    "Invent a dog"
                    {:schema {:type "object"
                              :properties {:name {:type "string"}}
                              :required ["name"]}})

Current scope of schema support:

* schemas are provided as normal Clojure maps in the library API
* the map should represent JSON-schema-compatible data
* schema requests use the chat provider path
* concise schema DSL and ``--schema-multi`` are currently CLI features

Tools
-----

Create a tool from plain data:

.. code-block:: clojure

   (def upper-tool
     (llm/tool
      {:name "upper"
       :description "Convert text to uppercase."
       :parameters {:type "object"
                    :properties {:text {:type "string"}}
                    :required ["text"]}
       :invoke (fn [{:keys [text]}]
                 {:text (.toUpperCase text)})}))

Use a tool in a prompt:

.. code-block:: clojure

   (llm/prompt {:model "gpt-4o-mini"}
               "Convert panda to uppercase"
               {:tools [upper-tool]})

Conversation API
----------------

Create an immutable conversation value:

.. code-block:: clojure

   (def convo
     (llm/conversation {:model "gpt-4o-mini"}
                       {:system "Be concise"}))

Send a follow-up message:

.. code-block:: clojure

   (def result
     (llm/converse convo "Hello"))

The return value includes:

* ``:conversation`` with updated messages
* ``:response`` with the normalized provider response

Listing models
--------------

The high-level API includes a raw model listing function:

.. code-block:: clojure

   (llm/list-models)

For normalized model descriptors and model search support, use the
``llm.model-catalog`` namespace directly.

Notes
-----

``llm/prompt`` uses multiple execution paths:

* plain prompts use the completion provider
* prompts with tools use the chat provider and tool loop
* prompts with schemas use the chat provider

This split keeps the simple path lightweight while still supporting
model-callable tools and structured output when needed.
