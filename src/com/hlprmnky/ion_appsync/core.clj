(ns com.hlprmnky.ion-appsync.core
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [datomic.client.api :as d]
   [datomic.ion.starter :as ion]
   [datomic.java.io.bbuf :as bbuf]))

;; We borrow the core items-by-type ion and get-client
;; from the Ion Starter repo: https://github.com/datomic/ion-starter/

(def get-client
  "This function will return a local implementation of the client
interface when run on a Datomic compute node. If you want to call
locally, fill in the correct values in the map."
  (memoize #(d/client {:server-type :ion
                       :region "us-east-2"                                                       ;; THESE NEED TO HAVE
                       :system "datomic-cloud-appsync"                                           ;; CORRECT VALUES
                       :query-group "datomic-cloud-appsync"                                      ;; FOR YOUR SPECIFIC
                       :endpoint "http://entry.datomic-cloud-appsync.us-east-2.datomic.net:8182" ;; DATOMIC CLOUD INSTANCE
                       :proxy-port 8182})))

(defn items-by-type-json
  "items-by-type starter ion modified to emit JSON for consumption by the AWS service ecosystem"
  [{:keys [input]}]
  (let [type (keyword (get (json/read-str input) "type"))
        conn (d/connect (get-client) {:db-name "datomic-cloud-appsync"})]
    ;; NOTE that conn can - and should be - parameterized in production builds.
    ;; See the ion-starter repo and get-connection for a more production-ready approach with Datomic schema validation, etc.
    (->> (ion/items-by-type* (d/db conn) type)
          json/write-str)))

(defn items-by-type-gql
  "GraphQL Datasource data-shape massager for items-by-type ion"
  [{:keys [input]}]
  (let [type (keyword (get (json/read-str input) "type"))
        conn (d/connect (get-client) {:db-name "datomic-cloud-appsync"})]
    ;; NOTE that conn can - and should be - parameterized in production builds.
    ;; See the ion-starter repo and get-connection for a more production-ready approach with Datomic schema validation, etc.
    (try
      (->> (ion/items-by-type* (d/db conn) type)
           (map #(zipmap [:sku :size :color :featured] %))
           json/write-str)
      (catch Exception e (str "Exception: |"
                              (.getMessage e)
                              "|, for input: |"
                              (str input)
                              "|, resolved type data: |"
                              type
                              "|")))))
(defn add-item-return-item
  "Add an item but return the item rather than the basis-t so that AppSync @subscribe directive can return the item
"
  [{:keys [input]}]
  (let [parsed (json/read-str input)
        added {:sku (get parsed "sku")
               :color (get parsed "color")
               :size (get parsed "size")
               :type (get parsed "type")}
        result (ion/add-item {:input input})]
    (json/write-str added))) ;; could do error handling with result here

(comment
  (items-by-type-json {:input "{\"type\" : \"hat\"}"})
  (items-by-type-gql {:input "{\"type\" : \"shirt\"}"})
  (items-by-type-gql {:input "{\"type\" : \"vibranium bracelet\"}"})
  (add-item-return-item {:input "{\"sku\": \"TEST-123\", \"color\": \"blue\", \"size\": \"large\", \"type\": \"shirt\"}"}))

