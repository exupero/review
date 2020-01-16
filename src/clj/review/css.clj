(ns review.css
  (:require [garden.def :refer [defstyles]]
            [garden.units :as u]
            [garden.color :as c]))

(def accent (c/hsla 240 100 60 1))

(def gray-light (c/hsla 0 0 90 1))
(def gray (c/hsla 0 0 80 1))
(def gray-dark (c/hsla 0 0 60 1))

(def green (c/hsla 120 100 90 1))
(def green-highlight (c/hsla 120 100 60 1))
(def green-dark (c/hsla 120 100 30 1))
(def green-bright (c/hsla 120 100 60 1))

(def red (c/hsla 0 100 93 1))
(def red-highlight (c/hsla 0 100 86 1))
(def red-dark (c/hsla 0 100 40 1))

(def yellow (c/hsla 60 100 60 1))

(def diff
  [[:.diff {:margin-bottom (u/rem 2)}]
   [:.diff__chunk {:border [[(u/px 1) :solid gray-dark]]
                   :margin-bottom (u/px -1)}
    [:pre :code {:margin 0
                 :font-family ["'Menlo'"]
                 :font-size (u/pt 10)}]]
   [:.diff__column {:display :flex
                    :flex-direction :column}]
   [:.diff__cell {:height (u/pt 15)
                  :line-height (u/pt 15)}]
   [:.diff__line-numbers {:font-family ["'Menlo'"]
                          :font-size (u/pt 10)
                          :text-align :right
                          :background-color gray-light
                          :border-collapse :collapse}]
   [:.diff__line-number {:padding [[0 (u/rem 0.3)]]
                         :line-height (u/pt 15)}]
   [:.diff__code {:overflow :scroll}]
   [:.diff__segment {:line-height (u/pt 15)}]])

(def comments
  [[:.comment-container {:padding (u/rem 1)
                         :border [[:solid gray-dark]]
                         :border-width [[0 (u/px 1)]]
                         :position :relative
                         :margin-bottom (u/px -1)}
    [:&:before {:content "''"
                :display :block
                :position :absolute
                :left (u/rem -0.5)
                :top 0
                :bottom 0
                :width (u/rem 0.5)
                :background-color gray-dark}]]
   [:.comment {:position :relative}
    [:p {:margin [[(u/rem 0.5) 0]]}]
    [:code {:font-family ["'Menlo'"]
            :background-color gray-light
            :padding [[0 (u/rem 0.2)]]
            :border-radius (u/rem 0.2)}]]
   [:.comment--new {:background-color yellow}
    [:textarea {:margin-top (u/rem 0.5)}]]
   [:.comment__oneline [:p {:margin 0
                            :display :inline}]]
   [:.comment__replies {:padding [[(u/rem 0.5) 0 0 (u/rem 0.5)]]}]])

(def buttons
  [[:.btn {:font-size (u/pt 12)
           :border :none
           :border-radius (u/px 3)
           :cursor :pointer
           :padding [[(u/rem 0.4) (u/rem 0.7)]]
           :background-color gray-light}]
   [:.btn--primary {:background-color accent
                    :color :white}]
   [:.btn--secondary {:background-color :transparent
                      :color gray-dark
                      :padding 0}
    [:&:hover {:text-decoration :underline}]]])

(def grid
  [[:.ml1 {:margin-left (u/rem 1)}]
   [:.mr1 {:margin-right (u/rem 1)}]
   [:.mr-space {:margin-right (u/rem 0.3)}]
   [:.mb1 {:margin-bottom (u/rem 1)}]
   [:.mt1 {:margin-top (u/rem 1)}]])

(def badge
  [[:.badge {:padding [[(u/rem 0.1) (u/rem 0.2)]]
             :background-color gray-light
             :border-radius (u/px 3)
             :line-height (u/pt 20)}]
   [:.badge--green {:background-color green}]
   ])

(defstyles screen
  [:body {:font-family ["'Helvetica Neue'"]}]
  [:body [:* {:box-sizing :border-box}]]
  [:textarea {:padding (u/rem 0.5)
              :font-size (u/pt 12)}]
  [:.flex {:display :flex}]
  [:.flex-stretch {:align-self :stretch}]
  [:.muted {:color gray-dark}]
  [:.full-width {:width (u/percent 100)}]
  [:.align-right {:text-align :right}]
  [:.pull-right {:float :right}]
  [:.clearfix [:&:after {:content "''"
                         :display :block
                         :clear :both}]]
  [:.added {:background-color green}
   [:&.added--color {:color green-dark}]]
  [:.added-highlight {:background-color green-highlight}]
  [:.removed {:background-color red}
   [:&.removed--color {:color red-dark}]]
  [:.removed-highlight {:background-color red-highlight}]
  [:.pointer {:cursor :pointer}]
  [:.hover [:&:hover {:background-color gray}]]
  (let [radius (u/rem 0.3)]
    [:.pills [:> [:* {:padding [[(u/rem 0.1) (u/rem 0.4)]]}
                  [:&:first-child {:border-radius [[radius 0 0 radius]]}]
                  [:&:last-child {:border-radius [[0 radius radius 0]]}]]]])
  [:.hljs {:padding [[0 "!important"]]
           :background [[:transparent "!important"]]
           :overflow-x [[:visible "!important"]]}]
  [(keyword "[data-comment-count]") {:position :relative}
   [:&:before {:content "attr(data-comment-count) ' 💬'"
               :display :block
               :position :absolute
               :font-size (u/pt 10)
               :color gray-dark
               :right (u/percent 100)
               :width (u/rem 3)
               :top (u/px -2)
               :text-align :right
               }]]
  diff
  comments
  buttons
  grid
  badge
           )
