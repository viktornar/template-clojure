(ns dev
  "Put everything needed for REPL development within easy reach"
  (:require
   [beesbuddy.server.handler :as handler]
   [beesbuddy.server :as server]))


(defn start!
  "Start Beesbuddy"
  []
  (server/start-web-server! #'handler/app))

(defn stop!
  "Stop Beesbuddy"
  []
  (server/stop-web-server!))

(defn restart!
  "Restart Beesbuddy"
  []
  (stop!)
  (start!))