(ns beesbuddy.server.handler
  (:require [beesbuddy.config :as config]
            [beesbuddy.util.log :as log]
            [compojure.core :refer [defroutes GET]]
            [ring.middleware.cookies :refer [wrap-cookies]]))

(defroutes routes
  "Main application routes"
  (GET "/*" []  "<h1>Hello wsorldss!!!</h1>"))
;; => #'beesbuddy.server.handler/routes

(def ^:private middleware
  ;; ▼▼▼ POST-PROCESSING ▼▼▼ happens from TOP-TO-BOTTOM
  [#'wrap-cookies                               ; Parses cookies in the request map and assocs as :cookies
   ])

(defn- apply-middleware
  [handler]
  (reduce
   (fn [handler middleware-fn]
     (middleware-fn handler))
   handler
   middleware))

(def ^{:arglists '([request] [request respond raise])} app
  "The primary entry point to the Ring HTTP server."
  (apply-middleware routes))

;; during interactive dev, recreate `app` whenever a middleware var or `routes/routes` changes.
(when config/is-dev?
  (doseq [varr  (cons #'routes middleware)
          :when (instance? clojure.lang.IRef varr)]
    (add-watch varr ::reload (fn [_key _ref _old-state _new-state]
                               (log/infof "%s changed, rebuilding %s" varr #'app)
                               (alter-var-root #'app (constantly (apply-middleware routes)))))))