Schemas
=======

``clj-llm`` supports schema-constrained output for models that advertise
schema support.

This feature is inspired by the schema support in Simon Willison's Python
``llm`` project, but the implementation here is currently a smaller MVP.

Supported schema input forms
----------------------------

The CLI currently supports three schema input styles.

Inline JSON schema
~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema '{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}' \
     "Invent a dog"

JSON schema file path
~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema dog.schema.json \
     "Invent a dog"

Concise schema DSL
~~~~~~~~~~~~~~~~~~

The CLI also supports a concise schema syntax.

Simple comma-separated fields:

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema 'name, age int, one_sentence_bio' \
     "Invent a cool dog"

Fields may also be separated by newlines and can include descriptions using
``:``.

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema 'name: the person name
     organization: who they represent
     role: their role' \
     "Extract people from this text"

Supported concise types
-----------------------

The current concise schema implementation supports these primitive type names:

* ``string`` or ``str``
* ``int`` or ``integer``
* ``float`` or ``number``
* ``bool`` or ``boolean``

If no type is provided, the field defaults to ``string``.

Schema multi
------------

Use ``--schema-multi`` when you want multiple results using the same item
schema.

.. code-block:: bash

   clj-llm prompt \
     --model gpt-4o-mini \
     --schema-multi 'name, age int, one_sentence_bio' \
     "Invent 3 cool dogs"

The current implementation wraps the item schema in an object with an
``items`` array.

Conceptually, this shape looks like:

.. code-block:: json

   {
     "type": "object",
     "properties": {
       "items": {
         "type": "array",
         "items": {
           "type": "object",
           "properties": {
             "name": {"type": "string"},
             "age": {"type": "integer"},
             "one_sentence_bio": {"type": "string"}
           },
           "required": ["name", "age", "one_sentence_bio"]
         }
       }
     },
     "required": ["items"]
   }

Library usage
-------------

In the Clojure library API, schemas are currently passed as ordinary Clojure
maps.

.. code-block:: clojure

   (require '[llm :as llm])

   (llm/prompt {:model "gpt-4o-mini"}
               "Invent a dog"
               {:schema {:type "object"
                         :properties {:name {:type "string"}}
                         :required ["name"]}})

Current limitations
-------------------

The current implementation does not yet include the full upstream schema
feature set.

Not implemented yet:

* the fuller upstream schema DSL
* nested schema structures in concise DSL
* schema IDs or saved schema registry features
* template integration for schemas
* model filtering such as ``models --schemas``

Execution path
--------------

Schema requests are currently routed through the chat provider path.
