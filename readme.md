# LLM

A low level library and command line tool for interacting with LLM agents.

## Usage

### List available models

```bash
llm models
```

### Execute a prompt

```bash
llm prompt "What is 2+2?"
```

### With system prompt

```bash
llm prompt -s "You are a pirate. Respond in pirate speak." "What is 2+2?"
```

### Specify a different model

```bash
llm prompt -m qwen2.5:0.5b "Hello"
```

### Get full JSON response

```bash
llm prompt --json "What is 2+2?"
```

### Disable streaming (get response all at once)

```bash
llm prompt --no-stream "What is 2+2?"
```

### Pass model options

```bash
llm prompt -o temperature 0.5 -o top_p 0.9 "Hello"
```

## Options

- `-m, --model MODEL` - Model name (default: llama3.2:latest)
- `-h, --host URL` - Base URL (default: http://127.0.0.1:11434/v1)
- `--[no-]json` - Print full JSON response
- `-s, --system TEXT` - System prompt
- `--[no-]stream` - Stream response (default: true)
- `--[no-]raw` - Disable provider prompt templating
- `-o, --option KEY VALUE` - Model option (can repeat)