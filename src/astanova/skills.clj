(ns astanova.skills
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defrecord Skill
  [id         ; e.g. "coding-agent" or GitHub path
   name       ; display name
   description
   prompt     ; full markdown body (the injection)
   metadata   ; parsed frontmatter map
   allowed-tools ; set of strings from frontmatter (for filtering)
   custom-tools ; vector of custom tool definitions from frontmatter
   supporting ; map of filename -> content (if folder)
   ])

(defn slugify [s]
  "Convert string to lowercase slug with hyphens."
  (-> s
      str/lower-case
      (str/replace #"[^\w\s-]" "")
      (str/replace #"[\s_-]+" "-")
      (str/replace #"^-|-$" "")))

(def skills-registry (atom {})) ; id -> Skill

(defn read-content
  "Read content from a File, URL string, file path string, or content string."
  [src]
  (cond
    (instance? java.io.File src) (slurp src)
    (string? src) (if (or (str/starts-with? src "http://")
                          (str/starts-with? src "https://")
                          (.exists (io/file src)))
                    (slurp src)
                    src)
    :else (slurp src))) ; assume URI or something slurpable

(defn parse-skill-md
  "Parse SKILL.md (string or File/URL). Returns Skill or nil."
  [md-content-or-path]
  (try
    (let [content (read-content md-content-or-path)
          ;; Simple frontmatter splitter (robust enough for most skills)
          [_ front body] (re-matches #"(?s)^---\s*(.*?)\s*---\s*(.*)" content)
          meta (when front (yaml/parse-string front))]
      (when meta
        (let [name (or (:name meta) "unnamed")
              id (or (:id meta) (slugify name))]
          (->Skill
           id
           name
           (:description meta)
           (str/trim body)
           meta
           (set (:allowed-tools meta))
           (or (:custom-tools meta) [])
           {}))))
    (catch Exception e
      (println "Error parsing skill:" (.getMessage e))
      nil)))

(defn load-skill!
  "Load from local path, raw GitHub URL, or ClawHub-style name.
   Returns loaded skill or nil."
  [source]
  (try
    (let [skill (parse-skill-md source)]
      (when skill
        (swap! skills-registry assoc (:id skill) skill)
        skill))
    (catch Exception e
      (println "Error loading skill from" source ":" (.getMessage e))
      nil)))

(defn load-all-local-skills! [dir]
  "Load all SKILL.md files from directory recursively."
  (let [dir-file (io/file dir)]
    (when (.exists dir-file)
      (doseq [f (file-seq dir-file)]
        (when (and (.isFile f) (str/ends-with? (.getName f) "SKILL.md"))
          (load-skill! f))))))

(defn get-skill [id]
  "Get skill by ID from registry."
  (get @skills-registry id))

(defn list-skills []
  "List all loaded skills."
  (vals @skills-registry))

(defn build-system-prompt
  "Build system prompt with skills catalog and active skill injection."
  [base-prompt enabled-skill-ids active-skill-id]
  (let [catalog (for [id enabled-skill-ids
                      :let [skill (get @skills-registry id)]
                      :when skill]
                  (str "- " (:name skill) ": " (:description skill)))
        active-prompt (when-let [active-skill (get @skills-registry active-skill-id)]
                        (str "\n\n=== ACTIVE SKILL: " active-skill-id " ===\n"
                             (:prompt active-skill)))]
    (str base-prompt
         (when (seq catalog)
           (str "\n\nAvailable skills (use /skill-name to activate):\n"
                (str/join "\n" catalog)))
         (or active-prompt ""))))

(defn activate-skill [skill-id]
  "Activate a skill by ID. Returns skill if found, nil otherwise."
  (get @skills-registry skill-id))

(defn deactivate-skill []
  "Deactivate current skill. Returns nil."
  nil)

(defn skill-command?
  "Check if input is a skill command (/skill-name or /clear)."
  [input]
  (when (string? input)
    (re-matches #"^/(\S+)(?:\s+(.*))?" input)))

(defn parse-skill-command
  "Parse skill command into [command skill-id args]."
  [input]
  (when-let [[_ command args] (skill-command? input)]
    (let [command (str/lower-case command)]
      (cond
        (= command "clear") [:clear nil nil]
        (= command "skills") [:list nil nil]
        :else [:activate command args]))))