(ns hooks.clojure.core
  (:require
   [clj-kondo.hooks-api :as hooks]
   [clojure.set :as set]
   [clojure.string :as str]))

(defn- node->qualified-symbol [node]
  (try
   (when (hooks/token-node? node)
     (let [sexpr (hooks/sexpr node)]
       (when (symbol? sexpr)
         (let [resolved (hooks/resolve {:name sexpr})]
           (when-not (= :clj-kondo/unknown-namespace (:ns resolved))
             (symbol (name (:ns resolved)) (name (:name resolved))))))))
   ;; some symbols like `*count/Integer` aren't resolvable.
   (catch Exception _
     nil)))

(def ^:private symbols-allowed-in-fns-not-ending-in-an-exclamation-point
  '#{;; these toucan methods might actually set global values if it's used outside of a transaction,
     ;; but since mt/with-temp runs in a transaction, so we'll ignore them in this case.
     toucan2.core/delete!
     toucan2.core/update!
     toucan2.core/insert!
     toucan2.core/insert-returning-instance!
     toucan2.core/insert-returning-instances!
     toucan2.core/insert-returning-pk!
     toucan2.core/insert-returning-pks!
     clojure.core.async/<!!
     clojure.core.async/>!!
     clojure.core.async/alts!!
     clojure.core.async/close!
     clojure.core.async/poll!
     clojure.core.memoize/memo-clear!
     clojure.core/conj!
     clojure.core/persistent!
     clojure.core/reset!
     clojure.core/swap!
     clojure.core/volatile!
     clojure.core/vreset!
     clojure.core/vswap!
     clojure.java.jdbc/execute!
     methodical.core/add-aux-method-with-unique-key!
     methodical.core/remove-aux-method-with-unique-key!
     next.jdbc/execute!

     ;; Definitely thread safe
     beesbuddy.test.util.dynamic-redefs/patch-vars!

     ;; TODO: most of these symbols shouldn't be here, we should go through them and
     ;; find the functions/macros that use them and make sure their names end with !
     ;; best way to do this is try remove each of these and rely on kondo output to find places where it's used
     clojure.test/grant-collection-perms!
     clojure.test/grant-collection-perms-fn!
     clojure.test/grant-perms-fn!
     clojure.test/purge-old-entries!
     clojure.test/revoke-collection-perms!
     clojure.test/save-results!
     beesbuddy-enterprise.advanced-permissions.models.permissions/update-db-download-permissions!
     beesbuddy-enterprise.internal-user/install-internal-user!
     beesbuddy-enterprise.sso.integrations.saml-test/call-with-login-attributes-cleared!
     beesbuddy.actions/perform-action!
     beesbuddy.analytics.snowplow-test/fake-track-event-impl!
     beesbuddy.analytics.snowplow/track-event-impl!
     beesbuddy.api.public-test/add-card-to-dashboard!
     beesbuddy.cmd.dump-to-h2/dump-to-h2!
     beesbuddy.cmd.load-from-h2/load-from-h2!
     beesbuddy.core/ensure-audit-db-installed!
     beesbuddy.db.schema-migrations-test.impl/run-migrations-in-range!
     beesbuddy.db.setup/migrate!
     beesbuddy.db.setup/setup-db!
     beesbuddy.db/migrate!
     beesbuddy.db/setup-db!
     beesbuddy.driver.mongo-test/create-database-from-row-maps!
     beesbuddy.driver.postgres-test/create-enums-db!
     beesbuddy.driver.postgres-test/drop-if-exists-and-create-db!
     beesbuddy.driver.sql-jdbc.execute/execute-statement!
     beesbuddy.email-test/reset-inbox!
     beesbuddy.email/send-email!
     beesbuddy.models.action/insert!
     beesbuddy.models.collection.graph-test/clear-graph-revisions!
     beesbuddy.models.collection.graph-test/do-with-n-temp-users-with-personal-collections!
     beesbuddy.models.field-values/create-or-update-full-field-values!
     beesbuddy.models.model-index/add-values!
     beesbuddy.models.moderation-review/create-review!
     beesbuddy.models.on-demand-test/add-dashcard-with-parameter-mapping!
     beesbuddy.models.permissions/grant-application-permissions!
     beesbuddy.models.permissions/grant-collection-read-permissions!
     beesbuddy.models.permissions/grant-collection-readwrite-permissions!
     beesbuddy.models.permissions/grant-full-data-permissions!
     beesbuddy.models.permissions/grant-native-readwrite-permissions!
     beesbuddy.models.permissions/grant-permissions!
     beesbuddy.models.permissions/revoke-application-permissions!
     beesbuddy.models.permissions/revoke-data-perms!
     beesbuddy.models.permissions/update-data-perms-graph!
     beesbuddy.models.permissions/update-group-permissions!
     beesbuddy.models.persisted-info/ready-database!
     beesbuddy.models.revision/revert!
     beesbuddy.models.setting-test/test-user-local-allowed-setting!
     beesbuddy.models.setting-test/test-user-local-only-setting!
     beesbuddy.models.setting.cache/restore-cache!
     beesbuddy.models.setting/set!
     beesbuddy.models.setting/validate-settings-formatting!
     beesbuddy.permissions.test-util/with-restored-perms!
     beesbuddy.pulse/send-notifications!
     beesbuddy.pulse/send-pulse!
     beesbuddy.query-processor.streaming.interface/begin!
     beesbuddy.query-processor.streaming.interface/finish!
     beesbuddy.query-processor.streaming.interface/write-row!
     beesbuddy.sample-data/try-to-extract-sample-database!
     beesbuddy.setup/create-token!
     beesbuddy.sync.sync-metadata.fields.sync-metadata/update-field-metadata-if-needed!
     beesbuddy.sync.sync-metadata/sync-db-metadata!
     beesbuddy.sync.util-test/sync-database!
     beesbuddy.sync.util/store-sync-summary!
     beesbuddy.sync/sync-database!
     beesbuddy.task.index-values/job-init!
     beesbuddy.task.persist-refresh/job-init!
     beesbuddy.task.persist-refresh/refresh-tables!
     beesbuddy.task.persist-refresh/schedule-persistence-for-database!
     beesbuddy.task/delete-task!
     beesbuddy.test.data.bigquery-cloud-sdk/execute!
     beesbuddy.test.data.impl/copy-db-tables-and-fields!
     beesbuddy.test.data.impl/get-or-create-database!
     beesbuddy.test.data.impl/get-or-create-default-dataset!
     beesbuddy.test.data.impl.get-or-create/set-test-db-permissions!
     beesbuddy.test.data.interface/create-db!
     beesbuddy.test.data.interface/destroy-db!
     beesbuddy.test.data.oracle/create-user!
     beesbuddy.test.data.oracle/drop-user!
     beesbuddy.test.data.sql-jdbc.load-data/make-insert!
     beesbuddy.test.data.users/clear-cached-session-tokens!
     beesbuddy.test.initialize/do-initialization!
     beesbuddy.test.initialize/initialize-if-needed!
     beesbuddy.test.integrations.ldap/start-ldap-server!
     beesbuddy.test.util.log/ensure-unique-logger!
     beesbuddy.test.util.log/set-ns-log-level!
     beesbuddy.test.util/do-with-temp-env-var-value!
     beesbuddy.test.util/restore-raw-setting!
     beesbuddy.test.util/upsert-raw-setting!
     beesbuddy.test/initialize-if-needed!
     beesbuddy.test/test-helpers-set-global-values!
     beesbuddy.test/with-temp-env-var-value!
     beesbuddy.upload-test/set-local-infile!
     beesbuddy.util.files/create-dir-if-not-exists!
     beesbuddy.util.ssh-test/start-mock-servers!
     beesbuddy.util.ssh-test/stop-mock-servers!})

(defn- end-with-exclamation?
  [s]
  (str/ends-with? s "!"))

(defn- non-thread-safe-form-should-end-with-exclamation*
  [{[defn-or-defmacro form-name] :children, :as node}]
  (when-not (and (:string-value form-name)
                 (end-with-exclamation? (:string-value form-name)))
    (letfn [(walk [f form]
              (f form)
              (doseq [child (:children form)]
                (walk f child)))]
      (walk (fn [form]
              (when-let [qualified-symbol (node->qualified-symbol form)]
                (when (and (not (contains? symbols-allowed-in-fns-not-ending-in-an-exclamation-point qualified-symbol))
                           (end-with-exclamation? qualified-symbol))
                  (hooks/reg-finding! (assoc (meta form-name)
                                             :message (format "The name of this %s should end with `!` because it contains calls to non thread safe form `%s`."
                                                              (:string-value defn-or-defmacro) qualified-symbol)
                                             :type :beesbuddy/test-helpers-use-non-thread-safe-functions)))))
            node))
    node))

(defn non-thread-safe-form-should-end-with-exclamation
  "Used to ensure defn and defmacro in test namespace to have name ending with `!` if it's non-thread-safe.
  A function or a macro can be defined as 'not thread safe' when their funciton name ends with a `!`.

  Only used in tests to identify thread-safe/non-thread-safe test helpers. See #37126"
  [{:keys [node cljc lang]}]
  (when (or (not cljc)
            (= lang :clj))
    (non-thread-safe-form-should-end-with-exclamation* node))
  {:node node})

(comment
 (require '[clj-kondo.core :as clj-kondo])
 (def form (str '(defmacro a
                   [x]
                   `(fun-call x))))

 (def form "(defmacro a
           [x]
           `(some! ~x))")

 (def form "(defun f
           [x]
           (let [g! (fn [] 1)]
           (g!)))")

 (str (hooks/parse-string form))
 (hooks/sexpr (hooks/parse-string form))

 (binding [hooks/*reload* true]
   (-> form
       (with-in-str (clj-kondo/run! {:lint ["-"]}))
       :findings))

 (do (non-thread-safe-form-should-end-with-exclamation* (hooks/parse-string form)) nil))

(defn- ns-form-node->require-node [ns-form-node]
  (some (fn [node]
          (when (and (hooks/list-node? node)
                     (let [first-child (first (:children node))]
                       (and (hooks/keyword-node? first-child)
                            (= (hooks/sexpr first-child) :require))))
            node))
        (:children ns-form-node)))

(defn- lint-require-shapes [ns-form-node]
  (doseq [node (-> ns-form-node
                   ns-form-node->require-node
                   :children
                   rest)]
    (cond
      (not (hooks/vector-node? node))
      (hooks/reg-finding! (assoc (meta node)
                                 :message "All :required namespaces should be wrapped in vectors [:beesbuddy/require-shape-checker]"
                                 :type    :beesbuddy/require-shape-checker))

      (hooks/vector-node? (second (:children node)))
      (hooks/reg-finding! (assoc (meta node)
                                 :message "Don't use prefix forms inside :require [:beesbuddy/require-shape-checker]"
                                 :type    :beesbuddy/require-shape-checker)))))

(defn- require-node->namespace-symb-nodes [require-node]
  (let [[_ns & args] (:children require-node)]
    (into []
          ;; prefixed namespace forms are NOT SUPPORTED!!!!!!!!1
          (keep (fn [node]
                  (cond
                    (hooks/vector-node? node)
                    ;; propagate the metadata attached to this vector in case there's a `:clj-kondo/ignore` form.
                    (vary-meta (first (:children node)) (partial merge (meta require-node) (meta node)))

                    ;; this should also be dead code since we require requires to be vectors
                    (hooks/token-node? node)
                    (vary-meta node (partial merge (meta require-node)))

                    :else
                    (printf "Don't know how to figure out what namespace is being required in %s\n" (pr-str node)))))
          args)))

(defn- ns-form-node->ns-symb [ns-form-node]
  (some-> (some (fn [node]
                  (when (and (hooks/token-node? node)
                             (not= (hooks/sexpr node) 'ns))
                    node))
                (:children ns-form-node))
          hooks/sexpr))

(defn- module
  "E.g.

    (module 'beesbuddy.qp.middleware.wow) => 'beesbuddy.qp"
  [ns-symb]
  (some-> (re-find #"^beesbuddy\.[^.]+" (str ns-symb)) symbol))

(defn- ignored-namespace? [ns-symb config]
  (some
   (fn [pattern-str]
     (re-find (re-pattern pattern-str) (str ns-symb)))
   (:ignored-namespace-patterns config)))

(defn- module-api-namespaces
  "Set API namespaces for a given module. `:any` means you can use anything, there are no API namespaces for this
  module (yet). If unspecified, the default is just the namespace with the same name as the module e.g.
  `beesbuddy.db`."
  [module config]
  (let [module-config (get-in config [:api-namespaces module])]
    (cond
      (= module-config :any)
      nil

      (set? module-config)
      module-config

      :else
      #{module})))

(defn- lint-modules [ns-form-node config]
  (let [ns-symb (ns-form-node->ns-symb ns-form-node)]
    (when-not (ignored-namespace? ns-symb config)
      (when-let [current-module (module ns-symb)]
        (let [allowed-modules               (get-in config [:allowed-modules current-module])
              required-namespace-symb-nodes (-> ns-form-node
                                                ns-form-node->require-node
                                                require-node->namespace-symb-nodes)]
          (doseq [node  required-namespace-symb-nodes
                  :let  [clj-kondo-ignore (some-> (meta node) :clj-kondo/ignore hooks/sexpr set)]
                  :when (not (contains? clj-kondo-ignore :beesbuddy/ns-module-checker))
                  :let  [required-namespace (hooks/sexpr node)
                         required-module    (module required-namespace)]
                  ;; ignore stuff not in a module i.e. non-Metabase stuff.
                  :when required-module
                  :let  [in-current-module? (= required-module current-module)]
                  :when (not in-current-module?)
                  :let  [allowed-module?           (or (= allowed-modules :any)
                                                       (contains? (set allowed-modules) required-module))
                         module-api-namespaces     (module-api-namespaces required-module config)
                         allowed-module-namespace? (or (empty? module-api-namespaces)
                                                       (contains? module-api-namespaces required-namespace))]]
            (when-let [error (cond
                               (not allowed-module?)
                               (format "Module %s should not be used in the %s module. [:beesbuddy/ns-module-checker :allowed-modules %s]"
                                       required-module
                                       current-module
                                       current-module)

                               (not allowed-module-namespace?)
                               (format "Namespace %s is not an allowed external API namespace for the %s module. [:beesbuddy/ns-module-checker :api-namespaces %s]"
                                       required-namespace
                                       required-module
                                       required-module))]
              (hooks/reg-finding! (assoc (meta node)
                                         :message error
                                         :type    :beesbuddy/ns-module-checker)))))))))

(defn lint-ns [x]
  (lint-require-shapes (:node x))
  (lint-modules (:node x) (get-in x [:config :linters :beesbuddy/ns-module-checker]))
  x)
