(ns beesbuddy.config
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [environ.core :as env])
  (:import clojure.lang.Keyword))

(set! *warn-on-reflection* true)

(defonce ^{:doc "This UUID is randomly-generated upon launch and used to identify this specific Metabase instance during
                this specifc run. Restarting the server will change this UUID, and each server in a horizontal cluster
                will have its own ID, making this different from the `site-uuid` Setting."}
  local-process-uuid
  (str (random-uuid)))

(def ^Boolean is-windows?
  "Are we running on a Windows machine?"
  #_{:clj-kondo/ignore [:discouraged-var]}
  (str/includes? (str/lower-case (System/getProperty "os.name")) "win"))

(def ^:private app-defaults
  "Global application defaults"
  {:bb-run-mode                     "prod"
   ;; DB Settings
   :bb-db-type                      "h2"
   :bb-db-file                      "beesbuddy.db"
   :bb-db-automigrate               "true"
   :bb-db-logging                   "true"
   ;; Jetty Settings. Full list of options is available here: https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj
   :bb-jetty-port                   "3000"
   :bb-jetty-join                   "true"
   ;; other application settings
   :bb-password-complexity          "normal"
   :bb-ns-trace                     ""                      ; comma-separated namespaces to trace
   :max-session-age                 "20160"                 ; session length in minutes (14 days)
   :bb-colorize-logs                (str (not is-windows?)) ; since PowerShell and cmd.exe don't support ANSI color escape codes or emoji,
   :bb-emoji-in-logs                (str (not is-windows?)) ; disable them by default when running on Windows. Otherwise they're enabled
   :bb-qp-cache-backend             "db"
   :bb-jetty-async-response-timeout (str (* 10 60 1000))    ; 10m
   :bb-load-sample-content          "false"})

(defn config-str
  "Retrieve value for a single configuration key.  Accepts either a keyword or a string.

   We resolve properties from these places:

   1.  environment variables (ex: MB_DB_TYPE -> :mb-db-type)
   2.  jvm options (ex: -Dmb.db.type -> :mb-db-type)
   3.  hard coded `app-defaults`"
  [k]
  (let [k       (keyword k)
        env-val (k env/env)]
    (or (when-not (str/blank? env-val) env-val)
        (k app-defaults))))

(defn config-int  "Fetch a configuration key and parse it as an integer." ^Integer [k] (some-> k config-str Integer/parseInt))
(defn config-bool "Fetch a configuration key and parse it as a boolean."  ^Boolean [k] (some-> k config-str Boolean/parseBoolean))
(defn config-kw   "Fetch a configuration key and parse it as a keyword."  ^Keyword [k] (some-> k config-str keyword))

(def ^Boolean is-dev?  "Are we running in `dev` mode (i.e. in a REPL or via `clojure -M:run`)?" (= :dev  (config-kw :bb-run-mode)))
(def ^Boolean is-prod? "Are we running in `prod` mode (i.e. from a JAR)?"                       (= :prod (config-kw :bb-run-mode)))
(def ^Boolean is-test? "Are we running in `test` mode (i.e. via `clojure -X:test`)?"            (= :test (config-kw :bb-run-mode)))

(defn load-sample-content?
  "Load sample content on fresh installs?
  Using this effectively means `BB_LOAD_SAMPLE_CONTENT` defaults to true."
  []
  (not (false? (config-bool :bb-load-sample-content))))