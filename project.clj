(defproject probangs "0.1.0"
  :description "An advanced implementation of search engine bangs"
  :url "https://github.com/spence-d/probangs"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [org.clojure/clojurescript "1.12.145"]
                 [org.clojure/data.json "2.5.2"]
                 [org.clojure/data.xml "0.2.0-alpha11"]
                 [ring/ring-core "1.9.2"]
                 [ring/ring-jetty-adapter "1.9.2"]
                 [compojure "1.7.2"]
                 [hiccup "2.0.0"]
                 [garden "1.3.10"]]
  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-ring "0.12.6"]]
  :cljsbuild {:builds [{:source-paths ["cljs"]
                        :compiler {:output-to "resources/public/main.js"
                                   :optimizations :advanced
                                   :pretty-print false}}]}
  :ring {:handler probangs.core/handler}
  :main probangs.core
  :repl-options {:init-ns probangs.core})
