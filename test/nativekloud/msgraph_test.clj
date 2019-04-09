(ns nativekloud.msgraph-test
  (:require [clojure.test :refer :all]
            [nativekloud.msgraph :refer :all]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(deftest integartion-test-msgraph
  
  

  (testing "Fetch user folders."
    (let [token  (get-token (json/parse-string (slurp "resources/config.json") keyword))
          users  (get-users token)
          users-with-mail (filter-with-mail users)
          user (last users-with-mail)
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
          user (last users-with-mail)
          folders (get-user-folders user token)
          totalItemCount (totalItemCount folders)
          total-counter (atom 0)
          attachment-count (atom 0)
          ]

      (doseq [folder folders]
        (let [messages (messages-with-state user folder {:initial true} token)
              messages-with-attachments (filter :hasAttachments messages)] 
          (doseq [message messages]
            (swap! total-counter inc)
            )
          (doseq [message-with-attachments messages-with-attachments]
            (let [attachments (list-attachments user folder message-with-attachments token)]
              (doseq [attachment attachments]
                (clojure.pprint/pprint (dissoc attachment :contentBytes))
                (swap! attachment-count inc))))
          ))
      (clojure.pprint/pprint {:total-item-count-from-API totalItemCount
                                  :processed-item-count @total-counter
                                  :mail (:mail user)
                                  :foldersCount (count folders)
                              :with-attach @attachment-count})
  
      (is (= totalItemCount @total-counter ))))

  (testing "Fetch user messages after date"
    (let [token  (get-token (json/parse-string (slurp "resources/config.json") keyword))
          users  (get-users token)
          users-with-mail (filter-with-mail users)
          user (last users-with-mail)
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
