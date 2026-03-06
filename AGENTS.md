# AGENTS.md

Guidance for coding agents working in this repository.

## Project overview

This is a small Clojure library and CLI for interacting with LLM agents.
Primary code lives under `src/llm`, development helpers under `dev`, and
tests under `test`.

Relevant entry points:
- `src/llm/main.clj` - CLI entry point
- `dev/user.clj` - REPL convenience namespace
- `dev/dev.clj` - reload workflow
- `deps.edn` - aliases and nREPL configuration
- `bb.edn` and `justfile` - common tasks

## Required workflow

### 1. Review before editing

Before changing code:
- Read the target file and nearby code.
- Search for related functions, tests, and call sites.
- Follow existing naming and namespace conventions.
- Prefer editing existing files over creating new ones.

Useful commands:
```bash
rg -n "function-name|namespace-name" src test dev
find src test dev -type f | sort
```

### 2. REPL-first development

REPL-first development is required for Clojure changes.

Check that nREPL is available:
```bash
cat .nrepl-port
clj-nrepl-eval -p 7889 '(+ 1 1)'
```

If nREPL is not running, start it with one of:
```bash
bb nrepl
just nrepl
clojure -M:jvm-base:dev:nrepl
```

This project is configured to run nREPL on port `7889` in `deps.edn:16`.

### 3. Initialize the dev REPL

The REPL helper flow is:
- `dev/user.clj:3` defines `user/dev`
- `user/dev` requires `dev` and switches into the `dev` namespace
- `dev/user.clj:8` aliases `fast-dev` to `user/dev`
- `dev/dev.clj:4` initializes `clj-reload` for `src`, `dev`, and `test`
- `dev/dev.clj:7` defines `dev/reload`

Agents should check the current REPL namespace before choosing the helper
function.

Check the current namespace with:
```bash
clj-nrepl-eval -p 7889 '*ns*'
```

Rules:
- If the current namespace is `user`, call `(fast-dev)`.
- If the current namespace is `dev`, call `(reload)`.
- Do not call `(reload)` from `user` before switching into `dev`.

Recommended flow:
```clojure
*ns*
;; if user
(fast-dev)
;; now in dev
(reload)
```

Example validation:
```bash
clj-nrepl-eval -p 7889 '*ns*'
clj-nrepl-eval -p 7889 "(do (require 'user :reload) (fast-dev) *ns*)"
clj-nrepl-eval -p 7889 "(do (require 'dev :reload) (reload))"
```

### 4. Validate in REPL before saving significant Clojure changes

Before committing Clojure code:
- Load the namespace in nREPL.
- Exercise the changed functions directly.
- Check nil, empty, and invalid-input behavior where relevant.
- Run the relevant tests.

Examples:
```bash
clj-nrepl-eval -p 7889 "(require '[llm.main :as main] :reload)"
clj-nrepl-eval -p 7889 "(require '[clojure.repl :as repl]) (repl/doc some-fn)"
clj-nrepl-eval -p 7889 "(clojure.test/run-tests 'llm.openai-compat-test)"
```

## Development commands

Primary task runners:

```bash
bb nrepl
bb test
bb jar
bb native
bb native-smoke
```

Equivalent `just` commands:

```bash
just nrepl
just test
just jar
just native
just native-smoke
```

Direct Clojure aliases from `deps.edn`:

```bash
clojure -M:test
clojure -M:jvm-base:dev:nrepl
clojure -T:build uber
clojure -T:build native
```

## Code style

Follow idiomatic Clojure and existing project patterns:
- Use kebab-case for vars and functions.
- Use predicates ending in `?`.
- Prefer plain data and transformations over unnecessary state.
- Prefer threading macros for multi-step transformations.
- Use `->` for map/object transformations.
- Use `->>` for sequence pipelines.
- Keep functions small and composable.
- Use docstrings for public functions.
- Use `ex-info` with structured data for reportable errors.
- Keep namespace declarations clean and conventional.

Namespace conventions in this repo are simple and minimal. Match the style already
present in the surrounding file.

## Testing guidance

Run targeted tests when possible, then broader test commands as needed.

Current test namespaces include:
- `test/llm/openai_compat_test.clj`
- `test/llm/jdk_http_test.clj`
- `test/llm/main_test.clj`

Project test alias currently runs:
```clojure
llm.openai-compat-test
llm.jdk-http-test
```

So if you add or modify tests outside those namespaces, also run them explicitly.

Examples:
```bash
clojure -M:test
clj-nrepl-eval -p 7889 "(require 'llm.main-test :reload) (clojure.test/run-tests 'llm.main-test)"
```

## File editing rules

- Prefer modifying existing files.
- Do not create extra documentation files unless requested.
- Keep changes focused on the user request.
- If you edit Clojure forms and encounter delimiter issues, use:
  ```bash
  clj-paren-repair path/to/file.clj
  ```

## Notes on current dev namespaces

### `dev/user.clj`
- `user/dev` requires `dev` and switches the REPL namespace to `dev`.
- `fast-dev` is a var alias to `user/dev`, so `(fast-dev)` is the intended quick
  entry point.

### `dev/dev.clj`
- Uses `clj-reload.core` as `reload`.
- Watches `src`, `dev`, and `test`.
- `dev/reload` recompiles and reloads changed namespaces.

## Suggested agent checklist

Before editing:
- Read the target namespace and related tests.
- Confirm nREPL is running.
- Load the relevant namespace in REPL.

Before finishing:
- Reload changed namespaces.
- Validate changed behavior in REPL.
- Run relevant tests.
- Summarize changed files and any follow-up work.
