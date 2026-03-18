(ns astanova.picocloj
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [astanova.skills :as skills])
  (:gen-class))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "! This is PicoClaw Clojure edition.")))

(def config-dir (str (System/getProperty "user.home") "/.picocloj"))
(def config-path (str config-dir "/config.edn"))

(def default-config
  {:agents {:defaults {:model-name "gpt-4o-mini"
                       :temperature 0.7
                       :max-tokens 4096}}
   :model-list [{:name "default"
                 :base-url "https://api.openai.com/v1"
                 :api-key "sk-XXXXXXXXXXXXXXXX"
                 :model "gpt-4o-mini"}]
   :skills {:enabled ["example"]
            :skills-dir (str config-dir "/skills")}})

(defn ensure-config []
  (let [dir (io/file config-dir)]
    (when-not (.exists dir) (.mkdirs dir)))
  (let [skills-dir (io/file (str config-dir "/skills"))]
    (when-not (.exists skills-dir) (.mkdirs skills-dir))
    ;; Copy example skill if it doesn't exist
    (let [example-skill-dir (io/file skills-dir "example")
          example-skill-file (io/file example-skill-dir "SKILL.md")]
      (when-not (.exists example-skill-dir) (.mkdirs example-skill-dir))
      (when-not (.exists example-skill-file)
        (spit example-skill-file "---
id: example
name: Example Assistant
description: An example skill that makes PicoClaw more helpful and enthusiastic
allowed-tools: []
version: 1.0.0
author: PicoClaw Team
---

You are now in **example mode**. This is a demonstration of how skills work in PicoClaw.

## Guidelines
- Be extra enthusiastic and helpful
- Use emojis where appropriate 🎉
- Explain your reasoning clearly
- Keep responses concise but informative

## Example Interactions

**User**: \"Hello!\"
**You**: \"Hey there! 👋 I'm excited to help you today! What can I assist you with?\"

**User**: \"How does this work?\"
**You**: \"Great question! 🤔 I'm using the Example Assistant skill right now. Skills are modular prompts that enhance my capabilities for specific tasks!\"

**User**: \"Can you help me with code?\"
**You**: \"Absolutely! 💻 I'd love to help with coding. What language or problem are you working on?\"

Remember: Stay positive, helpful, and engage with the user's questions enthusiastically!")
        (println "📁 Example skill copied to" (.getPath example-skill-file)))))
  (when-not (.exists (io/file config-path))
    (spit config-path (pr-str default-config))
    (println "✅ Config created at" config-path)
    (println " Edit it with your real api-key and model!")
    (println " Skills directory created at" (str config-dir "/skills"))
    (System/exit 0)))

(defn load-config []
  (ensure-config)
  (let [config (read-string (slurp config-path))
        ;; Merge with defaults to ensure new keys are present
        merged-config (merge default-config config)
        ;; Ensure skills directory exists in config
        skills-dir (or (get-in merged-config [:skills :skills-dir])
                       (str config-dir "/skills"))]
    (assoc-in merged-config [:skills :skills-dir] skills-dir)))

;; Tool Registry
(def tools-registry (atom {}))

(defn register-tool! [name description schema impl]
  (swap! tools-registry assoc name {:description description :schema schema :impl impl}))

;; Basic tools
(register-tool!
 "invoke_skill"
 "Activate a specific skill by name"
 {:type "object"
  :properties {:name {:type "string" :description "Name of skill to activate"}
               :reason {:type "string" :description "Why this skill is needed"}}
  :required [:name]}
 (fn [args]
   (let [skill-id (:name args)
         skill (skills/get-skill skill-id)]
     (if skill
       {:success true :skill skill-id :message (str "Activated skill: " skill-id)}
       {:success false :message (str "Skill not found: " skill-id)}))))

(register-tool!
 "get_current_time"
 "Get the current date and time"
 {:type "object"
  :properties {}
  :required []}
 (fn [_]
   {:time (java.time.LocalDateTime/now)
    :formatted (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")
                        (java.time.LocalDateTime/now))}))

(register-tool!
 "simple_calc"
 "Evaluate a simple arithmetic expression"
 {:type "object"
  :properties {:expression {:type "string" :description "Simple arithmetic expression"}}
  :required [:expression]}
 (fn [args]
   (try
     (let [result (eval (read-string (str "(+ " (:expression args) ")")))]
       {:result result :expression (:expression args)})
     (catch Exception e
       {:error (.getMessage e) :expression (:expression args)}))))

(defn filter-tools-for-skill [skill-id]
  "Filter tools based on skill's allowed-tools list."
  (if-let [skill (skills/get-skill skill-id)]
    (let [allowed (:allowed-tools skill)]
      (if (empty? allowed)
        @tools-registry  ; If no restrictions, all tools allowed
        (select-keys @tools-registry allowed)))
    @tools-registry))  ; If no skill active, all tools allowed

(def base-system-prompt
  "You are PicoClaw — a tiny, fast, helpful AI assistant inspired by the $10-hardware Go version.
You can use tools when needed to help the user. Be concise and helpful.

Skills are specialized modes you can activate using /skill-name commands. When a skill is active,
follow its specific instructions while still being helpful to the user.")

(defn tools-to-openai-format [tools-map]
  "Convert tools registry map to OpenAI tools array format."
  (mapv (fn [[name {:keys [description schema]}]]
          {:type "function"
           :function {:name name
                      :description (or description "")
                      :parameters schema}})
        tools-map))

(defn chat-completion
  "Call LLM API with optional tools.
   Returns {:role :assistant :content ...} or {:role :assistant :tool_calls ...}"
  [config messages & {:keys [tools] :or {tools nil}}]
  (let [model-cfg (first (:model-list config))
        url (str (:base-url model-cfg) "/chat/completions")
        body (merge {:model (:model model-cfg)
                     :messages messages
                     :temperature (get-in config [:agents :defaults :temperature])
                     :max_tokens (get-in config [:agents :defaults :max-tokens])}
                    (when tools {:tools tools}))]
    (try
      (let [response (-> (http/post url
                                    {:headers {"Authorization" (str "Bearer " (:api-key model-cfg))}
                                     :body (json/generate-string body)
                                     :content-type :json
                                     :accept :json})
                         :body
                         (json/parse-string true))
            message (-> response :choices first :message)]
        {:role :assistant
         :content (:content message)
         :tool_calls (:tool_calls message)})
      (catch Exception e
        {:role :assistant :content (str "ERROR: " (.getMessage e))}))))

(defn agent-loop [config]
  (println "🦐 PicoClaw Clojure edition ready! (type 'quit' to exit)")
  (println "  Use /skills to list skills, /skill-name to activate, /clear to deactivate")

  ;; Load skills from configured directory
  (let [skills-dir (get-in config [:skills :skills-dir])
        enabled-skill-ids (get-in config [:skills :enabled] [])]
    (println "📂 Loading skills from" skills-dir)
    (skills/load-all-local-skills! skills-dir)
    (println "✅ Loaded" (count (skills/list-skills)) "skills"))

  (loop [history []
         active-skill-id nil]
    (print "👤 You: ")
    (flush)
    (let [input (str/trim (read-line))]
      (cond
        (or (= input "quit") (= input "exit"))
        (println "👋 Bye!")

        ;; Skill commands
        (str/starts-with? input "/")
        (let [[command skill-id args] (skills/parse-skill-command input)
              enabled-skill-ids (get-in config [:skills :enabled] [])]
          (case command
            :clear
            (do (println "🧹 Cleared active skill")
                (recur history nil))

            :list
            (do (println "\n📚 Available skills:")
                (doseq [skill (skills/list-skills)]
                  (println (str "  /" (:id skill) " - " (:description skill))))
                (when active-skill-id
                  (println (str "\nActive skill: /" active-skill-id)))
                (recur history active-skill-id))

            :activate
            (if (skills/get-skill skill-id)
              (do (println "🎯 Activated skill:" skill-id)
                  (recur history skill-id))
              (do (println "❌ Skill not found:" skill-id)
                  (println "   Use /skills to see available skills")
                  (recur history active-skill-id)))

            ;; Unknown command
            (do (println "❓ Unknown command. Use /skills, /skill-name, or /clear")
                (recur history active-skill-id))))

        ;; Normal user input
        :else
        (let [;; Build system prompt with skills
              system-prompt (skills/build-system-prompt
                              base-system-prompt
                              (get-in config [:skills :enabled] [])
                              active-skill-id)

              ;; Filter tools based on active skill
              filtered-tools (filter-tools-for-skill active-skill-id)
              openai-tools (when (seq filtered-tools)
                             (tools-to-openai-format filtered-tools))

              ;; Prepare messages with system prompt
              messages (cond-> []
                         (not (str/blank? system-prompt))
                         (conj {:role "system" :content system-prompt})

                         (seq history)
                         (into history)

                         :always
                         (conj {:role "user" :content input}))

              ;; Call LLM with tools
              response (chat-completion config messages :tools openai-tools)

              ;; Handle tool calls if present
              new-history (conj history {:role "user" :content input})
              ;; Execute tool calls if present, returns [final-response updated-skill-id]
              [final-response updated-skill-id] (if-let [tool-calls (:tool_calls response)]
                                                  ;; Execute tool calls (simplified - single tool call)
                                                  (let [tool-call (first tool-calls)
                                                        tool-name (get-in tool-call [:function :name])
                                                        tool-args (json/parse-string (get-in tool-call [:function :arguments]) true)
                                                        tool-impl (get-in @tools-registry [tool-name :impl])
                                                        tool-result (if tool-impl
                                                                      (tool-impl tool-args)
                                                                      {:error (str "Tool not found: " tool-name)})
                                                        result-str (pr-str tool-result)
                                                        updated-skill-id (if (and (= tool-name "invoke_skill")
                                                                                  (:success tool-result))
                                                                           (:skill tool-result)
                                                                           active-skill-id)]
                                                    (println "🔧 Executed tool:" tool-name "->" result-str)
                                                    (when (and (= tool-name "invoke_skill")
                                                               (:success tool-result))
                                                      (println "🎯 Activated skill via tool:" updated-skill-id))
                                                    [{:role "assistant" :content (str "Tool result: " result-str)}
                                                     updated-skill-id])
                                                  [response active-skill-id])]

          (println "🦐 PicoClaw:" (:content final-response))
          (recur (conj new-history final-response) updated-skill-id))))

(def cli-options
  [["-h" "--help"]])

(defn -main
  [& args]
  (let [{:keys [options arguments]} (parse-opts args cli-options)]
    (cond
      (or (= (first arguments) "onboard") (empty? arguments))
      (ensure-config)

      (= (first arguments) "agent")
      (agent-loop (load-config))

      :else
      (do (println "Usage: clj -M -m astanova.picocloj <onboard|agent>")
          (System/exit 1)))))
