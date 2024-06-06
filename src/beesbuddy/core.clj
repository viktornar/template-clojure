(ns beesbuddy.core
  (:require
   [clojure.string :as str]
   [clojure.tools.trace :as trace]
   [java-time.api :as t]
  ;;  [beesbuddy.analytics.prometheus :as prometheus]
   [beesbuddy.config :as config]
   [beesbuddy.core.initialization-status :as init-status]
  ;;  [beesbuddy.db :as mdb]
  ;;  [beesbuddy.driver.h2]
  ;;  [beesbuddy.driver.mysql]
  ;;  [beesbuddy.driver.postgres]
  ;;  [beesbuddy.events :as events]
   [beesbuddy.logger :as logger]
  ;;  [beesbuddy.models.cloud-migration :as cloud-migration]
  ;;  [beesbuddy.models.setting :as settings]
  ;;  [beesbuddy.plugins :as plugins]
   [beesbuddy.plugins.classloader :as classloader]
  ;;  [beesbuddy.sample-data :as sample-data]
   [beesbuddy.server :as server]
   [beesbuddy.server.handler :as handler]
  ;;  [beesbuddy.setup :as setup]
  ;;  [beesbuddy.task :as task]
  ;;  [beesbuddy.troubleshooting :as troubleshooting]
   [beesbuddy.util :as u]
   [beesbuddy.util.log :as log])
  (:import
   (java.lang.management ManagementFactory)))

(set! *warn-on-reflection* true)

(comment
  ;; Load up the drivers shipped as part of the main codebase, so they will show up in the list of available DB types
  ;; beesbuddy.driver.h2/keep-me
  ;; beesbuddy.driver.mysql/keep-me
  ;; beesbuddy.driver.postgres/keep-me
  ;; Make sure the custom BeesBuddy logger code gets loaded up so we use our custom logger for performance reasons.
  logger/keep-me)

;; don't i18n this, it's legalese
(log/info
 (format "\nBeesBuddy %s" "snapshot")
 (format "\n\nCopyright © %d BeesBuddy, Inc." (.getYear (java.time.LocalDate/now))))
;;; --------------------------------------------------- Lifecycle ----------------------------------------------------

(defn- print-setup-url
  "Print the setup url during instance initialization."
  []
  (let [hostname  (or (config/config-str :mb-jetty-host) "localhost")
        port      (config/config-int :mb-jetty-port)
        site-url  
                      (str "http://"
                           hostname
                           (when-not (= 80 port) (str ":" port)))
        setup-url (str site-url "/setup/")]
    (log/info (u/format-color 'green
                              (str "Please use the following URL to setup your BeesBuddy installation:"
                                   "\n\n"
                                   setup-url
                                   "\n\n")))))

(defn- create-setup-token-and-log-setup-url!
  "Create and set a new setup token and log it."
  [] 
  ;; (setup/create-token!)   ; we need this here to create the initial token
  (print-setup-url))

(defn- destroy!
  "General application shutdown function which should be called once at application shutdown."
  []
  (log/info "BeesBuddy Shutting Down ...")
  ;; (task/stop-scheduler!)
  (server/stop-web-server!)
  ;; (prometheus/shutdown!)
  ;; This timeout was chosen based on a 30s default termination grace period in Kubernetes.
  ;; (let [timeout-seconds 20]
  ;;   (mdb/release-migration-locks! timeout-seconds))
  (log/info "BeesBuddy Shutdown COMPLETE"))

(defn- init!*
  "General application initialization function which should be run once at application startup."
  []
  (log/infof "Starting BeesBuddy version %s ..." "snapshot")
  ;; (log/infof "System info:\n %s" (u/pprint-to-str (troubleshooting/system-info)))
  (init-status/set-progress! 0.1)
  ;; First of all, lets register a shutdown hook that will tidy things up for us on app exit
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable destroy!))
  (init-status/set-progress! 0.2)
  ;; load any plugins as needed
  ;; (plugins/load-plugins!)
  (init-status/set-progress! 0.3)
  ;; (settings/validate-settings-formatting!)
  ;; startup database.  validates connection & runs any necessary migrations
  (log/info "Setting up and migrating BeesBuddy DB. Please sit tight, this may take a minute...")
  ;; Cal 2024-04-03:
  ;; we have to skip creating sample content if we're running tests, because it causes some tests to timeout
  ;; and the test suite can take 2x longer. this is really unfortunate because it could lead to some false
  ;; negatives, but for now there's not much we can do
  ;; (mdb/setup-db! :create-sample-content? (not config/is-test?))

  ;; Disable read-only mode if its on during startup.
  ;; This can happen if a cloud migration process dies during h2 dump.
  ;; (when (cloud-migration/read-only-mode)
  ;;   (cloud-migration/read-only-mode! false))

  (init-status/set-progress! 0.5)
  ;; Set up Prometheus
  ;; (when (prometheus/prometheus-server-port)
  ;;   (log/info "Setting up prometheus metrics")
  ;;   (prometheus/setup!)
  ;;   (init-status/set-progress! 0.6))

  (init-status/set-progress! 0.65)
  ;; run a very quick check to see if we are doing a first time installation
  ;; the test we are using is if there is at least 1 User in the database
  (let [new-install? false] 
    (init-status/set-progress! 0.7)
    (when new-install?
      (log/info "Looks like this is a new installation ... preparing setup wizard")
      ;; create setup token
      (create-setup-token-and-log-setup-url!)
      ;; publish install event
      ;; (events/publish-event! :event/install {}))
    )
    (init-status/set-progress! 0.8)
    ;; deal with our sample database as needed
    ;; (when (config/load-sample-content?)
    ;;   (if new-install?
    ;;     ;; add the sample database DB for fresh installs
    ;;     ;; (sample-data/extract-and-sync-sample-database!)
    ;;     ;; otherwise update if appropriate
    ;;     (sample-data/update-sample-database-if-needed!)))
    (init-status/set-progress! 0.9))

  (init-status/set-progress! 0.95)

  ;; start scheduler at end of init!
  ;; (task/start-scheduler!)
  (init-status/set-complete!)
  (let [start-time (.getStartTime (ManagementFactory/getRuntimeMXBean))
        duration   (- (System/currentTimeMillis) start-time)]
    (log/infof "BeesBuddy Initialization COMPLETE in %s" (u/format-milliseconds duration))))

(defn init!
  "General application initialization function which should be run once at application startup. Calls `[[init!*]] and
  records the duration of startup."
  []
  (let [start-time (t/zoned-date-time)]
    (init!*)))
    ;; (public-settings/startup-time-millis!
    ;;  (.toMillis (t/duration start-time (t/zoned-date-time))))))

;;; -------------------------------------------------- Normal Start --------------------------------------------------

(defn- start-normally []
  (log/info "Starting BeesBuddy in STANDALONE mode")
  (try
    ;; launch embedded webserver async
    (server/start-web-server! handler/app)
    ;; run our initialization process
    (init!)
    ;; Ok, now block forever while Jetty does its thing
    (when (config/config-bool :bb-jetty-join)
      (.join (server/instance)))
    (catch Throwable e
      (log/error e "BeesBuddy Initialization FAILED")
      (System/exit 1))))

(defn- run-cmd [cmd args]
  (classloader/require 'beesbuddy.cmd)
  ((resolve 'beesbuddy.cmd/run-cmd) cmd args))

;;; -------------------------------------------------- Tracing -------------------------------------------------------

(defn- maybe-enable-tracing
  []
  (let [bb-trace-str (config/config-str :bb-ns-trace)]
    (when (not-empty bb-trace-str)
      (log/warn "WARNING: You have enabled namespace tracing, which could log sensitive information like db passwords.")
      (doseq [namespace (map symbol (str/split bb-trace-str #",\s*"))]
        (try (require namespace)
             (catch Throwable _
               (throw (ex-info "A namespace you specified with BB_NS_TRACE could not be required" {:namespace namespace}))))
        (trace/trace-ns namespace)))))

;;; ------------------------------------------------ App Entry Point -------------------------------------------------

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn entrypoint
  "Launch BeesBuddy in standalone mode. (Main application entrypoint is [[beesbuddy.bootstrap/-main]].)"
  [& [cmd & args]]
  (maybe-enable-tracing)
  (if cmd
    (run-cmd cmd args) ; run a command like `java -jar beesbuddy.jar migrate release-locks` or `clojure -M:run migrate release-locks`
    (start-normally))) ; with no command line args just start BeesBuddy normally
