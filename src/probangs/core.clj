(ns probangs.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [probangs.parse :as parser]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [hiccup.page :as page]
            [hiccup.form :as form]
            [garden.core :refer [css]]
            [garden.selectors :as selector]))

(def default-config {:autofocus? true
                     :suggestions :never
                     :default ["brave"]
                     :lucky "duckduckgo"})

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

(defn make-index [filename]
  (->> index
       str
       (spit filename)))

(defn get-suggestions [term default]
  (let [[query bangs bang-names] (parser/parse-search term nil)]
    (if-some [suggest-url (or (some->> bangs (map :suggest) (some identity))
                              (some->> default (str \!) parser/get-bang :suggest))]
      (let [res (->> query
                     (parser/encode-replace suggest-url \s)
                     slurp
                     json/read-str)
            rebang [term (map #(str/join " " (concat bang-names [%]))
                              (second res))]]
        (json/write-str rebang)))))

(defroutes app
  (GET "/suggest"
       {{query "q" default "def"} :query-params}
       (let [sug (get-suggestions query default)]
         (pprint sug)
         {:status 200
          :headers {"Content-Type" "application/json"}
          :body sug}))
  (route/files "/" {:root "resources/public"})
  (route/not-found "Oh geez"))

(def handler (-> app handler/api))

(defn -main []
  (make-index "resources/public/index.html"))
