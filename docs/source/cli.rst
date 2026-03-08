CLI usage
=========

Like the Python ``llm`` project that inspired this work,
``clj-llm`` includes a first-class command line interface alongside the
library API.

The ``clj-llm`` CLI provides two primary subcommands:

* ``models``
* ``prompt``

If the first argument is not a subcommand, the CLI treats it as shorthand for
``prompt``.

List models
-----------

List models from the configured endpoint:

.. code-block:: bash

   clj-llm models

Filter models by query terms:

.. code-block:: bash

   clj-llm models -q 4o -q mini

Show detailed option metadata:

.. code-block:: bash

   clj-llm models --options

Prompt a model
--------------

Send a prompt:

.. code-block:: bash

   clj-llm prompt "What is 2+2?"

Use shorthand prompt mode:

.. code-block:: bash

   clj-llm "What is 2+2?"

Specify a system prompt:

.. code-block:: bash

   clj-llm prompt -s "You are concise." "Explain monads simply."

Select a model explicitly:

.. code-block:: bash

   clj-llm prompt -m qwen2.5:0.5b "Hello"

Resolve a model from query terms:

.. code-block:: bash

   clj-llm prompt -q 4o -q mini "Summarize this text"

Disable streaming:

.. code-block:: bash

   clj-llm prompt --no-stream "What is 2+2?"

Print full JSON output:

.. code-block:: bash

   clj-llm prompt --json "What is 2+2?"

Read prompt text from standard input:

.. code-block:: bash

   echo "What is 2+2?" | clj-llm prompt

Pass model options:

.. code-block:: bash

   clj-llm prompt -o temperature 0.5 -o top_p 0.9 "Hello"

Enable tools
------------

Enable a tool by name:

.. code-block:: bash

   clj-llm prompt -T llm_time "What time is it?"

Set the maximum tool loop rounds:

.. code-block:: bash

   clj-llm prompt -T llm_time --tool-max-rounds 4 "What time is it?"

Schemas
-------

``clj-llm`` supports schema-constrained output for models that advertise schema
support.

Pass an inline JSON schema using ``--schema``:

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema '{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}' \
     "Invent a dog"

You can also pass the path to a JSON schema file:

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema dog.schema.json \
     "Invent a dog"

A concise schema DSL is also supported. This example defines three fields,
with ``age`` typed as an integer:

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema 'name, age int, one_sentence_bio' \
     "Invent a cool dog"

Descriptions can be provided using ``:`` and fields can be separated by commas
or newlines:

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema 'name: the person name
     organization: who they represent' \
     "Extract people from this text"

Use ``--schema-multi`` to request multiple items wrapped in an ``items`` array:

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema-multi 'name, age int, one_sentence_bio' \
     "Invent 3 cool dogs"

Current implemented schema support includes:

* inline JSON schema
* JSON schema file paths
* concise schema DSL
* ``--schema-multi`` wrapping into an object with an ``items`` array

Current concise DSL type support includes:

* ``string`` or ``str``
* ``int`` or ``integer``
* ``float`` or ``number``
* ``bool`` or ``boolean``

For more detail, see :doc:`schemas`.

Important options
-----------------

Shared options:

* ``-m``, ``--model``: model name
* ``-h``, ``--host``: base URL
* ``--json``: print full JSON response

Prompt options:

* ``-s``, ``--system``: system prompt
* ``--stream`` and ``--no-stream``: enable or disable streaming
* ``--raw``: disable provider prompt templating when supported
* ``-o``, ``--option``: repeated model option pairs
* ``-q``, ``--query``: repeated model resolution terms
* ``-T``, ``--tool``: enable named tools
* ``--tool-max-rounds``: maximum tool execution rounds
* ``--schema``: inline JSON schema, concise schema DSL, or path to a JSON schema file
* ``--schema-multi``: request multiple items using the same schema

Notes
-----

The plain prompt command supports streaming output. Tool-enabled execution uses
a chat provider and currently returns the final response after tool processing.

Schema-constrained requests also use the chat provider path.
