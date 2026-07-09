(ns probangs.config
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.spec.alpha :as spec]))

(spec/def ::autofocus? boolean?)
(spec/def ::history integer?)
(spec/def ::suggestions #{:tab-only :always :never})
(spec/def ::favorites (spec/coll-of string?))
(spec/def ::lucky string?)
(spec/def ::default (spec/coll-of string?))
(spec/def ::config (spec/keys :opt-un [::autofocus?
                                       ::history
                                       ::suggestions
                                       ::favorites
                                       ::lucky
                                       ::default]))

(def default-config (atom {:autofocus? true
                           :history 0
                           :suggestions :tab-only
                           ;:favorites ["wiki" "reddit"]
                           :lucky "duckduckgo"
                           :default ["duckduckgo"]}))
(def config (atom @default-config))
(def search-history (atom []))
(def search-history-idx (atom 0))
(def referrer (atom nil))
(def default-engine (atom (-> @config :default rand-nth)))

(defn save-storage! [k v]
  (.setItem js/localStorage k (pr-str v)))

(defn cycle-history [offset]
  (when (<= 0 (+ @search-history-idx offset) (count @search-history))
    (let [idx (swap! search-history-idx + offset)]
      (if (zero? idx)
        ""
        (nth @search-history (- (count @search-history) idx))))))

(defn add-history [query]
  (when (pos? (:history @config))
    (swap! search-history conj query)
    (->> @search-history
         (take-last (:history @config))
         vec
         (save-storage! "history"))))

(defn form-pair [k v]
  {(keyword k)
   (case k
     "lucky"
     v

     "default"
     (str/split v #" ")

     (edn/read-string v))})

(defn merge-query-config [query orig]
  (let [ks (-> orig keys set)
        conf (->> query
                  (filter (comp ks keyword first))
                  (reduce #(merge %1 (form-pair (first %2) (second %2)))
                          {}))]
    (if (spec/valid? ::config conf)
      (merge orig conf)
      (do
        (js/console.info (str "Could not merge query config: " (pr-str conf)))
        orig))))

(defn merge-form-config! [div base]
  (->> (.-children div)
       (remove #(or (empty? (.-name %))
                    (empty? (.-value %))))
       (reduce #(merge %1 (form-pair (.-name %2) (.-value %2)))
               @base)
       (reset! base)))

(defn get-storage [k]
  (some->> k
           (.getItem js/localStorage)
           edn/read-string))

(defn merge-storage-config [orig]
  (let [conf (get-storage "config")]
    (if (spec/valid? ::config conf)
      (merge orig conf)
      (do
        (js/console.info (str "Could not merge stored config: " (pr-str conf)))
        orig))))

(defn merge-page-defaults [page-defaults orig]
  (let [conf (some->> page-defaults
                      edn/read-string
                      (merge orig))]
    (if (spec/valid? ::config conf)
      (merge orig conf)
      (do
        (js/console.info (str "Could not merge page defaults: " (pr-str conf)))
        orig))))

(defn load-config! [page-defaults query]
  (->> @default-config
       (merge-page-defaults page-defaults)
       (merge-storage-config)
       (merge-query-config query)
       (reset! config))

  (reset! search-history (get-storage "history"))
  (reset! default-engine (-> @config :default rand-nth)))

(defn handle-settings [div]
  (merge-form-config! div config)
  (reset! default-engine (-> @config :default rand-nth))
  (save-storage! "config" @config))

(defn ^:export clear-config []
  (reset! config @default-config)
  (reset! default-engine (-> @config :default rand-nth))
  (.removeItem js/localStorage "config"))

(defn ^:export clear-history []
  (reset! search-history [])
  (reset! search-history-idx 0)
  (.removeItem js/localStorage "history"))
