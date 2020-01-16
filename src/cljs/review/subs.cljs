(ns review.subs
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [net.cgrand.xforms :as xf]
            [re-frame.core :as rf]
            [cljs-time.format :as tf]
            [goog.date.DateTime :as DateTime]
            ["marked" :as marked]
            ["diff" :as diff]
            [review.util :as util]))

(rf/reg-sub ::pull-request
  (fn [db _]
    (db :pull-request)))

(rf/reg-sub ::comments
  (fn [db _]
    (db :comments)))

(rf/reg-sub ::title
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{:keys [title]}]
    (some-> title
            (string/replace #"CRAB-(\d+)" (fn [[s id]]
                                            (str "<a href=\"https://seeq.atlassian.net/browse/CRAB-" id "\">" s "</a>"))))))

(rf/reg-sub ::created-on
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{:keys [created_on]}]
    (tf/unparse (tf/formatter "MMM d, Y")
                (DateTime/fromRfc822String created_on))))

(rf/reg-sub ::updated-on
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{:keys [updated_on]}]
    (tf/unparse (tf/formatter "MMM d, Y")
                (DateTime/fromRfc822String updated_on))))

(rf/reg-sub ::reviewers
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{:keys [participants]}]
    (sequence
      (comp
        (filter (comp #{"REVIEWER"} :role))
        (map (fn [{{:keys [display_name]} :user :keys [approved]}]
               {:name display_name
                :approved? approved}))
        (xf/sort-by (comp #(if % 0 1) :approved?)))
      participants)))

(rf/reg-sub ::stats
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{:keys [stats]}]
    stats))

(rf/reg-sub ::diff
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{:keys [diff]}]
    diff))

(rf/reg-sub ::total-lines-removed
  (fn []
    (rf/subscribe [::stats]))
  (fn [stats]
    (transduce (map :lines_removed) + stats)))

(rf/reg-sub ::total-lines-added
  (fn []
    (rf/subscribe [::stats]))
  (fn [stats]
    (transduce (map :lines_added) + stats)))

(rf/reg-sub ::file-count
  (fn []
    (rf/subscribe [::stats]))
  (fn [stats]
    (count stats)))

(rf/reg-sub ::accounts
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{:keys [participants]}]
    (into {} (map (juxt (comp :account_id :user) :user)) participants)))

(defn inline-accounts [s accounts]
  (string/replace s #"@\{([A-Fa-f0-9:-]+)\}"
                  (fn [[_ id]]
                    (let [nm (-> accounts (get id) :display_name (or id))]
                      (str "<span class=\"badge\">" nm "</span>")))))

(rf/reg-sub ::description
  (fn []
    [(rf/subscribe [::pull-request])
     (rf/subscribe [::accounts])])
  (fn [[{:keys [description]} accounts]]
    (some-> description marked util/emojify (inline-accounts accounts))))

(defn diff-line-pair [removed added]
  (let [char-segments (diff/diffChars removed added)
        word-segments (diff/diffWords removed added)
        segments (js->clj (min-key count word-segments char-segments) :keywordize-keys true)]
    [(sequence
        (comp
          (remove :added)
          (map #(dissoc % :count :added)))
        segments)
     (sequence
       (comp
         (remove :removed)
         (map #(dissoc % :count :removed)))
       segments)]))

(rf/reg-sub ::mutations
  (fn []
    (rf/subscribe [::diff]))
  (fn [diff]
    (into {}
          (for [{:keys [from to chunks]} diff
                [i chnk] (map-indexed vector chunks)
                m (sequence
                    (comp
                      (partition-by (comp #{"unchanged"} :type))
                      (filter (fn [lines]
                                (and (-> lines first :type (not= "unchanged"))
                                     (let [{:strs [removed added]} (group-by :type lines)]
                                       (= (count removed) (count added))))))
                      (map (fn [lines]
                             (let [{:strs [removed added]} (group-by :type lines)]
                               (apply merge
                                      (map (fn [{removed :line :keys [old-line]} {added :line :keys [new-line]}]
                                             (let [[removed-segments added-segments] (diff-line-pair removed added)]
                                               {[from i old-line removed] removed-segments
                                                [to i new-line added] added-segments}))
                                           removed added))))))
                    chnk)]
            m))))

(rf/reg-sub ::new-comments
  (fn []
    [(rf/subscribe [::diff])
     (rf/subscribe [::comments])])
  (fn [[diff comments]]
    (sequence
      (comp
        (mapcat (comp #(tree-seq sequential? seq %) val))
        (filter :id)
        (filter :new?))
      comments)))

(rf/reg-sub ::top-level-comments
  (fn []
    (rf/subscribe [::comments]))
  (fn [comments]
    (remove :new? (get comments [nil nil nil] [])) ; comments without a file and line numbers are top-level comments
    ))

(rf/reg-sub ::inline-comments
  (fn []
    (rf/subscribe [::comments]))
  (fn [comments]
    (dissoc comments [nil nil nil]) ; only return comments with a file and line numbers
    ))

(def extension->language
  {".feature" :gherkin
   ".java" :java
   ".js" :js
   ".json" :json
   ".kt" :kotlin
   ".py" :python
   ".sass" :sass
   ".scss" :scss
   ".ts" :js
   ".tsx" :jsx})

(rf/reg-sub ::diff-and-comments
  (fn []
    [(rf/subscribe [::diff])
     (rf/subscribe [::mutations])
     (rf/subscribe [::inline-comments])])
  (fn [[diff mutations comments]]
    (map (fn [{:keys [from to] :as file}]
           (-> file
             (assoc :highlight-syntax (some->> (or from to)
                                               (re-find #"\.[A-Za-z]+$")
                                               extension->language
                                               name
                                               (str "language-")))
             (update :chunks
                     (fn [chunks]
                       (sequence
                         (comp
                           (map-indexed (fn [i chnk]
                                          (map (fn [line]
                                                 (assoc line :segments (or (mutations [from i (line :old-line) (line :line)])
                                                                           (mutations [to i (line :new-line) (line :line)]))))
                                               chnk)))
                           (mapcat (fn [lines]
                                     (sequence
                                       (fn [rf]
                                         (let [buf (volatile! [])]
                                           (fn
                                             ([] (rf))
                                             ([res] (rf (rf res {:lines @buf})))
                                             ([res {:keys [old-line new-line] :as line}]
                                              (let [comment-key [(or from to) old-line new-line]]
                                                (if-let [cs (seq (get comments comment-key))]
                                                  (let [lines @buf]
                                                    (vreset! buf [])
                                                    (rf (rf res {:lines (conj lines line)})
                                                        {:comments (map (fn [c]
                                                                          (update c :value #(some-> % marked))) cs)}))
                                                  (do (vswap! buf conj line) res)))))))
                                       lines))))
                         chunks)))))
         diff)))

(defn treeify-files [files]
  (if-let [paths (seq (remove (comp empty? :parts) files))]
    (sequence
      (comp
        (xf/by-key (comp first :parts)
                   #(update % :parts rest)
                   (xf/into []))
        (map (fn [[k files]]
               [k (treeify-files files)]))
        (xf/sort-by first))
      paths)
    (first (filter :path files))))

(rf/reg-sub ::file-tree
  (fn []
    [(rf/subscribe [::stats])
     (rf/subscribe [::inline-comments])])
  (fn [[stats comments]]
    (let [file->comment-count (into {}
                                    (comp
                                      (map (fn [[k comments]]
                                             [k (sequence
                                                  (comp
                                                    (mapcat (partial tree-seq :replies :replies))
                                                    (filter :id))
                                                  comments)]))
                                      (xf/by-key ffirst second (comp (mapcat seq) xf/count)))
                                    comments)]
      (first
        (sequence
          (comp
            (map (some-fn (comp :path :old) (comp :path :new)))
            (map (fn [path]
                   {:path path
                    :comment-count (file->comment-count path 0)
                    :parts (string/split path #"/")}))
            (xf/into [])
            (map treeify-files))
          stats)))))

(rf/reg-sub ::stewards
  (fn []
    (rf/subscribe [::pull-request]))
  (fn [{{:keys [display_name]} :author :keys [stewards]}]
    (into #{}
          (comp
            (mapcat second)
            (remove #{display_name})
            (xf/by-key identity xf/count)
            (xf/sort-by second >)
            (map first))
          stewards)))
