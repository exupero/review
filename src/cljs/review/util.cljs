(ns review.util
  (:require [clojure.string :as string]
            [net.cgrand.xforms :as xf]))

(def emojis
  (into {}
        (map (fn [[k v]]
               [(re-pattern (str ":" (name k) ":"))
                (js/String.fromCodePoint v)]))
        {:100 0x1f4af
         :clap 0x1f44f
         :man_facepalming 0x1f926
         :question 0x2753
         :recycle 0x267b
         :slight_smile 0x1f642
         :sparkles 0x2728
         :thumbsup 0x1f44d
         :white_check_mark 0x2705
         :wink 0x1f609
         :x 0x274c
         }))

(defn emojify [s]
  (reduce
    (fn [s [k v]]
      (string/replace s k v))
    s
    emojis))

(defn javascript? [file]
  (re-find #"\.(js|jsx|ts|tsx)$" file))

(defn java? [file]
  (re-find #"\.java$" file))

(defn context [diff path from to]
  (->> diff
    (some (fn [{:keys [from to chunks]}]
            (when (#{from to} path) chunks)))
    (map (fn [lines]
           (let [line-numbers (into [] (map (juxt :old-line :new-line)) lines)
                 idx (.indexOf line-numbers [from to])]
             (when (<= 0 idx)
               (->> lines
                 (drop (- idx 2))
                 (take 6))))))
    (remove nil?)
    first))

(defn suggested-comments [diff]
  (into {}
        (xf/by-key (comp cat (xf/into [])))
        (for [{:keys [from to chunks]} diff
              lines chunks
              {typ :type :keys [old-line new-line line]} lines
              :let [comments (cond-> []
                                     (and (= "added" typ) (re-find #"\bTODO\b" line) (not (re-find #"(?i)\bCRAB-" line))) (conj "TODO")
                                     (and (= "added" typ) (java? to) (re-find #"\.size\(\)\s+==\s+0\b" line)) (conj "Maybe use `.isEmpty()`?")
                                     (and (= "added" typ) (javascript? to) (re-find #"\.length\s+===?\s+0\b" line)) (conj "Maybe use `_.isEmpty()`?")
                                     (and (= "added" typ) (javascript? to) (re-find #"import \* as " line)) (conj "Don't use `import * as`?")
                                     (and (= "added" typ) (javascript? to) (re-find #"@debug" line)) (conj "Leftover `@debug`")
                                     )]
              :when (seq comments)]
          [[(or from to) old-line new-line]
           (map #(do {:id (random-uuid)
                      :comment %
                      :new? true
                      :inline {:path (or to from) :from old-line :to new-line}
                      :context (context diff (or to from) old-line new-line)})
                comments)])))
