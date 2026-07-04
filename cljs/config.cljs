(ns probangs.config
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.spec.alpha :as spec]))

;TODO query string overrides

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
                           :history 20
                           :suggestions :tab-only
                           :favorites ["wiki" "reddit"]
                           :lucky "duckduckgo"
                           :default ["brave" "duckduckgo"]}))
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
    
(defn merge-form-config! [div base]
  (let [new-config (->> (.-children div)
                        (remove #(or (empty? (.-name %))
                                     (empty? (.-value %))))
                        (reduce #(merge %1 {(keyword (.-name %2))
                                            (case (.-name %2)
                                              "lucky"
                                              (.-value %2)

                                              "default"
                                              (str/split (.-value %2) #" ")

                                              (edn/read-string (.-value %2)))})
                                @base))]
    
    (reset! base new-config)))

(defn get-storage [k]
  (some->> k
           (.getItem js/localStorage)
           edn/read-string))

(defn merge-storage-config! []
  (let [conf (get-storage "config")]
    (if (spec/valid? ::config conf)
      (reset! config (merge @default-config conf))
      (reset! config @default-config))))

(defn load-config! []
  (some->> js/probangs_default_config
           edn/read-string
           (swap! default-config merge))
  (merge-storage-config!)
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
