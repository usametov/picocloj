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
;; How to run (one-time setup):
;; 1. mkdir picoclaw-clj && cd picoclaw-clj
;; 2. Create deps.edn and src/picoclaw/core.clj (copy below)
;; 3. clj -M -m picoclaw.core onboard
;; 4. Edit ~/.picoclaw/config.edn (put your real api-key + model)
;; 5. clj -M -m picoclaw.core agent
;;
;; deps.edn
;; --------
;; {:deps {clj-http/clj-http {:mvn/version "3.13.0"}
;; cheshire/cheshire {:mvn/version "5.13.0"}
;; org.clojure/tools.cli {:mvn/version "1.0.214"}}
;; :paths ["src"]}

(ns picoclaw.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))

(def config-dir (str (System/getProperty "user.home") "/.picoclaw"))
(def config-path (str config-dir "/config.edn"))

(def default-config
  {:agents {:defaults {:model-name "gpt-4o-mini"
                       :temperature 0.7
                       :max-tokens 4096}}
   :model-list [{:name "default"
                 :base-url "https://api.openai.com/v1"
                 :api-key "sk-XXXXXXXXXXXXXXXX"
                 :model "gpt-4o-mini"}]})

(defn ensure-config []
  (let [dir (io/file config-dir)]
    (when-not (.exists dir) (.mkdirs dir)))
  (when-not (.exists (io/file config-path))
    (spit config-path (pr-str default-config))
    (println "✅ Config created at" config-path)
    (println " Edit it with your real api-key and model!")
    (System/exit 0)))

(defn load-config []
  (ensure-config)
  (read-string (slurp config-path)))

(defn chat-completion [config messages]
  (let [model-cfg (first (:model-list config))
        url (str (:base-url model-cfg) "/chat/completions")
        body {:model (:model model-cfg)
                   :messages messages
                   :temperature (get-in config [:agents :defaults :temperature])
                   :max_tokens (get-in config [:agents :defaults :max-tokens])}]
    (try
      (-> (http/post url
                     {:headers {"Authorization" (str "Bearer " (:api-key model-cfg))}
                      :body (json/generate-string body)
                      :content-type :json
                      :accept :json})
          :body
          (json/parse-string true)
          :choices
          first
          :message
          :content)
      (catch Exception e
        (str "ERROR: " (.getMessage e))))))

(defn agent-loop [config]
  (println "🦐 PicoClaw Clojure edition ready! (type 'quit' to exit)")
  (loop [history [{:role "system" :content "You are PicoClaw — a tiny, fast, helpful AI assistant inspired by the $10-hardware Go version."}]]
    (print "👤 You: ")
    (flush)
    (let [input (str/trim (read-line))]
      (if (or (= input "quit") (= input "exit"))
        (println "👋 Bye!")
        (let [new-history (conj history {:role "user" :content input})
              reply (chat-completion config new-history)]
          (println "🦐 PicoClaw:" reply)
          (recur (conj new-history {:role "assistant" :content reply})))))))

(def cli-options
  [["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options arguments]} (parse-opts args cli-options)]
    (cond
      (or (= (first arguments) "onboard") (empty? arguments))
      (ensure-config)

      (= (first arguments) "agent")
      (agent-loop (load-config))

      :else
      (do (println "Usage: clj -M -m picoclaw.core <onboard|agent>")
          (System/exit 1)))))

;; To build a native binary later (optional):
;; graalvm + native-image (exactly like the original Go binary spirit)
```

**That’s it — a fully working minimum-viable PicoClaw in Clojure.**

- Same workflow as the original (`onboard` → config → `agent`)
- Same ultra-light philosophy (tiny codebase, single binary possible with GraalVM)
- Ready to extend with tools, memory persistence, MCP, voice, etc. (just add more keys to the config and a tool-calling parser in the loop)

Run it today and you have your own PicoClaw on the JVM! 🦐

(If you want the Babashka single-script version or full tool-calling ReAct agent, just say the word — I can ship v2 in one message.)
Inserted in:clojure.
