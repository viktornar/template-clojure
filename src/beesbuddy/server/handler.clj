(ns beesbuddy.server.handler
  (:require
   [compojure.core :refer [defroutes GET]]))

(defroutes routes
  "Main application routes"
  (GET "/*" []  "<h1>Hello world !!!</h1>"))

(def ^{:arglists '([request] [request respond raise])} app
  "The primary entry point to the Ring HTTP server."
  routes)
