(ns astanova.shell-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [astanova.picocloj :as pico]
            [astanova.skills :as skills]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn load-shell-helper-skill []
  (let [skill-path (str (System/getProperty "user.home") "/.picocloj/skills/shell-helper/SKILL.md")]
    (when (.exists (io/file skill-path))
      (skills/load-skill! skill-path))))

(defn skills-fixture [f]
  (load-shell-helper-skill)
  (f))

(use-fixtures :once skills-fixture)

(deftest shell-command-tool-test
  (testing "shell_command executes simple command"
    (let [tool-impl (get-in @pico/tools-registry ["shell_command" :impl])]
      (is (some? tool-impl) "shell_command tool should be registered")
      (let [result (tool-impl {:command "echo" :args ["hello"]})]
        (is (= 0 (:exit result)) "exit code should be zero")
        (is (str/includes? (:out result) "hello") "output should contain hello")
        (is (str/blank? (:err result)) "stderr should be empty"))))

  (testing "shell_command with dir parameter"
    (let [tool-impl (get-in @pico/tools-registry ["shell_command" :impl])
          tmp-dir (System/getProperty "java.io.tmpdir")
          canonical-tmp-dir (.getCanonicalPath (io/file tmp-dir))
          result (tool-impl {:command "pwd" :dir tmp-dir})
          output (str/trim (:out result))]
      (is (= 0 (:exit result)) "exit code zero")
      (is (= canonical-tmp-dir output) "output should match canonical temp dir")))

  (testing "shell_command with command string splitting"
    (let [tool-impl (get-in @pico/tools-registry ["shell_command" :impl])]
      ;; command without args vector should be split
      (let [result (tool-impl {:command "echo hello world"})]
        (is (= 0 (:exit result)))
        (is (str/includes? (:out result) "hello world")))
      ;; command with empty args vector should be split
      (let [result (tool-impl {:command "echo hello" :args []})]
        (is (= 0 (:exit result)))
        (is (str/includes? (:out result) "hello")))))

  (testing "shell_command error handling"
    (let [tool-impl (get-in @pico/tools-registry ["shell_command" :impl])
          result (tool-impl {:command "false"})]
      (is (not= 0 (:exit result)) "non-zero exit code")
      (is (str/blank? (:out result)) "stdout empty")
      (is (str/blank? (:err result)) "stderr empty for false command"))))

(deftest shell-helper-skill-test
  (testing "shell-helper skill loads correctly"
    (let [skill (skills/get-skill "shell-helper")]
      (is (some? skill) "shell-helper skill should be loaded")
      (is (= #{"shell_command"} (:allowed-tools skill)) "allowed-tools includes shell_command")
      (is (str/includes? (:prompt skill) "shell helper mode") "prompt contains mode")))

  (testing "tool filtering for shell-helper skill"
    (let [filtered-tools (pico/filter-tools-for-skill "shell-helper")]
      (is (contains? filtered-tools "shell_command") "shell_command should be present")
      (is (not (contains? filtered-tools "simple_calc")) "simple_calc should not be present")
      (is (not (contains? filtered-tools "invoke_skill")) "invoke_skill should not be present"))))

(deftest shell-command-safety-test
  (testing "command injection attempt"
    (let [tool-impl (get-in @pico/tools-registry ["shell_command" :impl])
          result (tool-impl {:command "echo" :args ["hello; rm -rf /"]})]
      ;; The ; should be passed as literal argument, not interpreted by shell
      (is (str/includes? (:out result) "hello; rm -rf /")
          "semicolon should be treated as literal argument"))))