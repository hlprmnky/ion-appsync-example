(ns pages.handlers
  (:require
    [cljs.pprint :refer [pprint]]

    [goog.string :as gstring]
    [goog.string.format]
    [goog.crypt.base64 :as base64]

    [clojure.string :as str]

    ["request" :as req]
    ["request-promise-native" :as reqp]

    [pages.views :as views]))

(defn ->clj
      "parse a json string into a cljs map"
      [json-string]
      (js->clj (js/JSON.parse json-string) :keywordize-keys true))

(defn -js->clj+
      "For cases when built-in js->clj doesn't work. Source: https://stackoverflow.com/a/32583549/4839573"
      [x]
      (into {} (for [k (js-keys x)]
                    [k (aget x k)])))

(defn requestp
      "send a request returning a promise"
      [url-or-opts]
      (reqp (clj->js url-or-opts)))

(defn cognito-config
      [event]
      (let [event (js->clj event :keywordize-keys true)
            env (-js->clj+ (.-env js/process))
            cognito-domain (get env "CognitoDomain")
            api-id (get-in event [:requestContext :apiId])
            api-stage (get-in event [:requestContext :stage])
            region (get env "AWS_REGION")]
           {:aws-region     region
            :api-server     (gstring/format "https://%s.execute-api.%s.amazonaws.com/%s"
                                            api-id
                                            region
                                            api-stage)
            :api-stage      api-stage
            :cognito-domain cognito-domain
            :cognito-server (gstring/format "https://%s.auth.%s.amazoncognito.com"
                                            cognito-domain
                                            region)
            :cognito-pool   (get env "CognitoPool")
            ; TODO can IAM provide access to the cognito service without the secret in the env?
            :client-id      (get env "CognitoClientId")
            :client-secret  (get env "CognitoClientSecret")}))

(defn app-url
      [{:keys [api-server] :as config}]
      (str api-server "/app"))

(defn home-url
      [{:keys [api-server]}]
      (str api-server "/home"))

(defn login-url
      [{:keys [client-id cognito-server] :as config}]
      (gstring/format "%s/login?client_id=%s&response_type=code&redirect_uri=%s"
                      cognito-server
                      client-id
                      (app-url config)))

(defn logout-url
      [{:keys [client-id cognito-server] :as config}]
      (gstring/format "%s/logout?client_id=%s&logout_uri=%s" cognito-server client-id (home-url config)))

(defn home-page
      [event context callback]
      (let [markup (views/home {:urls {:login (login-url (cognito-config event))}})]
           (callback nil (clj->js {:body       markup
                                   :statusCode 200
                                   :headers    {:Content-Type "text/html"}}))))

(defn jwt-data
      "checks structure and returns parsed data from a Cognito JWT token"
      [token]
      (let [parts (str/split token #"\.")]
           (when (not= 3 (count parts))
                 (throw (ex-info "invalid JWT token" {:count (count parts)})))
           (let [[header payload signature] parts]
                {:header    (-> header base64/decodeString ->clj)
                 :payload   (-> payload base64/decodeString ->clj)
                 :signature signature})))

; https://aws.amazon.com/premiumsupport/knowledge-center/decode-verify-cognito-json-token/
; https://github.com/auth0/node-jsonwebtoken
(defn validate-token                                        ; TODO !!!!
      [token-data]
      (let []))

(defn app-page
      [event context callback]
      (let [event-map (js->clj event :keywordize-keys true)
            auth-code (get-in event-map [:queryStringParameters :code])
            {:keys [aws-region cognito-pool api-stage client-id client-secret cognito-domain cognito-server] :as config} (cognito-config event)
            token-request {:method  "POST"
                           :url     (str cognito-server "/oauth2/token/") ; trailing / is important!
                           :headers {:Authorization (str "Basic " (base64/encodeString (str client-id ":" client-secret)))}
                           ; form data also sets Content-Type = application/x-www-form-urlencoded
                           :form    {:grant_type   "authorization_code"
                                     :client_id    client-id
                                     :code         auth-code
                                     :redirect_uri (app-url config)}}
            env (-js->clj+ (.-env js/process))]

           (-> (requestp token-request)
               (.then (fn [body]
                          (pprint (str "stage: " api-stage))
                          (let [{:keys [access_token refresh_token]} (->clj body)
                                token-data (jwt-data access_token)
                                markup (views/app (cond-> {:username  (get-in token-data [:payload :username])
                                                           :token     token-data
                                                           :token-row access_token
                                                           :stage     api-stage
                                                           :urls      {:logout (logout-url config)}}
                                                          ; only provide env data when in "dev" stage
                                                          (= "dev" api-stage) (merge {:event   event
                                                                                      :context context
                                                                                      :env     env})))]
                               (validate-token token-data)
                               (callback nil (clj->js {:body       markup
                                                       :statusCode 200
                                                       :headers    {:Content-Type "text/html"}})))))
               (.catch (fn [error]
                           ;(js/console.log error)
                           ;(js/console.log (.-body (.-response error)))
                           (callback error (clj->js {:body       (views/error {})
                                                     :statusCode 500
                                                     :headers    {:Content-Type "text/html"}})))))))
