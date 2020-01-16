(ns review.events
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [re-frame.core :as rf]
            [cljs-time.format :as tf]
            [goog.date.DateTime :as DateTime]
            [net.cgrand.xforms :as xf]
            [review.db :as db]
            [review.effects :as effects]
            [review.util :as util]))

(rf/reg-event-db ::initialize-db
  (fn [_ _]
    db/default-db))

(rf/reg-event-fx ::set-pull-request-id
  (fn [{:keys [db]} [_ id]]
    {:db (assoc db :pull-request-id id)
     ::effects/load-pull-request {:id id :success ::set-pull-request}}))

(defn reshape-comment [cmt]
  (let [{{:keys [path from to] :as inline} :inline
         {:keys [raw]} :content
         {:keys [display_name]} :user
         {parent-id :id} :parent
         :keys [id updated_on]} cmt]
    {:id id
     :parent-id parent-id
     :inline inline
     :user display_name
     :comment raw
     :timestamp (tf/unparse (tf/formatter "MMM d, Y")
                            (DateTime/fromRfc822String updated_on))}))

(defn treeify-comments [comments]
  (reduce
    (fn [comments cmt]
      (let [{:keys [id parent-id] :as cmt} (reshape-comment cmt)]
        (if parent-id
          (walk/postwalk (fn [n]
                           (if (and (map? n) (= parent-id (n :id)))
                             (update n :replies (fnil conj []) cmt)
                             n))
                         comments)
          (assoc comments id cmt))))
    {} comments))

(rf/reg-event-db ::set-pull-request
  (fn [db [_ {:keys [comments] :as pull-request}]]
    (assoc db
           :pull-request pull-request
           :comments (->> comments
                       (remove :deleted)
                       treeify-comments
                       (sequence (xf/by-key (comp (juxt :path :from :to) :inline second)
                                            (comp (map second) (xf/into []))))
                       (into {})
                       (merge-with concat (util/suggested-comments (pull-request :diff)))
                       (walk/postwalk (fn [n]
                                        (if (and (map? n) (contains? n :comment))
                                          (assoc n :multiline? (string/includes? (n :comment) "\n"))
                                          n)))))))

(defn new-comment-form
  ([] {:id (random-uuid) :mode :input})
  ([path from to]
   (assoc (new-comment-form) :inline {:path path :from from :to to})))

(rf/reg-event-db ::add-inline-comment
  (fn [db [_ file [old-line new-line]]]
    (update-in db [:comments [file old-line new-line]]
               (fn [comments]
                 (cond
                   (nil? comments) [(new-comment-form file old-line new-line)]
                   (some (comp #{:input} :mode) comments) comments
                   :else (conj comments (assoc (new-comment-form file old-line new-line)
                                               :context (util/context (-> db :pull-request :diff) file old-line new-line))))))))

(rf/reg-event-db ::add-top-level-comment
  (fn [db [_ id s]]
    (prn id s)
    (if (and id s)
      (update-in db [:comments [nil nil nil]] (fnil conj []) {:id id :comment s :new? true})
      db)))

(rf/reg-event-db ::add-comment
  (fn [db [_ id s]]
    (if (and id s)
      (update db :comments (partial walk/postwalk (fn [n]
                                                    (if (and (map? n) (= id (n :id)))
                                                      (-> n
                                                        (assoc :comment s :new? true :multiline? (string/includes? s "\n"))
                                                        (dissoc :mode))
                                                      n))))
      db)))

(rf/reg-event-db ::reply-to-comment
  (fn [db [_ id]]
    (if id
      (update db :comments (partial walk/postwalk (fn [n]
                                                    (if (and (map? n) (= id (n :id)))
                                                      (update n :replies (fnil conj [])
                                                              (assoc (new-comment-form) :parent-id id))
                                                      n))))
      db)))

(rf/reg-event-db ::edit-comment
  (fn [db [_ id]]
    (if id
      (update db :comments (partial walk/postwalk (fn [n]
                                                    (if (and (map? n) (= id (n :id)))
                                                      (assoc n :mode :input)
                                                      n))))
      db)))

(rf/reg-event-db ::cancel-comment
  (fn [db [_ id]]
    (if id
      (update db :comments (partial walk/postwalk (fn [n]
                                                    (if (and (sequential? n) (not (map-entry? n)))
                                                      (into (empty n)
                                                            (comp
                                                              (map (fn [n]
                                                                     (if (and (map? n) (= id (n :id)))
                                                                       (when (n :comment)
                                                                         (dissoc n :mode))
                                                                       n)))
                                                              (remove nil?))
                                                            n)
                                                      n))))
      db)))

(rf/reg-event-db ::delete-comment
  (fn [db [_ id]]
    (if id
      (update db :comments (partial walk/postwalk (fn [n]
                                                    (if (and (sequential? n) (not (map-entry? n)))
                                                      (into (empty n)
                                                            (remove (every-pred map? (comp #{id} :id)))
                                                            n)
                                                      n))))
      db)))

(rf/reg-event-fx ::submit-comments
  (fn [{:keys [db]} _]
    {::effects/submit-comments {:id (db :pull-request-id)
                                :comments (->> db :comments
                                            (tree-seq coll? seq)
                                            (filter :new?))
                                :success ::mark-comments-as-submitted}}))

(rf/reg-event-db ::mark-comments-as-submitted
  (fn [db _]
    (update db :comments (partial walk/postwalk (fn [n]
                                                    (if (and (map? n) (n :new?))
                                                      (dissoc n :new?)
                                                      n))))))

(rf/reg-event-fx ::approve
  (fn [{:keys [db]} _]
    {::effects/approve {:id (db :pull-request-id)}}))

(rf/reg-event-fx ::copy-text
  (fn [_ [_ text]]
    {::effects/copy-text text}))

