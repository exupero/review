(ns review.effects
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]))

(defn request [req]
  (ajax/ajax-request
    (merge
      {:format (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :handler (fn [[ok? result]]
                  (if ok?
                    (js/console.log result)
                    (js/console.error result)))}
      req)))

(rf/reg-fx ::load-pull-request
  (fn [{:keys [id success]}]
    (request
      {:uri (str "/api/pull-requests/" id)
       :method :get
       :handler (fn [[ok? result]]
                  (if ok?
                    (rf/dispatch [success result])
                    (js/console.error result)))})))

(rf/reg-fx ::submit-comments
  (fn [{:keys [id comments success]}]
    (request
      {:uri (str "/api/pull-requests/" id "/comments")
       :method :post
       :params comments
       :handler (fn [[ok? result]]
                  (if ok?
                    (rf/dispatch [success result])
                    (js/console.error result)))})))

(rf/reg-fx ::approve
  (fn [{:keys [id]}]
    (request
      {:uri (str "/api/pull-requests/" id "/approval")
       :method :post})))

(rf/reg-fx ::copy-text
  (fn [text]
    (-> js/navigator .-clipboard (.writeText text)
      (.then (constantly nil) #(js/console.error "Failed to copy text" %)))))
