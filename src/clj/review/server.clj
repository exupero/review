(ns review.server
  (:require [review.handler :refer [handler]]
            [config.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (run-jetty handler {:port port :join? false})))

(comment

  (def server (atom nil))

  (reset! server (run-jetty #'handler {:port 8281 :join? false}))

  (.stop @server)

  )
