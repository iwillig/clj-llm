Credits
=======

Upstream inspiration
--------------------

``clj-llm`` is based on Simon Willison's Python ``llm`` project.

We want to clearly and explicitly credit that project for the overall shape of
this work: a small, scriptable library paired with a practical command line
interface for working with language models.

Upstream project links:

* Documentation: https://llm.datasette.io/en/stable/
* Source code: https://github.com/simonw/llm

Project goal
------------

The goal of ``clj-llm`` is to bring that same general structure to Clojure:

* a reusable library API
* a command line tool for day-to-day use
* a simple developer-facing interface for interacting with LLM systems

What is original here
---------------------

While the project direction and product shape are inspired by the Python
``llm`` project, the implementation in this repository is Clojure-specific and
is built around this codebase's own namespaces, data structures, provider
integrations, and development workflow.

Thanks
------

Thanks to Simon Willison and the contributors to the Python ``llm`` project
for creating a clear and useful model for this kind of tool.
