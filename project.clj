(defproject name.trofimov/webapp-clojure-2020 "1.0.0-SNAPSHOT"
  :description "Multi-page web application prototype with Clojure(Script)"
  :dependencies [;;; Clojure
                 [org.clojure/clojure "1.11.1"]

                 ;;; ClojureScript (shadow-cljs)
                 [com.google.guava/guava "31.1-jre" :scope "provided"]
                 [thheller/shadow-cljs "2.22.8" :scope "provided"]

                 ;;; System
                 [integrant "0.8.0"]
                 [mount "0.1.17"]
                 [tolitius/mount-up "0.1.3"]

                 ;;; Web Server
                 [com.github.strojure/ring-control "1.0.59"]
                 [com.github.strojure/ring-lib "1.2.3-62"]
                 [com.github.strojure/ring-undertow "1.1.1-105"]
                 [metosin/reitit-core "0.6.0"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-defaults "0.3.4"]

                 ;;; Database
                 [com.h2database/h2 "2.1.214"]
                 [com.layerware/hugsql "0.5.3" :exclusions [com.layerware/hugsql-adapter-clojure-java-jdbc]]
                 [com.layerware/hugsql-adapter-next-jdbc "0.5.3"]
                 [com.mattbertolini/liquibase-slf4j "5.0.0"]
                 [com.zaxxer/HikariCP "5.0.1" :exclusions [org.slf4j/slf4j-api]]
                 [org.liquibase/liquibase-core "4.20.0"]
                 [seancorfield/next.jdbc "1.2.659"]
                 [p6spy/p6spy "3.9.1"]

                 ;;; Logging
                 [ch.qos.logback/logback-classic "1.4.6"]
                 [ch.qos.logback/logback-core "1.4.6"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.codehaus.janino/janino "3.1.9"]
                 [org.slf4j/jul-to-slf4j "2.0.7"]
                 [org.slf4j/slf4j-api "2.0.7"]

                 ;;; Libs (Java)
                 [com.fasterxml.jackson.core/jackson-core "2.14.2"]
                 [commons-codec/commons-codec "1.15"]
                 [org.apache.commons/commons-lang3 "3.12.0"]

                 ;;; Libs (Clojure)
                 [clojurewerkz/propertied "1.3.0"]
                 [com.cognitect/transit-clj "1.0.329"]
                 [medley "1.4.0"]
                 [potemkin "0.4.6"]

                 ;;; Libs (ClojureScript)
                 [cljsjs/react "18.2.0-1"]
                 [cljsjs/react-dom "18.2.0-1"]
                 [rum "0.12.10"]

                 ;;; Daemon
                 [commons-daemon/commons-daemon "1.3.3"]]

  :main ^:skip-aot app.main
  :test-paths ["test" "src"]
  :target-path "target/%s"
  :plugins [[lein-shell "0.5.0"]]

  :clean-targets ^{:protect false} ["target"
                                    "resources/public/app"]

  :repl-options {:init-ns dev.env.main}

  :shell {:commands
          {"node_modules/.bin/postcss"
           {:windows "node_modules/.bin/postcss.cmd"}}}

  :aliases {"shadow-cljs" ["run" "-m" "shadow.cljs.devtools.cli"]

            "css-example-release" ["shell"
                                   "node_modules/.bin/postcss"
                                   "tailwind/app/\\$_example/main.css"
                                   "-o" "resources/public/app/example/main.css"
                                   "--config" "tailwind/app/config/"]}

  :profiles {:dev {:jvm-opts ["-Dconfig.file=dev/app/config/default.props"]
                   :main ^:skip-aot dev.env.main
                   :dependencies [[me.raynes/fs "1.4.6"]
                                  [nrepl "1.0.0"]
                                  [ns-tracker "0.4.0"]
                                  [ring-refresh "0.1.3"]
                                  [ring/ring-devel "1.9.6"]
                                  [zcaudate/hara.io.watch "2.8.7"]]
                   :source-paths ["dev" "tailwind"]}

             :test-release [:uberjar
                            {:jvm-opts ["-Dconfig.file=dev/app/config/default.props"]}]

             :uberjar {:aot :all
                       :prep-tasks ["compile"
                                    ["shadow-cljs" "release" "example"]
                                    "css-example-release"]
                       :injections [(do (println "Disable clojure.test/*load-tests*")
                                        (require 'clojure.test)
                                        (alter-var-root #'clojure.test/*load-tests* (constantly false)))]}}

  :uberjar-name "website.jar")
