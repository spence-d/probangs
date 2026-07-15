(ns probangs.parse
  (:require [probangs.list :refer [bangs]]
            [clojure.string :as str]))

(defn- get-bang-alias [key]
  (let [bang (get bangs key)]
    (if-let [alias-name (:alias bang)]
      (get bangs alias-name)
      bang)))

(defn- encode [s]
  #?(:clj (java.net.URLEncoder/encode s "UTF-8")
     :cljs (js/encodeURIComponent s)))

(defn encode-replace [url letter value]
  (let [tag-str (str "{{{" letter "}}}")
        pattern (re-pattern (str "\\?.*"
                                 (str/escape tag-str {\{ "\\{" \} "\\}"})))
        encoded-val (if (re-find pattern url)
                      ;Only encode if we're in the query segment
                      (encode value)
                      ;Paths stay unencoded
                      value)]
    (str/replace-first url tag-str encoded-val)))

(defn get-bang [bang & {:keys [default lucky]
                        :or {default "ddg"
                             lucky "ddg"}}]
  (let [[bang site] (str/split bang #"@" -1)
        site (cond
               ;!r (no site search specified)
               (nil? site) nil
               ;!r@ (search domain r with default engine)
               (empty? site) default
               ;!r@ddg (search domain r with engine ddg)
               :else (get-bang-alias site))]
    (cond
      (empty? bang)
      default

      (= bang "!")
      (if site
        ;!@ddg (search the site on the current domain)
        (assoc site :site nil)
        ;! (I'm Feeling Lucky)
        (let [orig-bang (get-bang-alias lucky)]
          (->> orig-bang
               :lucky
               :url
               (assoc orig-bang :url))))

      (= (first bang) \!)
      ;!r (classical bang)
      (cond-> (->> bang rest (apply str) get-bang-alias)
        ;!r@ddg (search site r with engine ddg)
        site (->> :base (assoc site :site)))

      :else
      (let [[key param] (str/split bang #"!")
            [tag value] (str/split param #"=")
            orig-bang (get-bang-alias key)
            tag-bang (or (get orig-bang (keyword tag))
                         (get-in orig-bang [:flags (keyword tag)])
                         (get orig-bang (:default orig-bang)))
            default-value (if (contains? orig-bang (keyword tag)) value tag)]

        (assoc site :site (:base orig-bang))
        (if default-value
          (let [tag-url (encode-replace (or (:url tag-bang)
                                            (:url orig-bang)) \t default-value)
                tag-base (encode-replace (or (:base tag-bang)
                                             (:base orig-bang)) \t default-value)]
            (if site
              (assoc site :site tag-base)
              (merge orig-bang {:url tag-url
                                :base tag-base})))
          (if site
            (assoc site :site (:base tag-bang))
            (merge orig-bang tag-bang)))))))

(defn parse-search [query lucky]
  (let [words (str/split query #" ")
        bang-keys (filter #(re-find #"^!$|!.+$" %) words)
        bang-list (map #(get-bang % :lucky lucky) bang-keys)
        valid-keys (filter some? (map #(and %1 %2) bang-list bang-keys))
        valid-bangs (filter some? bang-list)
        query (str/trim (reduce #(str/replace-first %1 %2 "")
                                query valid-keys))]
    [query valid-bangs valid-keys]))

(defn search-engine [query bang-list & [current-url default]]
  (map (fn [bang]
         (let [url (:url bang)
               site (cond
                      (contains? bang :site)
                      (or (:site bang)
                          current-url)

                      (nil? url)
                      (:base bang))
               q (if site
                   (str "site:"
                        (-> site (str/replace-first #"^[^:]+://" ""))
                        " " query)
                   query)]

           (if (empty? query)
             (:base bang)
             (encode-replace (or url (-> default get-bang-alias :url)) \s q))))
       bang-list))

(defn process-cmd [cmd current-url & {:keys [default lucky]
                                      :or {default "ddg"
                                           lucky "ddg"}}]
  (let [[search-query bangs] (parse-search cmd lucky)]
    (cond
      ;I'm Feeling Lucky
      (str/starts-with? cmd "\\")
      {:urls (search-engine (->> cmd rest (apply str))
                            [(get-bang (str lucky "!lucky"))])}

      ;Bang search
      (not (empty? bangs))
      {:urls (search-engine search-query bangs current-url default)}

      ;Web search
      (some (partial = \ ) cmd)
      {:urls (search-engine cmd [(get-bang-alias default)])}
      ;TODO split urls on spaces into tabs?

      ;Explicit URL
      (str/includes? cmd ":")
      {:urls [cmd]}

      ;Implicit URL
      (str/includes? cmd ".")
      {:urls [(str "https://" cmd)]}

      ;One-word web search
      :else {:urls (search-engine cmd [(get-bang-alias default)])})))
