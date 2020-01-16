(ns review.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources]]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [shadow.http.push-state :as push-state]
            [review.bitbucket :as bb]))

(defroutes routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/:id" [id] (resource-response "index.html" {:root "public"}))
  (GET "/api/pull-requests/:id" [id] {:body (bb/pull-request id)})
  (POST "/api/pull-requests/:id/comments" {{:keys [id]} :route-params :keys [body-params]}
        (try
          (doseq [{cmt "comment" {:strs [path from to]} "inline" :strs [parent-id]} body-params]
            (bb/comment! id cmt {:path path :from from :to to :parent-id parent-id}))
          (catch Exception e
            (prn e)))
        {:status 201 :body "{}"})
  (POST "/api/pull-requests/:id/approval" [id]
        (bb/approve! id)
        {:status 201 :body "{}"})
  (resources "/"))

(def handler (-> #'routes
               wrap-params
               (wrap-restful-format :formats [:json])
               wrap-stacktrace
               wrap-reload))
