(defproject html-ion-pages "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :profiles {:dev {:dependencies [[crucible "0.35.0"]
                                  [org.clojure/data.json "0.2.6"]]
                   :source-paths ["src-pages"]}})
