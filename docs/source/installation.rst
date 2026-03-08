Installation
============

Prerequisites
-------------

You need a working Clojure environment and access to an OpenAI-compatible
endpoint. The project defaults to a local endpoint, which works well with
local model runners that expose an OpenAI-compatible API.

Running from source
-------------------

From the repository root, common development tasks include:

.. code-block:: bash

   bb nrepl
   bb test
   bb jar

Equivalent ``just`` commands are also available:

.. code-block:: bash

   just nrepl
   just test
   just jar

CLI entry point
---------------

The CLI entry point is ``llm.main``.

To run the CLI from source, use a normal Clojure invocation pattern for your
local setup. For example:

.. code-block:: bash

   clojure -M -m llm.main prompt "What is 2+2?"

Configuration
-------------

The project uses three main configuration values:

* base URL
* API key
* model id

These are resolved through the library configuration layer before requests are
sent to the provider.

Current defaults in the project are intended for local development. See the
source in ``src/llm/config.clj`` and related namespaces for the exact runtime
behavior.
