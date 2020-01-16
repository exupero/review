(ns review.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import [goog.history Html5History EventType])
  (:require [secretary.core :as secretary]
            [goog.events :as gevents]
            [re-frame.core :as rf]
            [review.events :as events]
            ))

(defn hook-browser-navigation! []
  (doto (Html5History.)
    (gevents/listen EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
    (.setUseFragment false)
    (.setPathPrefix "")
    (.setEnabled true)))

(defn app-routes []
  ;; --------------------
  ;; define routes here
  (defroute "/" []
    )

  (defroute "/:id" [id]
    (rf/dispatch [::events/set-pull-request-id id]))

  ;; --------------------
  (hook-browser-navigation!)
  )
