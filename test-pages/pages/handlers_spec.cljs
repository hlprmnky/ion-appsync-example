(ns pages.handlers-spec
  (:require
    [cljs.pprint :refer [pprint]]
    [cljs.test :refer [deftest is async testing]]
    [pages.handlers :as handlers]
    [pages.views :as views]
    [cljs-node-io.core :as io :refer [slurp spit]]))

(defn set-env!
      "set required env params for the handlers"
      []
      (set! (.-cognito_domain (.-env js/process)) "appsyncgql")
      (set! (.-cognito_pool (.-env js/process)) "us-west-2_ix30soqfX")
      (set! (.-cognito_client_id (.-env js/process)) "62g1e7kgkbex6icfrkvv341q2g")
      (set! (.-cognito_client_secret (.-env js/process)) "h1toni0lc8duvrfe")
      (set! (.-AWS_REGION (.-env js/process)) "us-west-2"))

(deftest home
         (async done
                (set-env!)
                (handlers/home-page {} {}
                                    (fn [error result]
                                        ;(pprint result)
                                        (is (nil? error) "home page succeeds because no callouts are made")
                                        (done)))))

; TODO use nock to avoid the callout and error https://github.com/nock/nock
(deftest app-context
         (async done
                (set-env!)
                (handlers/app-page {} {}
                                   (fn [error result]
                                       (is (not (nil? error)) "app page fails because token code is invalid")
                                       (done)))))

(deftest ssl-request
         (async done
                (-> (handlers/requestp {:method         "GET"
                                        :uri            "https://www.google.com"
                                        :followRedirect false})
                    (.then (fn [body]
                               (is (> (count body) 100) "html from google page is big")
                               (done)))
                    (.catch (fn [error]
                                (js/console.log error)
                                (is false "error in ssl call")
                                (done))))))

(deftest views-static
         (io/make-parents "out/views/foo")
         (spit "out/views/home.html" (views/home {:urls {:login "/login"}}))
         (spit "out/views/app.html" (views/app {:username  "Chris"
                                                :token     {:some "token data"}
                                                :token-raw "fff.xxxx.fff"
                                                :event     {}
                                                :urls      {:logout "/logout"}})))