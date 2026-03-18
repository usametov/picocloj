```clojure
;; Minimum Viable PicoClaw in Clojure
;; ==================================
;; A tiny, single-file CLI AI assistant inspired by https://github.com/sipeed/picoclaw
;; (the ultra-light Go agent).
;;
;; Features (MVP):
;; - `onboard` → creates ~/.picoclaw/config.edn with your API key
;; - `agent` → interactive chat loop (ReAct-style memory via conversation history)
;; - Uses any OpenAI-compatible endpoint (OpenAI, Groq, DeepSeek, VolcEngine, etc.)
;; - < 200 LOC, runs with plain `clj`
;; - No tool calling in this MVP (add later via LLM JSON mode + function defs)
;;
;; How to run :
;; 3. clj -M -m astanova.picocloj onboard
;; 4. Edit ~/.picocloj/config.edn (put your real api-key + model)
;; 5. clj -M -m astanova.picocloj agent
 
## Minimum-viable PicoClaw version in Clojure.**

- Same workflow as the original (`onboard` → config → `agent`)
- Same ultra-light philosophy (tiny codebase, single binary possible with GraalVM)
- Ready to extend with tools, memory persistence, MCP, voice, etc. (just add more keys to the config and a tool-calling parser in the loop)

