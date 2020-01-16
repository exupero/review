(ns review.blame
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as string]))

(defn shell [& args]
  (let [{:keys [out err exit]} (apply sh args)]
    (when-not (zero? exit) (throw (Exception. err)))
    out))

(defn blame [file start-line end-line]
  (-> (shell "git" "blame" "--line-porcelain" "-L" (str start-line "," end-line) file :dir "/Users/exupero/seeq/crab")
    string/split-lines
    (->> (into #{}
               (comp
                 (map (partial re-find #"^author (.*)"))
                 (remove nil?)
                 (map second))))))

(defn stewards [diff]
  (remove nil?
          (for [{:keys [from chunks]} diff
                :when from
                chnk chunks
                {typ :type :keys [old-line new-line]} chnk
                :when (#{#_:added :removed} typ)
                :let [line-number (or old-line new-line)]]
            (try
              [[from line-number] (blame from line-number line-number)]
              (catch Exception e
                (prn e)
                nil)))))
