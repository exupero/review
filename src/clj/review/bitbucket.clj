(ns review.bitbucket
  (:require [clojure.string :as string]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [net.cgrand.xforms :as xf]
            [review.blame :as blame]
            [review.xf :refer [split-by]]))

(def creds [(System/getenv "BITBUCKET_USERNAME")
            (->> (System/getenv "BITBUCKET_PASSWORD_BASE64_ENCODED")
              (.decode (java.util.Base64/getDecoder))
              String.
              string/trim)])

(defn add-auth [req]
  (assoc req :basic-auth creds))

(defn request [req]
  (-> req add-auth http/request))

(defn add-line-numbers [old-start-line new-start-line]
  (fn [rf]
    (let [old-line-number (volatile! (cond-> old-start-line (pos? old-start-line) dec))
          new-line-number (volatile! (cond-> new-start-line (pos? new-start-line) dec))]
      (fn
        ([] (rf))
        ([res] (rf res))
        ([res item]
         (let [[o n] (condp = (item :type)
                       :unchanged [1 1]
                       :removed [1 0]
                       :added [0 1]
                       :comment [0 0])]
           (rf res (assoc item
                          :old-line (cond-> (vswap! old-line-number + o)
                                            (zero? o) (as-> $ nil))
                          :new-line (cond-> (vswap! new-line-number + n)
                                            (zero? n) (as-> $ nil))))))))))

(defn parse-diff-chunk [[header & lines]]
  (let [[s & args] (re-find #"@@ -(\d+),(\d+) \+(\d+),(\d+) @@" header)
        [old-start old-size new-start new-size] (map #(Integer/parseInt %) args)]
    (sequence
      (comp
        (map (fn [line]
               (let [[c] line
                     line (subs line 1)]
                 (condp = c
                   \space {:type :unchanged :line line}
                   \- {:type :removed :line line}
                   \+ {:type :added :line line}
                   \\ {:type :comment :line line}))))
        (add-line-numbers (or old-start 0) (or new-start 0)))
      lines)))

(defn parse-diff [diff]
  (sequence
    (comp
      (split-by (partial re-find #"^diff --git"))
      (map (fn [lines]
             (let [[header & chunks] (sequence (split-by (partial re-find #"^@@")) lines)]
               {:from (some->> header (some (partial re-find #"^--- a/(.*)$")) second)
                :to (some->> header (some (partial re-find #"^\+\+\+ b/(.*)$")) second)
                :chunks (map parse-diff-chunk chunks)}))))
    (string/split-lines diff)))

(defn as-get [url]
  {:url url :method :get})

(defn all-comments [p]
  (loop [{nxt :next :keys [values]} (-> p :links :comments :href as-get request :body (json/parse-string true))
         comments []]
    (let [comments (into comments values)]
      (if nxt
        (recur (-> nxt as-get request :body (json/parse-string true)) comments)
        comments))))

(defn pull-request [id]
  (let [p (-> {:url (str "https://api.bitbucket.org/2.0/repositories/seeq12/crab/pullrequests/" id) :method :get}
            request :body (json/parse-string true))
        stats (future (-> p :links :diffstat :href as-get request :body (json/parse-string true) :values))
        diff (future (-> p :links :diff :href as-get request :body parse-diff))
        comments (future (all-comments p))
        ; stewards (future (blame/stewards @diff))
        ]
    (assoc p
           :stats @stats
           :diff @diff
           :comments @comments
           ; :stewards @stewards
           )))

(defn comment! [id s {:keys [path from to parent-id]}]
  (request
    {:url (str "https://api.bitbucket.org/2.0/repositories/seeq12/crab/pullrequests/" id "/comments")
     :method :post
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string
             (cond-> {:content {:raw s}}
                     path (assoc :inline (cond-> {:path path}
                                                 from (assoc :from from)
                                                 to (assoc :to to)))
                     parent-id (assoc :parent {:id parent-id})))}))

(defn approve! [id]
  (request
    {:url (str "https://api.bitbucket.org/2.0/repositories/seeq12/crab/pullrequests/" id "/approve")
     :method :post}))
