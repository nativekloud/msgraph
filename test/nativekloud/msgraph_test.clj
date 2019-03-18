(ns nativekloud.msgraph-test
  (:require [clojure.test :refer :all]
            [nativekloud.msgraph :refer :all]
            [cheshire.core :as json]))

(deftest integartion-test-msgraph
  (set-params! (json/parse-string (slurp "resources/config.json") keyword))

  (testing "Check if we have a token."
    (is (some? (get-in @settings [:token]))))

  (testing "Fetch users."
    (is (not (empty? (get-users)))))

  (testing "Fetch groups."
    (is (not (empty? (get-groups))))))
f
