(ns probangs.frontend
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [probangs.parse :as parser]
            [probangs.list]
            [probangs.config :as config]))

(def suggestions (atom []))

(defn process-search [query]
  (let [ret (parser/process-cmd query
                                (some-> @config/referrer js/URL. .-hostname)
                                :default @config/default-engine
                                :lucky (:lucky @config/config))]
    (when ret
      (if-not (str/starts-with? query " ")
        (config/add-history query))
      ret)))

(defn load-urls [urls & {:keys [bypass-history?]
                         :or {:bypass-history? true}}]
  (run! #(.open js/window % "_blank") (rest urls))
  (if bypass-history?
    (-> js/window .-location (.replace (first urls)))
    (-> js/window .-location (set! (first urls)))))

;Pre-load entry point
(let [params (-> js/window
                  .-location
                  .-hash
                  (str/replace-first #"#" "?")
                  js/URLSearchParams.)]
  (config/load-config! js/probangs_default_config params)

  (reset! config/referrer (or (.get params "from")
                              (not-empty (.-referrer js/document))))

  (if-some [q (.get params "q")]
    (if-not (= (.get params "go") "false")
      (let [urls (-> q process-search :urls)]
        (if (= (count urls) 1)
          (load-urls urls))))))

(defn clear-suggestions []
  (when-not (empty? @suggestions)
    (reset! suggestions [])
    (-> (.getElementById js/document "pro-search-menu")
        .-innerText
        (set! ""))))

(defn handle-suggestion-click [event]
  (let [suggestion (-> event .-target .-title)
        searchbar (.getElementById js/document "pro-search-bar")]
    (doto searchbar
      (-> .-value (set! suggestion))
      .focus))
  (clear-suggestions))

(defn create-suggest-option
  ([suggestion]
   (create-suggest-option suggestion suggestion nil))
  ([suggestion replacement description]
   (let [sugg-el (doto (.createElement js/document "span")
                   (-> .-title (set! replacement))
                   (-> .-innerText (set! suggestion)))
         desc-el (doto (.createElement js/document "span")
                   (-> .-title (set! replacement))
                   (-> .-innerText (set! description)))]
   (doto (.createElement js/document "a")
     (-> .-href (set! "javascript:"))
     (.addEventListener "click" handle-suggestion-click)
     (-> .-title (set! replacement))
     (.appendChild sugg-el)
     (.appendChild desc-el)))))

(defn ^:async get-suggestions [query]
  (let [encoded (js/encodeURIComponent query)
        response (await (.fetch js/window (str "/suggest?q=" encoded
                                               "&def=" @config/default-engine)))
        json (await (.json response))]
    (when json
      (let [suggestions (reset! suggestions (second json))
            menu (.getElementById js/document "pro-search-menu")]
        (run! #(->> %
                    create-suggest-option
                    (.appendChild menu)) suggestions)))))

;TODO suggest other segments: r![sub] and !r@[ddg]
(defn update-suggestions [event]
  (clear-suggestions)
  (let [term (-> event .-target .-value)
        menu (.getElementById js/document "pro-search-menu")]
    (if-some [[_ last-bang] (re-find #"\B!(\S+)$" term)]
      (let [search-desc? (some? (re-find #"[A-Z]" last-bang))
            last-bang (str/lower-case last-bang)]
        (->> probangs.list/bangs
             (filter #(if search-desc?
                        (some-> %
                                second
                                :name
                                str/lower-case
                                (str/starts-with? last-bang))
                        (str/starts-with? (first %) last-bang)))
             (map first)
             sort
             (take 20)
             (map (partial str \!))
             (reset! suggestions)
             (run! #(->> (create-suggest-option
                           %
                           (str/replace term #"\S+$" %)
                           (or (get-in probangs.list/bangs
                                       [(apply str (rest %)) :name])
                               (->> (get-in probangs.list/bangs
                                            [(apply str (rest %)) :alias])
                                    (get probangs.list/bangs)
                                    :name)))
                         (.appendChild menu)))))
      (if (= (:suggestions @config/config) :always)
        (get-suggestions term)))))

(defn handle-key [event]
  (let [search (.getElementById js/document "pro-search-bar")]
    (case (.-key event)
      "Enter"
      (do
        (.preventDefault event)
        (when-some [{:keys [urls]} (process-search (.-value search))]
          (-> search .-value (set! ""))
          (load-urls urls :bypass-history? false)))

      "Tab"
      (when (empty? @suggestions)
        (.preventDefault event)
        (if (not= (:suggestions @config/config) :never)
          (-> search .-value get-suggestions)))

      "Escape"
      (do
        (.blur search))

      ("ArrowUp" "ArrowDown")
      (do
        (if (str/ends-with? (.-key event) "Up")
          (-> search .-value (set! (config/cycle-history 1)))
          (-> search .-value (set! (config/cycle-history -1)))))

      nil)))

(defn ^:export toggle-panel [pname]
  (let [panel (.getElementById js/document pname)]
    (if (= (.-className panel) "pane hidden")
      (-> panel .-className (set! "pane shown"))
      (-> panel .-className (set! "pane hidden")))))

(defn ^:export copy-left [elem]
  (.. js/navigator
      -clipboard
      (writeText (some-> elem
                         .-previousElementSibling
                         .-value))))

(def this-url (-> js/window .-location (str/split #"\?") first))
(def this-path (re-find #".*/" this-url))

(defn populate-settings []
  (if-some [settings (.getElementById js/document "probangs-settings")]
    (let [conf @config/config
          [history lucky default]
          (seq (.getElementsByTagName settings "input"))
          [autofocus suggestions]
          (seq (.getElementsByTagName settings "select"))
          [_submit clear-config clear-history]
          (seq (.getElementsByTagName settings "button"))]
      (-> autofocus .-value (set! (:autofocus? conf)))
      (-> suggestions .-value (set! (:suggestions conf)))
      (-> history .-value (set! (:history conf)))
      (-> lucky .-value (set! (:lucky conf)))
      (-> default .-value (set! (->> conf
                                     :default
                                     (str/join " "))))
      (-> clear-config .-disabled (set! (nil? (js/localStorage.getItem "config"))))
      (-> clear-history .-disabled (set! (nil? (js/localStorage.getItem "history")))))))

(defn ^:export handle-settings [div]
  (config/handle-settings div)
  (populate-settings))

(defn handle-load [_]
  (let [searchbar (doto (.createElement js/document "input")
                    (-> .-id (set! "pro-search-bar"))
                    (-> .-type (set! "search")))
        menu (doto (.createElement js/document "div")
               (-> .-id (set! "pro-search-menu")))
        searcharea (.getElementById js/document "pro-search-area")
        params (-> js/window
                   .-location
                   .-hash
                   (str/replace-first #"#" "?")
                   js/URLSearchParams.)]

    (doto searcharea
      (.appendChild searchbar)
      (.appendChild menu))

    (if-some [q (.get params "q")]
      (-> searchbar .-value (set! q)))

    (if-some [links (.getElementById js/document "probangs-links")]
      (let [[url suggestion downloads bookmarklet]
            (seq (.getElementsByTagName links "span"))]
        (.prepend url (doto (.createElement js/document "input")
                        (-> .-type (set! "text"))
                        (.setAttribute "readonly" "")
                        (-> .-value (set! (str this-url "#q=%s")))))
        (if (or (= (:suggestions @config/config) :never)
                (= (.. js/window -location -protocol) "file:"))
          (doto suggestion
            (.. -previousElementSibling remove)
            .remove)
          (.prepend suggestion (doto (.createElement js/document "input")
                                 (-> .-type (set! "text"))
                                 (.setAttribute "readonly" "")
                                 (-> .-value (set! (str this-path "suggest?q=%s"))))))
        (doto downloads
          (.appendChild (doto (.createElement js/document "a")
                          (-> .-href (set! this-url))
                          (.setAttribute "download" "")
                          (-> .-innerText (set! "[html]"))))
          (.appendChild (doto (.createElement js/document "a")
                          (-> .-href (set! (str this-path "main.js")))
                          (.setAttribute "download" "")
                          (-> .-innerText (set! "[js]")))))
        (if (not= (.. js/window -location -protocol) "file:")
          (.appendChild bookmarklet (doto (.createElement js/document "a")
                                      (-> .-href (set! (str "javascript:((s)=>{var t='',go='&go=false';"
                                                            "if(s.trim()&&s!=='%'+'s'){t=' '+s;go=''}"
                                                            "window.open('"
                                                            this-url
                                                            "#q='+encodeURIComponent(window.getSelection().toString()+t)+go+'"
                                                            "&from='+encodeURIComponent(window.location),'_blank');})('%s')")))
                                      (-> .-innerText (set! "!Pro")))))))

    (populate-settings)

    (.addEventListener searchbar "keydown" handle-key true)
    (.addEventListener searchbar "input" update-suggestions)
    (if (:autofocus? @config/config) (.focus searchbar))))

(.addEventListener js/window "load" handle-load)
