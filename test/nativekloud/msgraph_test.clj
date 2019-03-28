(ns nativekloud.msgraph-test
  (:require [clojure.test :refer :all]
            [nativekloud.msgraph :refer :all]
            [cheshire.core :as json]))

(deftest integartion-test-msgraph
  
  

  (testing "Fetch user folders."
    (let [token  (get-token (json/parse-string (slurp "resources/config.json") keyword))
          users  (get-users token)
          users-with-mail (filter-with-mail users)
          user (rand-nth users-with-mail)
          folders (get-user-folders user token)
          totalItemCount (totalItemCount folders)]
      (clojure.pprint/pprint {:totalItemCount totalItemCount
                              :mail (:mail user)
                              :foldersCount (count folders)})
      (is (>= totalItemCount 0))))
  

  (testing "Fetch user messages initial"
    (let [token  (get-token (json/parse-string (slurp "resources/config.json") keyword))
          users  (get-users token)
          users-with-mail (filter-with-mail users)
          user (rand-nth users-with-mail)
          folders (get-user-folders user token)
          totalItemCount (totalItemCount folders)
          total-counter (atom 0)
          ]

      (doseq [folder folders]
              (doseq [message (messages-with-state user folder {:initial true} token)]
                (swap! total-counter inc)
                  ))
      (clojure.pprint/pprint {:total-item-count-from-API totalItemCount
                              :processed-item-count @total-counter
                              :mail (:mail user)
                              :foldersCount (count folders)})
      (is (>= totalItemCount 0))))

  (testing "Fetch user messages after date"
    (let [token  (get-token (json/parse-string (slurp "resources/config.json") keyword))
          users  (get-users token)
          users-with-mail (filter-with-mail users)
          user (rand-nth users-with-mail)
          folders (get-user-folders user token)
          totalItemCount (totalItemCount folders)
          total-counter (atom 0)
          ]

      (doseq [folder folders]
              (doseq [message (messages-with-state user folder {:initial false :time-started "2019-03-27"} token)]
                (swap! total-counter inc)
                  ))
      (clojure.pprint/pprint {:total-item-count-from-API totalItemCount
                              :processed-item-count @total-counter
                              :mail (:mail user)
                              :foldersCount (count folders)})
      (is (>= totalItemCount 0)))))

(comment
  (run-tests)
  
  )
