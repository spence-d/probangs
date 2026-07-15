(ns probangs.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml :refer [element] :rename {element xel}]
            [probangs.parse :as parser]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [hiccup.page :as page]
            [hiccup.form :as form]
            [garden.core :refer [css]]
            [garden.selectors :as selector]))

(def default-config {:autofocus? true
                     :suggestions :never})

(def os-ns "http://a9.com/-/spec/opensearch/1.1/")
(xml/alias-uri 'os os-ns)

(def style
  (css {:pretty-print? false}
       [:body {:background-color "black"}]
       [:input#pro-search-bar {:background-color "#181818"
                               :color "white"
                               :border-radius "7px"
                               :border-color "lightgray"
                               :border-style "solid"
                               :border-width "2px"
                               :width "100%"
                               :font-size "larger"
                               :font-family "monospace"}]
       [:input#pro-search-bar:focus {:outline "none"
                                     :border-color "lawngreen"}]
       [:h1 {:text-align "center"
             :color "lightgray"}]
       [:div#pro-search-area {:background-image "linear-gradient(to bottom, gray, black)"
                              :width "50%"
                              :margin "0 auto"
                              :border-radius "7px"}]
       [:div#pro-search-menu
        [:a {:text-decoration "none"
             :background-color "#181818"
             :margin "2px"
             :border-radius "4px"
             :color "whitesmoke"
             :border "solid gray 1px"
             :display "grid"
             :grid-template-columns "max-content 1fr"
             :font-size "small"
             :padding "0 3px 0 3px"}
         [:span (selector/nth-child 1) {:text-align "left"}]
         [:span (selector/nth-child 2) {:text-align "right"}]]
        [:a:focus {:outline "none"
                   :background-color "green"}]
        [:a:hover {:border-color "green"}]]
       [:#footer {:bottom "0"
                  :left "0"
                  :position "absolute"
                  :font-family "monospace"
                  :width "100%"
                  :text-align "center"
                  :display "grid"
                  :grid-template-columns "1fr max-content 1fr"
                  :grid-template-rows "auto 1fr"}
        [:a {:color "dimgray"
             :text-decoration "none"}]
        [:.pane {:display "grid"
                 :grid-template-columns "1fr 2fr"
                 :column-gap "1em"
                 :color "lightgray"
                 :border "solid dimgray 1px"}
         [:label {:text-align "right"}]
         [:span {:text-align "left"}]
         [:input {:background-color "#181818"
                  :color "lightgray"
                  :text-align "left"}]
         [:button {:text-align "center"}]]
        [:#links {}]
        [:#settings {}]
        [:.hidden {:visibility "hidden"}]
        [:.shown {:visibility "visible"}]]))

(def index
  (page/html5 {}
              [:head
               [:script {:type "text/javascript"}
                (str "const probangs_default_config="
                     (-> default-config pr-str pr-str))]
               (page/include-js "main.js")
               [:meta {:charset "utf-8"}]
               [:title "!Pro Search"]
               [:link {:rel "search"
                       :title "!Pro"
                       :type "application/opensearchdescription+xml"
                       :href "opensearch.xml"}]
               [:link {:rel "icon"
                       :type "image/png"
                       :href "favicon.ico"}]
               [:style style]]
              [:body
               [:h1 "!Pro"]
               [:div#pro-search-area]
               [:footer#footer
                [:div#probangs-links.pane.hidden
                 [:label "Search URL"]
                 [:span
                  [:a {:href "javascript:"
                       :onClick "probangs.frontend.copy_left(this)"} "📋"]]
                 [:label "Suggestions"]
                 [:span
                  [:a {:href "javascript:"
                       :onClick "probangs.frontend.copy_left(this)"} "📋"]]
                 [:label "Use Locally"]
                 [:span]
                 [:label "Bookmarklet"]
                 [:span]]
                [:div.hidden]
                [:div#probangs-settings.pane.hidden
                 (form/label "Autofocus" "autofocus?")
                 (form/drop-down "autofocus?" [["Disabled" "false"]
                                               ["Enabled" "true"]])
                 (form/label "Suggestions" "suggestions")
                 (form/drop-down "suggestions" [["Never" ":never"]
                                                ["On Tab Press" ":tab-only"]
                                                ["Always" ":always"]])
                 (form/label "History Buffer" "history")
                 (form/text-field "history")
                 (form/label "Lucky Search" "lucky")
                 (form/text-field "lucky")
                 (form/label "Default Bangs" "default")
                 (form/text-field "default")
                 [:label]
                 [:button
                  {:onClick "probangs.frontend.handle_settings(this.parentElement);"}
                  "Set"]
                 [:label]
                 [:button
                  {:onClick "probangs.config.clear_config();"}
                  "Clear Config"]
                 [:label]
                 [:button
                  {:onClick "probangs.config.clear_history();"}
                  "Clear History"]]
                [:a {:href "javascript:"
                     :onClick "probangs.frontend.toggle_panel('probangs-links')"
                     :tabindex -1}
                 "🔗"]
                [:a {:href "https://github.com/spence-d/probangs"
                     :tabindex -1}
                 (str "probangs "
                      (System/getProperty "probangs.version"))]
                [:a {:href "javascript:"
                     :onClick "probangs.frontend.toggle_panel('probangs-settings')"
                     :tabindex -1}
                 "⚙"]]]))

(defn make-opensearch [host filename]
  (let [opensearch
        (xel ::os/OpenSearchDescription {:xmlns os-ns
                                         :xmlns:moz "http://www.mozilla.org/2006/browser/search/"}
             (xel ::os/ShortName {} "!Pro")
             (xel ::os/Descripton {} "!Pro Search - Bangs for Pros")
             (xel ::os/InputEncoding {} "UTF-8")
             (xel ::os/OutputEncoding {} "UTF-8")
             (xel ::os/Image {:width "16"
                              :height "16"
                              :type "image/png"}
                  (str host "/favicon.ico"))
             (when-not (= (:suggestions default-config) :never)
               (xel ::os/Url {:type "application/x-suggestions+json"
                              :method "GET"
                              :template (str host "/suggest?q={searchTerms}")}))
				 (xel ::os/Url {:type "text/html"
                            :method "GET"
                            :template (str host "#q={searchTerms}")}))]
  (->> opensearch
       xml/emit-str
       (spit filename))))

(defn make-index [filename]
  (->> index
       str
       (spit filename)))

(defn get-suggestions [term default headers]
  (let [[query bangs bang-names] (parser/parse-search term nil)]
    (if-some [suggest-url (or (some->> bangs (map :suggest) (some identity))
                              (some->> default (str \!) parser/get-bang :suggest))]
      (let [encode (partial parser/encode-replace suggest-url \s)
            conn (-> query
                     encode
                     io/as-url
                     .openConnection)]
        (doto conn
          (.setRequestMethod "GET")
          (.setDoOutput true)
          (.setConnectTimeout 1000)
          (.setReadTimeout 1000)
          (.setRequestProperty "accept" (headers "accept"))
          (.setRequestProperty "user-agent" (headers "user-agent")))
        (if (= (.getResponseCode conn) 200)
          (let [res (with-open [input (.getInputStream conn)]
                      (-> input slurp json/read-str))
                rebang [term (map #(str/join " " (concat bang-names [%]))
                                  (second res))]]
            (json/write-str rebang)))))))

(defroutes app
  (GET "/suggest"
       {{query "q" default "def"} :query-params
        headers :headers}
       (let [sug (get-suggestions query default headers)]
         (pprint sug)
         {:status 200
          :headers {"Content-Type" "application/json"}
          :body sug}))
  (route/files "/" {:root "resources/public"})
  (route/not-found "Oh geez"))

(def handler (-> app handler/api))

(defn -main []
  (if-some [host (System/getenv "PROBANGS_HOST")]
    (if host
      (make-opensearch host "resources/public/opensearch.xml")))
  (make-index "resources/public/index.html"))
