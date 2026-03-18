(ns astanova.picocloj
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "! This is PicoClaw Clojure edition.")))

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
