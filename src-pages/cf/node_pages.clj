(ns ^{:doc "Generation of AWS Cloudformation templates which provide html pages running in Node.js Lambdas."}
cf.node-pages
  (:require
    [crucible.core :refer [template parameter resource output xref encode join sub region account-id]]
    [crucible.aws.s3 :as s3]
    [crucible.aws.iam :as iam]
    [crucible.aws.lambda :as lambda]
    [crucible.aws.api-gateway :as api-gw]
    [crucible.policies :as policies]
    [clojure.java.io :as io]
    [clojure.data.json :as json]))

(def zip-file-path "pages/handlers.zip")

(defn add-page
  "adds all the resources for an html page to be included in the stack"
  [stack code-in-bucket? {:keys [page-name js-handler path]}]

  (let [lambda-env {::lambda/variables {:cognito_pool          (xref :CognitoPoolId)
                                        :cognito_domain        (xref :CognitoDomain)
                                        :cognito_client_id     (xref :CognitoClientId)
                                        :cognito_client_secret (xref :CognitoClientSecret)}}
        fake-code {::lambda/zip-file "foo/bar.zip"}         ; used to create lambdas without real code loaded
        zip-file (if code-in-bucket?
                   {::lambda/s3-bucket (xref :code-bucket)
                    ::lambda/s3-key    zip-file-path}
                   fake-code)
        lambda-resource-key (keyword (str "lambda-page-" page-name))
        api-resource-key (keyword (str "api-" page-name))]
    (-> stack
        (assoc lambda-resource-key
               (lambda/function {::lambda/function-name (join [(xref :Application) "-page-" page-name])
                                 ::lambda/code          zip-file
                                 ::lambda/handler       (str "handlers." js-handler)
                                 ::lambda/role          (xref :lamdba-role :arn)
                                 ::lambda/runtime       "nodejs6.10"
                                 ::lambda/environment   lambda-env}))


        (assoc (keyword (str "permission-" page-name))
               (lambda/permission {::lambda/action        "lambda:invokeFunction"
                                   ::lambda/function-name (xref lambda-resource-key :arn)
                                   ::lambda/principal     "apigateway.amazonaws.com"
                                   ::lambda/source-arn    (join ["arn:aws:execute-api:"
                                                                 region
                                                                 ":"
                                                                 account-id
                                                                 ":"
                                                                 (xref :api)
                                                                 "/*"])}))
        (assoc api-resource-key (api-gw/resource {:rest-api-id (xref :api)
                                                  :parent-id   (xref :api :root-resource-id)
                                                  :path-part   path}))

        (assoc (keyword (str "api-get-" page-name))
               (api-gw/method {:http-method        "GET"
                               :resource-id        (xref api-resource-key)
                               :rest-api-id        (xref :api)
                               :authorization-type "NONE"
                               :integration        {:type                    "AWS_PROXY"
                                                    :integration-http-method "POST" ; Important! Lambda must be invoked using a POST
                                                    :uri                     (join ["arn:aws:apigateway:"
                                                                                    region
                                                                                    ":lambda:path/2015-03-31/functions/"
                                                                                    (xref lambda-resource-key :arn)
                                                                                    "/invocations"])}})))))

(defn stack-json
  "return a CF template. code-loaded? indicates if the node.js code has been uploaded to the s3 bucket."
  [code-loaded?]
  (-> {:Application         (parameter)
       :CognitoPoolId       (parameter)
       :CognitoDomain       (parameter)
       :CognitoClientId     (parameter)
       :CognitoClientSecret (parameter)}

      (assoc :code-bucket (s3/bucket {::s3/bucket-name (join [(xref :Application) "-nodejs-pages"])}))

      (assoc :lamdba-role (iam/lambda-access-role))

      (add-page code-loaded? {:page-name  "home"
                              :js-handler "home"
                              :path       "home"})

      (add-page code-loaded? {:page-name  "app"
                              :js-handler "app"
                              :path       "app"})

      ; using join instead of sub because https://github.com/brabster/crucible/issues/107 once fixed, then use (sub "${Application}-home-page")
      (assoc :api (api-gw/rest-api {::api-gw/name        (join [(xref :Application) "-html-pages"])
                                    ::api-gw/description "routes to invoke lamdbas that generate html pages"}))

      (assoc :api-deployment (-> (api-gw/deployment {:rest-api-id (xref :api)})
                                 ; deployment must wait for at least one method in the api. use a depends-on to ensure this
                                 (assoc-in [1 :depends-on] [:api-get-home])))
      (assoc :api-deployment-stage-dev (api-gw/stage {:rest-api-id   (xref :api)
                                                      :deployment-id (xref :api-deployment)
                                                      :stage-name    "dev"}))

      (template "Lambdas and API Gateway resources to serve html pages (including login and logout flows)")))

; defined as a vars so that the Crucible lein task can be used to generate
(def create-stack (stack-json false))
(def update-stack (stack-json true))

(comment
  (let [write (fn [template file]
                (let [_ (io/make-parents file)
                      f (io/file file)]
                  (with-open [w (io/writer f)]
                    (json/write (json/read-str (encode template)) w))))]
    (write create-stack "target/templates/cf/node-pages/create-stack.json")
    (write update-stack "target/templates/cf/node-pages/update-stack.json")))