(ns review.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [cljs-time.format :as tf]
            ["highlight.js" :as hl]
            ["highlight.js/lib/languages/javascript" :as hljs]
            ["marked" :as marked]
            [review.subs :as subs]
            [review.events :as events]
            [review.util :as util]))

(defn classes [& cls]
  (transduce
    (comp
      (remove nil?)
      (map name)
      (interpose " "))
    str cls))

(defn description [d]
  [:div {:dangerouslySetInnerHTML {:__html d}}])

(defn new-top-level-comment [_]
  (let [textarea (r/atom nil)]
    (fn [{:keys [id]}]
      [:div.comment
       [:textarea.full-width {:ref (fn [node]
                                     (when node
                                       (reset! textarea node)))}]
       [:div.align-right
        [:button.btn.btn--primary
         {:on-click (fn [_]
                      (rf/dispatch [::events/add-top-level-comment id (.-value @textarea)])
                      (set! (.-value @textarea) ""))}
         "Add"]]])))

(defn new-inline-comment [_]
  (let [textarea (r/atom nil)]
    (fn [{cmt :comment :keys [id]}]
      [:div.comment
       [:textarea.full-width {:defaultValue cmt
                              :ref (fn [node]
                                     (when node
                                       (.focus node)
                                       (reset! textarea node)))}]
       [:div.align-right
        [:button.btn.btn--secondary.mr1
         {:on-click #(rf/dispatch [::events/cancel-comment id])}
         "Cancel"]
        [:button.btn.btn--primary
         {:on-click #(rf/dispatch [::events/add-comment id (.-value @textarea)])}
         "Add"]]])))

(defn new-comment [{cmt :comment :keys [id multiline?]}]
  (if multiline?
    [:div.comment.comment--new
     [:div {:dangerouslySetInnerHTML {:__html (-> cmt marked util/emojify)}}]
     [:div
      [:button.btn.btn--secondary
       {:on-click #(rf/dispatch [::events/edit-comment id])}
       [:small "Edit"]]
      [:span.muted " – "]
      [:button.btn.btn--secondary
       {:on-click #(rf/dispatch [::events/delete-comment id])}
       [:small "Delete"]]]]
    [:div.comment.comment--new
     [:span.comment__oneline {:dangerouslySetInnerHTML {:__html (-> cmt marked util/emojify)}}]
     [:span.muted " – "]
     [:button.btn.btn--secondary
      {:on-click #(rf/dispatch [::events/edit-comment id])}
      [:small "Edit"]]
     [:span.muted " – "]
     [:button.btn.btn--secondary
      {:on-click #(rf/dispatch [::events/delete-comment id])}
      [:small "Delete"]]]))

(declare review-comment)

(defn published-comment [{cmt :comment :keys [id user multiline? timestamp replies]}]
  [:div.comment {:key id}
   [:strong user]
   (if multiline?
     [:div
      [:div {:dangerouslySetInnerHTML {:__html (-> cmt marked util/emojify)}}]
      [:div
       [:button.btn.btn--secondary [:small "Reply"]]]]
     [:span
      [:span.muted " – "]
      [:span.comment__oneline {:dangerouslySetInnerHTML {:__html (-> cmt marked util/emojify)}}]
      [:span.muted " – " [:small timestamp] " – "]
      [:button.btn.btn--secondary
       {:on-click #(rf/dispatch [::events/reply-to-comment id])}
       [:small "Reply"]]])
   (when (seq replies)
     [:div.comment__replies (for [reply replies]
                              [review-comment reply])])])

(defn review-comment [{:keys [id mode new?] :as cmt}]
  [:div {:key id}
   (cond
     (= :input mode) [new-inline-comment cmt]
     new? [new-comment cmt]
     :else [published-comment cmt])])

(defn review-comments [comments]
  [:div.comment-container (for [cmt comments]
                            [review-comment cmt])])

(defn diff-chunk [file highlight-syntax lines]
  (let [[{o1 :old-line n1 :new-line}] lines
        {o2 :old-line n2 :new-line} (last lines)]
    [:div.flex.diff__chunk {:key (str o1 ":" n1 "-" o2 ":" n2)}
     [:table.diff__line-numbers
      [:tbody (for [{:keys [old-line new-line]} lines]
                [:tr.pointer.hover.diff__cell
                 {:key (str old-line ":" new-line)
                  :on-click #(rf/dispatch [::events/add-inline-comment file [old-line new-line]])}
                 [:td.diff__line-number old-line]
                 [:td.diff__line-number new-line]])]]
     [:div.full-width.diff__column.diff__code
      [:pre
       [:code {:class highlight-syntax
               :ref (fn [n]
                      (when n (js/setTimeout #(hl/highlightBlock n) 0)))}
        (for [[i {t :type :keys [line segments]}] (map-indexed vector lines)]
          [:div.diff__cell.diff__cell--code {:key i :class t}
           (if segments
             [:span.flex (for [[i {:keys [value added removed]}] (map-indexed vector segments)]
                           [:span.diff__segment
                            {:key i
                             :class (classes
                                      (when added :added-highlight)
                                      (when removed :removed-highlight))}
                            value])]
             line)])]]]]))

(defn review-comment-with-context [{{:keys [path]} :inline :keys [id context] :as cmt}]
  [:div {:key id}
   [:div path]
   [diff-chunk path "" (take 3 context)]
   [review-comments [cmt]]
   [diff-chunk path "" (drop 3 context)]])

(defn diff-summary [removed added]
  [:span.pills
   [:span.removed.removed--color "-" removed]
   [:span.added.added--color "+" added]])

(defn file-tree [tree]
  [:div (for [[k v] tree]
          [:div {:key k}
           (if (contains? v :path)
             [:a (cond-> {:href (str "#" (v :path))}
                         (pos? (v :comment-count)) (assoc :data-comment-count (v :comment-count)))
              k]
             [:div k "/"
              [:div {:style {:padding-left "0.5rem"}}
               [file-tree v]]])])])

(defn diff [files]
  [:div (for [{:keys [from to chunks highlight-syntax]} files]
          [:div.diff {:key (or to from)}
           (if (or (not from) (not to) (= from to))
             [:div [:a {:name (or from to)} (or from to)]]
             [:div [:a {:name (or from to)} from] " → " to])
           [:div (for [{:keys [lines comments]} chunks]
                   (if comments
                     [review-comments comments]
                     [diff-chunk (or from to) highlight-syntax lines]))]])])

(defn main []
  (let [{:keys [id title stats author source participants destination state]} @(rf/subscribe [::subs/pull-request])
        created-on @(rf/subscribe [::subs/created-on])
        updated-on @(rf/subscribe [::subs/updated-on])
        source (some-> source :branch :name)]
    [:div
     [:h1
      [:span {:style {:font-weight :normal}} "#" id ": "]
      [:span {:dangerouslySetInnerHTML {:__html @(rf/subscribe [::subs/title])}}]]
     [:div.mb1
      [:span
       "by " [:span.badge (some-> author :display_name)]
       [:span.muted " on "] created-on
       (when-not (= updated-on created-on)
         [:span [:span.muted " updated "] updated-on])]
      [:div.pull-right
       [:div.mb1
        [:span.badge.pointer
         {:on-click #(rf/dispatch [::events/copy-text source])}
         source]
        " → "
        [:span.badge (some-> destination :branch :name)]]
       [:div.pull-right
        [:span.badge {:class (classes (when (= state "MERGED") :badge--green))} state]]]]
     [:div
      "Reviewers: "
      (for [{nm :name :keys [approved?]} @(rf/subscribe [::subs/reviewers])]
        [:span.badge.mr-space {:key nm :class (classes (when approved? :badge--green))} nm])]
     [:hr]
     [description @(rf/subscribe [::subs/description])]
     [:hr]
     [:h2 "Comments"]
     [:div (for [cmt @(rf/subscribe [::subs/top-level-comments])]
             [:div.mb1 [review-comment cmt]])]
     [:h2 "New Comments"]
     [:div (for [[i cmt] (map-indexed vector @(rf/subscribe [::subs/new-comments]))]
             (if (cmt :context)
               [:div.mb1 {:key i} [review-comment-with-context cmt]]
               [:div.mb1 {:key i} [review-comment cmt]]))]
     [new-top-level-comment {:id (random-uuid)}]
     [:hr]
     [:h2 "Changes"]
     [:div.clearfix.mb1
      [:div.pull-right
       [:button.btn.mr1
        {:on-click #(rf/dispatch [::events/approve])}
        "Approve"]
       [:button.btn.btn--primary
        {:on-click #(rf/dispatch [::events/submit-comments])}
        "Submit Review"]]
      [diff-summary @(rf/subscribe [::subs/total-lines-removed]) @(rf/subscribe [::subs/total-lines-added])]
      [:span " " @(rf/subscribe [::subs/file-count]) " files changed"]
      (when-let [stewards (seq @(rf/subscribe [::subs/stewards]))]
        [:span
         [:span.muted " affecting lines last changed by "]
         (for [nm stewards]
           [:span.badge.mr-space {:key nm} nm])])]
     [:div.flex
      [file-tree @(rf/subscribe [::subs/file-tree])]
      [:div.ml1 [diff @(rf/subscribe [::subs/diff-and-comments])]]]
     ]))
