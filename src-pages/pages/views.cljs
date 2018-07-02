(ns pages.views
  (:require
    [cljs.pprint :refer [pprint]]
    [hiccups.runtime :as hiccupsrt]
    [garden.core :refer [css]])
  (:require-macros [hiccups.core :as hiccups :refer [html]]))

(defn home
      "return html for a page where a user can start login"
      [{:keys [urls]}]
      (let [{:keys [login]} urls
            styles (css [:body {}
                         [:h1 {:width  "300px"
                               :margin "0 auto"}]
                         [:.login {:width   "200px"
                                   :margin  "0 auto"
                                   :padding "20px"}]
                         [:a {:width       "100px"
                              :font-size   "1.2rem"
                              :font-weight "bold"}]])]
           (html
             [:div
              [:style {} styles]
              [:h1 {} "Website Home Page"]
              [:div {:class "login"}
               [:a {:href login} "Log In"]]])))

(defn app
      "return html for a page where a user is logged in and can logout"
      [{:keys [stage event context env username token token-raw urls]}]
      (let [{:keys [logout]} urls
            styles (css [:body {:font-size "16px"}
                         [:pre {:white-space "pre-wrap"
                                :width       "400px"}]
                         [:.header {:background-color "lightgrey"
                                    :font-size        "1.1rem"
                                    :padding          "5px"}]
                         [:.clearfix {:overflow "auto"}]
                         [:.clearfix:after {:content ""
                                            :clear   "both"}]
                         [:.logo {:width "10rem"
                                  :float "left"}]
                         [:.user {:width "5rem"
                                  :float "right"}]
                         [:.logout {:width "5rem"
                                    :float "right"}]
                         [:.dev-tools {:margin-top       "200px"
                                       :background-color "lightgrey"
                                       :padding          "10px"}]])]
           (html [:div {}

                  [:style {} styles]

                  [:script {:type "javascript"}
                   (str "var jwt = '" token-raw "';")]

                  [:div {:class "header clearfix"}
                   [:div {:class "logo"} "Application Home"]

                   [:a {:class "logout" :href logout} "Log Out"]
                   [:div {:class "user"} username]]

                  [:div {:id "app"}
                   [:h3 {} "Loading.."]]

                  (when (= "dev" stage)
                        (let [event (js->clj event :keywordize-keys true)
                              context (js->clj context :keywordize-keys true)]
                             [:div {:class "dev-tools"} "Development Tools"
                              [:h4 {} "JWT Token Data"]
                              [:pre {} (str token)]

                              [:h4 {} "Request Event"]
                              [:pre {} (str event)]

                              [:h4 {} "Request Env"]
                              [:pre {} (str env)]

                              ]))])))

(defn error
      [{:keys []}]
      (html [:div {} "Failed"]))
