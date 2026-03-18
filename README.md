# astanova/picocloj

Minimum Viable PicoClaw version in Clojure

A tiny, single-file CLI AI assistant inspired by https://github.com/sipeed/picoclaw (the ultra-light Go agent).

## Features (MVP)

- `onboard` → creates ~/.picoclaw/config.edn with your API key
- `agent` → interactive chat loop (ReAct-style memory via conversation history)
- Uses any OpenAI-compatible endpoint (OpenAI, Groq, DeepSeek, VolcEngine, etc.)
- < 200 LOC, runs with plain `clj`
- No tool calling in this MVP (add later via LLM JSON mode + function defs)

## Installation

Clone the repository and ensure you have Clojure installed.

## Usage

### One-time setup

```bash
clojure -M:run-m onboard
```

This creates `~/.picoclaw/config.edn`. Edit it with your real API key and model configuration.

### Interactive chat

```bash
clojure -M:run-m agent
```

Type your messages, and PicoClaw will respond. Type `quit` or `exit` to end the session.

### Direct greeting (exec function)

```bash
clojure -X:run-x
# Hello, World! This is PicoClaw Clojure edition.

clojure -X:run-x :name '"Alice"'
# Hello, Alice! This is PicoClaw Clojure edition.
```

### Configuration

Edit `~/.picoclaw/config.edn` to set your API key, model, and endpoint.

Example config:

```clojure
{:agents {:defaults {:model-name "gpt-4o-mini"
                     :temperature 0.7
                     :max-tokens 4096}}
 :model-list [{:name "default"
               :base-url "https://api.openai.com/v1"
               :api-key "sk-your-real-key-here"
               :model "gpt-4o-mini"}]}
```

## Building

To build an uberjar:

```bash
clojure -T:build ci
```

This creates `target/net.clojars.astanova/picocloj-0.1.0-SNAPSHOT.jar`.

Run the jar:

```bash
java -jar target/net.clojars.astanova/picocloj-0.1.0-SNAPSHOT.jar onboard
```

## Development

Run tests:

```bash
clojure -T:build test
```

## License

Copyright © 2026 Asel

Distributed under the [Eclipse Public License 1.0](http://www.eclipse.org/legal/epl-v10.html)
