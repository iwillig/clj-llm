# CLJ LLM

A low level library and command line tool for interacting with LLM agents.

The CLI binary is named `clj-llm` to avoid conflicting with the Python
`llm` project.

## Usage

### List available models

```bash
clj-llm models
```

### Execute a prompt

```bash
clj-llm prompt "What is 2+2?"
```

### Prompt shorthand

If the first argument is not a subcommand, `clj-llm` treats it as a
`prompt` command.

```bash
clj-llm "What is 2+2?"
```

### With system prompt

```bash
clj-llm prompt -s "You are a pirate. Respond in pirate speak." "What is 2+2?"
```

### Specify a different model

```bash
clj-llm prompt -m qwen2.5:0.5b "Hello"
```

### Get full JSON response

```bash
clj-llm prompt --json "What is 2+2?"
```

### Disable streaming

```bash
clj-llm prompt --no-stream "What is 2+2?"
```

### Read prompt text from stdin

```bash
echo "What is 2+2?" | clj-llm prompt
```

When stdin and a positional prompt are both present, stdin is prepended to
the prompt with a blank line between them.

### Pass model options

```bash
clj-llm prompt -o temperature 0.5 -o top_p 0.9 "Hello"
```

## Options

Shared options:

- `-m, --model MODEL` - Model name (default: `llama3.2:latest`)
- `-h, --host URL` - Base URL (default: `http://127.0.0.1:11434/v1`)
- `--[no-]json` - Print full JSON response

Prompt options:

- `-s, --system TEXT` - System prompt
- `--[no-]stream` - Stream response chunks as they arrive (default: `true`)
- `--[no-]raw` - Disable provider prompt templating when supported
- `-o, --option KEY VALUE` - Model option name/value pair (can repeat)
