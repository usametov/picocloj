# Introduction to PicoClaw Clojure Edition

PicoClaw Clojure is a minimal, single-file CLI AI assistant inspired by the ultra-light Go agent [picoclaw](https://github.com/sipeed/picoclaw).

It provides a simple interactive chat interface using OpenAI-compatible APIs, with a focus on minimal code and dependencies.

## Key Design Principles

- **Minimalism**: Less than 200 lines of code
- **Simplicity**: Single source file, plain Clojure, no complex frameworks
- **Portability**: Runs anywhere Clojure runs, with optional GraalVM native compilation
- **Extensibility**: Easy to add tool calling, memory persistence, and MCP support

## Architecture

The core components are:

1. **Configuration management**: Stores API keys and model settings in `~/.picoclaw/config.edn`
2. **HTTP client**: Communicates with OpenAI-compatible endpoints via `clj-http`
3. **Interactive loop**: Simple read-eval-print loop with conversation history
4. **Command-line interface**: Two subcommands: `onboard` and `agent`

## Available Commands

  - `/skills` lists available skills
  - `/example` activates the example skill
  - `/clear` deactivates the current skill
  - Skills load automatically from the configured directory
  - The `invoke_skill` tool can activate skills programmatically

For detailed information about creating and using skills, see [Skills Documentation](skills.md).

## Future Directions

- Tool calling via LLM JSON mode
- Conversation persistence
- MCP (Model Context Protocol) server integration
- Voice interface
- Native binary with GraalVM
