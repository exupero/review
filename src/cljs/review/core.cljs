(ns review.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [review.events :as events]
            [review.routes :as routes]
            [review.views :as views]
            [review.config :as config]
            ))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (rf/clear-subscription-cache!)
  (reagent/render [views/main]
                  (.getElementById js/document "app")))

(defn init []
  (routes/app-routes)
  (rf/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))
