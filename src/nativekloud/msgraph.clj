(ns nativekloud.msgraph
  (:require [clj-http.conn-mgr :as conn-mgr]
            [clj-http.client :as http]
            [safely.core :refer [safely]]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer [throw+ try+]]))


;;; API


(def version "v1.0")

(defn trunc
    [s n]
    (subs s 0 (min (count s) n)))


(def api-base-url (str  "https://graph.microsoft.com" "/" version))

(defn build-url [path]
  (str api-base-url path))

(def cm (conn-mgr/make-reusable-conn-manager {:timeout 5 :threads 20}))

(defn- api-token-url [tenant_id]
  (str "https://login.microsoftonline.com/" tenant_id "/oauth2/v2.0/token"))


(defn get-token [config]
  (:body (http/post (api-token-url (:tenant_id config))
                    {:form-params  (:params config)
                     :as                 :json
                     :timeout            5
                     :connection-manager cm
                     :throw-exceptions   true})))

;; API clj-http
(defn call-api [url token]
  (safely
    (try+
   ;(log/info "Requesting: " (trunc url 200) "...")
   (http/get url {:oauth-token        (:access_token token)
                  :as                 :json
                  :debug              false
                  :throw-exceptions   true
                  :connection-manager cm
                  })
                        ;
   (catch [:status 404] {:keys [request-time headers body]}
     (log/warn "404: Resource not found" url))

   (catch [:status 400] {:keys [request-time headers body]}
     (log/warn "400: Bad request" url))

   (catch [:status 401] {:keys [request-time headers body]}
     (log/warn "401: Auth error" url))
   
   (catch Object _
     (throw+)))
  
   :on-error
   :max-retry 5
   :track-as "etl.msgraph.client"
   :log-stacktrace false
   )
  )


;; API call

(defn api-get
  "Calls msgraph API and handles paged results.
  Returns vector of results"
  [url token]
  (let [url (str api-base-url url)]
    (loop [response (:body (call-api url token))
           results  []]
      (if (nil? ((keyword "@odata.nextLink") response))
        (concat results (:value response))
        (recur (:body (call-api ((keyword "@odata.nextLink") response) token))
               (concat results (:value response)))))))

;; (defn api-get-callback
;;   "Calls msgraph API and handles paged results.
;;   Returns vector of results"
;;   [url user fn]
;;   (let [url (str api-base-url url)]
;;     (loop [response (:body (call-api url))
;;            results  (fn user (:value response))]
;;       (if (nil? ((keyword "@odata.nextLink") response))
;;         (fn user (:value response))
;;         (recur (:body (call-api ((keyword "@odata.nextLink") response)))
;;                (fn user (:value response)))))))


;; (defn api-get-delta
;;   "Delta query enables applications to discover newly created, updated, or deleted
;;   entities without performing a full read of the target resource with every request.
;;   Microsoft Graph applications can use delta query to efficiently synchronize changes
;;   with a local data store.
;;   See docs at https://docs.microsoft.com/en-us/graph/delta-query-overview
;;   Returns map {:resuls [] :deltaLink url} " 
;;   [url]
;;   (loop [response (:body (call-api url))
;;          results  []]
;;     (if (nil? ((keyword "@odata.nextLink") response))
;;       {:results  (concat results (:value response))
;;        :deltaLink ((keyword "@odata.deltaLink") response)} 
;;       (recur (:body (call-api ((keyword "@odata.nextLink") response)))
;;              (concat results (:value response))))))


;; (defn api-get-delta-callback
;;   "Delta query enables applications to discover newly created, updated, or deleted
;;   entities without performing a full read of the target resource with every request.
;;   Microsoft Graph applications can use delta query to efficiently synchronize changes
;;   with a local data store.
;;   See docs at https://docs.microsoft.com/en-us/graph/delta-query-overview
;;   Returns map {:resuls [] :deltaLink url} " 
;;   [url fn]
;;   (loop [response (:body (call-api url))
;;          results  (fn (:value response))]
;;     (if (nil? ((keyword "@odata.nextLink") response))
;;       {:deltaLink ((keyword "@odata.deltaLink") response)}
;;       (recur (:body (call-api ((keyword "@odata.nextLink") response)))
;;              (fn (:value response))))))


(defn get-users
  "Lists users in the organization."
  [token]
  (api-get "/users?$top=999" token))

(defn get-groups [token]
  (api-get "/groups?$top=999" token))

(defn get-domains [token]
  (api-get "/domains" token))

(defn filter-with-mail [users]
  (filter #(not-empty (:mail %)) users))

;; Folders

(defn mailFolders [user token]
  (api-get (str "/users/" (:id user) "/mailFolders") token))

(defn childFolders [user folder token]
  (api-get (str "/users/" (:id user) "/mailFolders/" (:id folder) "/childFolders") token))


(defn get-all-childFolders [user folders token]
  (loop [with-children (filter #(not=  0 (:childFolderCount %)) folders)
         response (flatten (filter not-empty (map #(childFolders user % token) with-children)))
         children []]
    (if (empty? with-children)
      (concat children response)
      (recur (filter #(not= 0 (:childFolderCount %)) response)
             (flatten (filter not-empty (map #(childFolders user % token) with-children)))
             (concat children response) ))))

(defn get-user-folders [user token]
  (let [folders (mailFolders user token)
        children (get-all-childFolders user folders token)
        all (concat folders children)]
    all))

;; (defn get-group-conversations [group]
;;   (api-get (str "/groups/" (:id group) "/conversations")))

;; Messages

(defn messages [user folder token]
  (api-get (str "/users/" (:id user) "/mailFolders/" (:id folder) "/messages") token))

(defn messages-since [user folder date token]
  "date format ?$filter=ReceivedDateTime ge 2019-04-01"
  (api-get
   (str "/users/" (:id user) "/mailFolders/" (:id folder) "/messages?$filter=ReceivedDateTime ge " date)
   token))

(defn messages-with-state [user folder state token]
  (if (:initial state)
    (messages user folder token)
    (messages-since user folder (:time-started state) token)))

(defn list-attachments [user folder msg token]
  "List message attachments  
    /users/{id | userPrincipalName}/mailFolders/{id}/childFolders/{id}/messages/{id}/attachments"
  (api-get
   (str "/users/" (:id user) "/mailFolders/" (:id folder) "/messages/" (:id msg) "/attachments")
   token))

(defn get-attachment [user folder msg attachment token]
  "Get attachment  
    /users/{id | userPrincipalName}/mailFolders/{id}/childFolders/{id}/messages/{id}/attachments/{id}"
  (api-get
   (str "/users/" (:id user) "/mailFolders/" (:id folder) "/messages/" (:id msg) "/attachments/" (:id attachment) )
   token))

;; (defn messages-callback [user folder fn]
;;   (when-not (zero? (:totalItemCount folder))
;;     (log/info "geting messages in folder " (:displayName folder) " totalItemCount:" (:totalItemCount folder) )
;;     (api-get-callback (str "/users/" (:id user) "/mailFolders/" (:id folder) "/messages?$top=500") user fn)))


(defn get-all-messages [user token]
  (doseq [folder (get-user-folders user token)] (messages user folder token)))

(defn accounts [token]
  (let [users (get-users token)
        groups (get-groups token)]
    (concat users groups)))

(defn totalItemCount [folders]
  (reduce (fn [sum folder] (+ (:totalItemCount folder) sum))
                                   0
                                   folders))

