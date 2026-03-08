Clj-llm documentation
=====================

``clj-llm`` is a small Clojure library and command line tool for interacting
with OpenAI-compatible large language model endpoints.

This project is explicitly based on Simon Willison's Python ``llm`` project:
https://llm.datasette.io/en/stable/

Our goal is to bring that same overall structure to Clojure: a small,
scriptable library together with a practical command line tool.

It provides two main entry points:

* a CLI for listing models and sending prompts
* a Clojure API for prompting models, working with conversations, and running
  tool-enabled chat loops

This documentation is a starting point for the project and will expand as the
API and CLI stabilize.

.. toctree::
   :maxdepth: 2
   :caption: Contents

   overview
   installation
   cli
   library
   schemas
   credits
