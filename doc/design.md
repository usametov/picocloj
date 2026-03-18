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


**Skills in the OpenClaw/Claude ecosystem are standardized, portable bundles** based on the open **Agent Skills** format (see agentskills.io and the SKILL.md spec). They are **not** full code plugins but mostly:

- A **SKILL.md** file (or folder containing one + supporting files like references/).
- **YAML frontmatter** (metadata: `name`, `description`, optional `allowed-tools`, `version`, `metadata`, Claude-specific keys like `disable-model-invocation`, `context: fork`, `model`, etc.).
- **Markdown body** = the actual **prompt injection** (instructions, workflows, templates, guardrails, examples, decision trees).

The body becomes injected context for the LLM when the skill activates (auto or via `/name`). The frontmatter controls discovery, invocation, tool restrictions, and dynamic behavior (e.g., `!shell-command` for live data injection in Claude Code/OpenClaw).

**Why this works across communities**:
- OpenClaw (local agent, ClawHub registry with 13k+ skills) uses them exactly this way.
- Claude Code / Cursor / Codex / Gemini CLI / many others follow the same open standard (or extend it lightly).
- Skills teach the agent *when/how* to use tools (they rarely define tool *schemas* themselves — they reference platform tools like `bash`, `read`, `grep`, or custom ones).
- "Tool definitions" in your sense = the `allowed-tools` list + prompt guidance on usage. The actual executable tools stay in the runtime (your Clojure fns + schemas).

You can drop almost any community SKILL.md into your agent with zero or minimal changes (just map referenced tool names to your implementations).

### Plug-and-Play Architecture for Your Clojure Agent

Design for **modularity, token efficiency, and zero runtime coupling**. Skills are pure data (EDN after parsing). Your agent remains in control of tools, execution, and LLM calls (Anthropic/OpenAI/etc. tool-calling API).

#### 1. Core Data Model (skills namespace)
```clojure
(ns your-agent.skills
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defrecord Skill
  [id ; e.g. "coding-agent" or GitHub path
   name
   description
   prompt ; full markdown body (the injection)
   metadata ; parsed frontmatter map
   allowed-tools ; set of strings from frontmatter (for filtering)
   supporting ; map of filename -> content (if folder)
   ])

(defn parse-skill-md
  "Parse SKILL.md (string or File/URL). Returns Skill or nil."
  [md-content-or-path]
  (let [content (if (string? md-content-or-path)
                  md-content-or-path
                  (slurp md-content-or-path))
        ;; Simple frontmatter splitter (robust enough for most skills)
        [_ front body] (re-matches #"(?s)^---\s*(.*?)\s*---\s*(.*)" content)
        meta (when front (yaml/parse-string front))]
    (when meta
      (->Skill
       (or (:name meta) "unnamed")
       (:name meta)
       (:description meta)
       (str/trim body)
       meta
       (set (:allowed-tools meta))
       {})))) ; extend for folder support later
```

#### 2. Registry + Loader (plug-and-play loading)
```clojure
(def skills-registry (atom {})) ; id -> Skill

(defn load-skill!
  "Load from local path, raw GitHub URL, or ClawHub-style name.
   Example: (load-skill! \"https://raw.githubusercontent.com/openclaw/skills/main/skills/steipete/coding-agent/SKILL.md\")"
  [source]
  (let [skill (parse-skill-md (if (string? source) (slurp source) source))]
    (when skill
      (swap! skills-registry assoc (:id skill) skill)
      skill)))

;; Auto-load all local skills (your plug-and-play folder)
(defn load-all-local-skills! [dir]
  (doseq [f (file-seq (io/file dir))]
    (when (and (.isFile f) (str/ends-with? (.getName f) "SKILL.md"))
      (load-skill! f))))
```

**How to reuse community skills**:
- Download single SKILL.md from ClawHub/GitHub raw links and drop in `skills/` folder.
- Or `load-skill!` directly from URL (no install step needed).
- For full folders (references, examples): Extend loader to fetch zip or multiple files and store in `:supporting`.

#### 3. Prompt Builder (the magic injection)
Token-efficient hybrid of Claude/OpenClaw style:

```clojure
(defn build-system-prompt
  [base-prompt enabled-skill-ids active-skill-id] ; active = currently injected
  (let [catalog (for [id enabled-skill-ids
                      :let [s (get @skills-registry id)]]
                  (str "- " (:name s) ": " (:description s)))
        full-prompt (when active-skill-id
                      (str "\n\n=== ACTIVE SKILL: " active-skill-id " ===\n"
                           (:prompt (get @skills-registry active-skill-id))))]
    (str base-prompt
         "\n\nAvailable skills (use /name or invoke-skill tool when relevant):\n"
         (str/join "\n" catalog)
         full-prompt)))
```

**Invocation options** (choose one or combine):
- **Simple**: User types `/coding-agent ...` → parse in your message handler → set `active-skill-id` → rebuild prompt.
- **Best (agentic like Claude)**: Add a built-in tool:
  ```clojure
  {:name "invoke_skill"
   :description "Load and activate a skill by name"
   :input_schema {:type "object"
                  :properties {:name {:type "string"}
                               :arguments {:type "string"}}}}
  ```
  When the model calls it → `(set-active-skill! name)` → continue the turn with injected prompt.
- **Auto-invoke**: On every LLM call, run a cheap classification (small model or keyword) to pick active skill based on `description`.

#### 4. Tool Handling (bundled definitions + references)
- Skills declare `:allowed-tools` in frontmatter → when active, filter your tool list to only those (security + focus).
- If a skill assumes platform tools (e.g., `bash workdir:...`, `process:...` from the coding-agent example), implement equivalents in Clojure:
  - `exec-bash` tool that supports `workdir` + background (use `clojure.java.shell` or process libs).
  - Or ignore and let the prompt adapt (many skills are pure reasoning).
- **Custom tool schemas in skills** (rare but supported): Extend metadata with `:custom-tools` vector → merge into your LLM tools list when active.

Your core tools stay in a separate `tools-registry` map (name → {:schema ... :impl fn}).

#### 5. Agent Loop Integration
```clojure
;; In your main agent call
(defn call-llm-with-skills [messages enabled-ids active-id]
  (let [system (build-system-prompt base-system enabled-ids active-id)
        tools (filter-tools-for-active active-id)] ; respect allowed-tools
    (llm-api-call (cons {:role "system" :content system} messages)
                  :tools tools)))

;; Tool executor: normal + special handling for invoke_skill
```

#### 6. Optional Advanced Features (Claude/OpenClaw parity)
- **Dynamic `!command`**: Pre-process prompt body; replace `!`shell` with `(shell/exec ...)` output (sandboxed!).
- **Subagents (`context: fork`)**: Spawn a child agent instance with isolated context + skill prompt.
- **Hooks / lifecycle**: Ignore or implement via metadata.
- **Security**: Add a scanner (simple regex for "ignore previous instructions", base64, etc.) before loading. Many community skills are audited; others have issues (see Snyk reports).

#### 7. Directory Structure (your project)
```
your-agent/
├── skills/ ; drop community SKILL.md here
│ ├── coding-agent/
│ │ └── SKILL.md
│ └── my-custom-skill/SKILL.md
├── src/your_agent/
│ ├── skills.clj
│ ├── core.clj
│ └── tools.clj
└── config.edn ; enabled-skills list
```

#### Getting Started in 30 Minutes
1. Add `[clj-yaml "0.7.0"]` (or latest).
2. Create `skills/` + `load-all-local-skills!` on startup.
3. Add the `invoke_skill` tool + prompt builder.
4. Test with one popular skill: `steipete/coding-agent` or any from ClawHub/GitHub (they all parse the same).

This architecture is **100% plug-and-play**: Any SKILL.md from OpenClaw, Claude Code, Cursor, or the open standard just works. You control execution and tools; the community provides the expert prompts and workflows.

Want me to give you a full minimal repo skeleton (with example parsing + invoke tool), help map a specific community skill to your tools, or extend for folders/dynamic `!` commands? Just say the word.
Inserted in:clojure
