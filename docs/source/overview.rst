Overview
========

``clj-llm`` is a low-level Clojure library and CLI based on Simon Willison's
Python ``llm`` project.

Source project and documentation:

* https://llm.datasette.io/en/stable/
* https://github.com/simonw/llm

This project exists to bring that same general shape to Clojure: a small,
scriptable library together with a command line tool for working with language
models.

We want to clearly credit the original project for the structure and overall
inspiration behind this work.

The project is designed for developers who want a small, scriptable interface
to OpenAI-compatible model APIs from Clojure and from the shell.

Current capabilities
--------------------

The current codebase supports:

* listing models from an OpenAI-compatible endpoint
* sending plain completion-style prompts
* using a chat-based tool loop for model tool calling
* resolving model ids from aliases or query terms
* streaming plain prompt responses in the CLI

Project layout
--------------

Important namespaces and files include:

* ``src/llm/main.clj``: CLI entry point
* ``src/llm/cli.clj``: command-line parsing and command execution
* ``src/llm.clj``: high-level public API
* ``src/llm/openai_compat.clj``: completion-style provider
* ``src/llm/openai_chat.clj``: chat provider for tool-enabled flows
* ``src/llm/tool_loop.clj``: tool execution loop
* ``src/llm/model_catalog.clj``: normalized model catalog and resolution

Concepts
--------

Provider configuration
~~~~~~~~~~~~~~~~~~~~~~

The library works with an OpenAI-compatible base URL, API key, and model id.
By default, the project is configured for a local endpoint at:

.. code-block:: text

   http://127.0.0.1:11434/v1

Model resolution
~~~~~~~~~~~~~~~~

The project can resolve a model from:

* an exact model id
* an alias such as ``4o-mini``
* a list of search terms

Tools
~~~~~

The library includes abstractions for model-callable tools. A tool provides:

* a name
* a description
* a parameter schema
* an implementation function

When tools are enabled, prompts are routed through the chat provider and the
tool loop continues until the model stops requesting tool calls or the maximum
number of rounds is reached.
