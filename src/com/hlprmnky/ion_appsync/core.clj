(ns com.hlprmnky.ion-appsync.core
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [datomic.client.api :as d]
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

(defn items-by-type*
  "Returns info about items matching type"
  [db type]
  (d/q '[:find ?sku ?size ?color ?featured
         :in $ ?type
         :where
         [?e :inv/type ?type]
         [?e :inv/sku ?sku]
         [?e :inv/size ?size]
         [?e :inv/color ?color]
         [(datomic.ion.starter/feature-item? $ ?e) ?featured]]
       db type))

(defn items-by-type-json
  "items-by-type starter ion modified to emit JSON for consumption by the AWS service ecosystem"
  [{:keys [input]}]
  (let [type (keyword (get (json/read-str input) "type"))
        conn (d/connect (get-client) {:db-name "datomic-cloud-appsync"})]
    ;; NOTE that conn can - and should be - parameterized in production builds.
    ;; See the ion-starter repo and get-connection for a better but more verbose approach
    (->> (items-by-type* (d/db conn) type)
          json/write-str)))

(defn items-by-type-gql
  "GraphQL Datasource data-shape massager for items-by-type ion"
  [{:keys [input]}]
  (let [type (keyword (get (json/read-str input) "type"))
        conn (d/connect (get-client) {:db-name "datomic-cloud-appsync"})]
    ;; NOTE that conn can - and should be - parameterized in production builds.
    ;; See the ion-starter repo and get-connection for a better but more verbose approach
    (try
      (->> (items-by-type* (d/db conn) type)
           (map #(zipmap [:sku :size :color :featured] %))
           json/write-str)
      (catch Exception e (str "Exception: |"
                              (.getMessage e)
                              "|, for input: |"
                              (str input)
                              "|, resolved type data: |"
                              type
                              "|")))))


(comment
  (System/setProperty "aws.profile" "hlprmnky")
  (items-by-type-gql {:input "{\"type\" : \"hat\"}"})
  (items-by-type-gql {:input "{\"type\" : \"vibranium bracelet\"}"}))

