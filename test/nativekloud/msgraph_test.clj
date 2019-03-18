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
    (is (not (empty? (get-groups)))))

  (testing "Fetch user folders."
    (let [users  (get-users)
          users-with-mail (filter-with-mail users)
          user (rand-nth users-with-mail)
          folders (get-user-folders user)
          totalItemCount (totalItemCount folders)]
      (clojure.pprint/pprint {:totalItemCount totalItemCount
                              :mail (:mail user)
                              :foldersCount (count folders)})
      (is (>= totalItemCount 0))))

  ;; Need to ask for more permissions
  ;; (testing "Fetch group conversations."
  ;;   (let [groups   (get-groups)
  ;;         groups-with-mail (filter-with-mail groups)
  ;;         group (rand-nth groups-with-mail)
  ;;         conversations (get-group-conversations group)
  ;;         ]
  ;;     (clojure.pprint/pprint {:group group
  ;;                             :conversation (rand-nth conversations)})
  ;;     (is (>= (count conversations) 0))))
  )

  
(comment
  (run-tests)
  )
