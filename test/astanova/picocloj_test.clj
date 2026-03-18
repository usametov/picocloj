(ns astanova.picocloj-test
  (:require [clojure.test :refer [deftest is testing]]
            [astanova.picocloj :as sut]))

(deftest greet-test
  (testing "greet prints greeting"
    (let [output (with-out-str (sut/greet {:name "Test"}))]
      (is (.contains output "Hello, Test! This is PicoClaw Clojure edition.")))))

(deftest config-path-test
  (testing "config path is in user home"
    (is (.contains sut/config-path (System/getProperty "user.home")))))
