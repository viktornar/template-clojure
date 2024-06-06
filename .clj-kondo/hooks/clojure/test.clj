(ns hooks.clojure.test
  (:require
   [clj-kondo.hooks-api :as hooks]
   [clojure.string :as str]
   [hooks.common]))

(def ^:private disallowed-parallel-forms
  "Things you should not be allowed to use inside parallel tests. Besides these, anything ending in `!` not whitelisted
  in [[allowed-parallel-forms]] is not allowed."
  '#{clojure.core/alter-var-root
     clojure.core/with-redefs
     clojure.core/with-redefs-fn
     beesbuddy-enterprise.sandbox.test-util/with-gtaps!
     beesbuddy-enterprise.sandbox.test-util/with-gtaps-for-user!
     beesbuddy-enterprise.sandbox.test-util/with-user-attributes
     beesbuddy-enterprise.test/with-gtaps!
     beesbuddy-enterprise.test/with-gtaps-for-user!
     beesbuddy-enterprise.test/with-user-attributes
     beesbuddy.actions.test-util/with-actions
     beesbuddy.actions.test-util/with-actions-disabled
     beesbuddy.actions.test-util/with-actions-enabled
     beesbuddy.actions.test-util/with-actions-test-data
     beesbuddy.actions.test-util/with-actions-test-data-and-actions-enabled
     beesbuddy.actions.test-util/with-actions-test-data-tables
     beesbuddy.analytics.snowplow-test/with-fake-snowplow-collector
     beesbuddy.email-test/with-expected-messages
     beesbuddy.email-test/with-fake-inbox
     beesbuddy.test.data.users/with-group
     beesbuddy.test.data.users/with-group-for-user
     beesbuddy.test.persistence/with-persistence-enabled
     beesbuddy.test.util.log/with-log-level
     beesbuddy.test.util.log/with-log-messages-for-level
     beesbuddy.test.util.misc/with-single-admin-user
     beesbuddy.test.util/with-all-users-permission
     beesbuddy.test.util/with-discarded-collections-perms-changes
     beesbuddy.test.util/with-env-keys-renamed-by
     beesbuddy.test.util/with-locale
     beesbuddy.test.util/with-non-admin-groups-no-root-collection-for-namespace-perms
     beesbuddy.test.util/with-non-admin-groups-no-root-collection-perms
     beesbuddy.test.util/with-temp-env-var-value
     beesbuddy.test.util/with-temp-vals-in-db
     beesbuddy.test.util/with-temporary-raw-setting-values
     beesbuddy.test.util/with-temporary-setting-values
     beesbuddy.test.util/with-user-in-groups
     beesbuddy.test/with-actions
     beesbuddy.test/with-actions-disabled
     beesbuddy.test/with-actions-enabled
     beesbuddy.test/with-actions-test-data
     beesbuddy.test/with-actions-test-data-and-actions-enabled
     beesbuddy.test/with-actions-test-data-tables
     beesbuddy.test/with-all-users-permission
     beesbuddy.test/with-discarded-collections-perms-changes
     beesbuddy.test/with-env-keys-renamed-by
     beesbuddy.test/with-expected-messages
     beesbuddy.test/with-fake-inbox
     beesbuddy.test/with-group
     beesbuddy.test/with-group-for-user
     beesbuddy.test/with-locale
     beesbuddy.test/with-log-level
     beesbuddy.test/with-log-messages-for-level
     beesbuddy.test/with-non-admin-groups-no-root-collection-for-namespace-perms
     beesbuddy.test/with-non-admin-groups-no-root-collection-perms
     beesbuddy.test/with-persistence-enabled
     beesbuddy.test/with-single-admin-user
     beesbuddy.test/with-temp-env-var-value
     beesbuddy.test/with-temp-vals-in-db
     beesbuddy.test/with-temporary-raw-setting-values
     beesbuddy.test/with-temporary-setting-values
     beesbuddy.test/with-user-in-groups})

;;; TODO -- we should disallow `beesbuddy.test/user-http-request` with any method other than `:get`

(def ^:private allowed-parallel-forms
  "These fns are destructive, but are probably fine inside ^:parallel tests because it usually means you're doing
  something to an atom or something like that."
  '#{clojure.core/assoc!
     clojure.core/compare-and-set!
     clojure.core/conj!
     clojure.core/disj!
     clojure.core/dissoc!
     clojure.core/persistent!
     clojure.core/pop!
     clojure.core/reset!
     clojure.core/reset-vals!
     clojure.core/run!
     clojure.core/swap!
     clojure.core/swap-vals!
     clojure.core/volatile!
     clojure.core/vreset!
     clojure.core/vswap!
     clojure.core.async/<!
     clojure.core.async/<!!
     clojure.core.async/>!
     clojure.core.async/>!!
     clojure.core.async/alt!
     clojure.core.async/alt!!
     clojure.core.async/alts!
     clojure.core.async/alts!!
     clojure.core.async/close!
     clojure.core.async/ioc-alts!
     clojure.core.async/offer!
     clojure.core.async/onto-chan!
     clojure.core.async/onto-chan!!
     clojure.core.async/poll!
     clojure.core.async/put!
     clojure.core.async/take!
     clojure.core.async/to-chan!
     clojure.core.async/to-chan!!
     beesbuddy.driver.sql-jdbc.execute/execute-prepared-statement!
     beesbuddy.pulse/send-pulse!
     beesbuddy.query-processor.store/store-database!
     next.jdbc/execute!})

(defn- warn-about-disallowed-parallel-forms [form]
  (letfn [(error! [form message]
            (hooks/reg-finding! (assoc (meta form)
                                       :message message
                                       :type :beesbuddy/validate-deftest)))
          (f [form]
            (when-let [qualified-symbol (hooks.common/node->qualified-symbol form)]
              (cond
                (disallowed-parallel-forms qualified-symbol)
                (error! form (format "%s is not allowed inside a ^:parallel test or test fixture" qualified-symbol))

                (and (not (allowed-parallel-forms qualified-symbol))
                     (str/ends-with? (name qualified-symbol) "!"))
                (error! form (format "destructive functions like %s are not allowed inside a ^:parallel test or test fixture. If this should be allowed, add it to the whitelist in .clj-kondo/hooks/clojure/test.clj"
                                     qualified-symbol)))))
          (walk [form]
            (f form)
            (doseq [child (:children form)]
              (walk child)))]
    (walk form)))

(defn- deftest-check-parallel
  "1. Check if test is marked ^:parallel / ^:synchronized correctly
   2. Make sure disallowed forms are not used in ^:parallel tests"
  [{[_ test-name & body] :children, :as _node}]
  (let [test-metadata     (:meta test-name)
        metadata-sexprs   (map hooks/sexpr test-metadata)
        combined-metadata (transduce
                           (map (fn [x]
                                  (if (map? x)
                                    x
                                    {x true})))
                           (completing merge)
                           {}
                           metadata-sexprs)
        parallel?     (:parallel combined-metadata)
        synchronized? (:synchronized combined-metadata)]
    (when (and parallel? synchronized?)
      (hooks/reg-finding! (assoc (meta test-name)
                                 :message "Test should not be marked both ^:parallel and ^:synchronized"
                                 :type :beesbuddy/validate-deftest)))
    ;; only when the custom `:beesbuddy/deftest-not-marked-parallel` is enabled: complain if tests are not explicitly
    ;; marked `^:parallel` or `^:synchronized`. This is mostly to encourage people to mark everything `^:parallel` in
    ;; places like `beesbuddy.lib` tests unless there is a really good reason not to.
    (when-not (or parallel? synchronized?)
      (hooks/reg-finding!
       (assoc (meta test-name)
              :message "Test should be marked either ^:parallel or ^:synchronized"
              :type :beesbuddy/deftest-not-marked-parallel-or-synchronized)))
    (when parallel?
      (doseq [form body]
        (warn-about-disallowed-parallel-forms form)))))

(def ^:private number-of-lines-for-a-test-to-be-considered-horrifically-long
  200)

(defn- deftest-check-not-horrifically-long
  [node]
  (let [{:keys [row end-row]} (meta node)]
    (when (and row end-row)
      (let [num-lines (- end-row row)]
        (when (>= num-lines number-of-lines-for-a-test-to-be-considered-horrifically-long)
          (hooks/reg-finding! (assoc (meta node)
                                     :message (str (format "This test is horrifically long, it's %d lines! 😱 " num-lines)
                                                   "Do you really want to try to debug it if it fails? 💀 "
                                                   "Split it up into smaller tests! 🥰")
                                     :type :beesbuddy/i-like-making-cams-eyes-bleed-with-horrifically-long-tests)))))))

(defn deftest [{:keys [node cljc lang]}]
  ;; run [[deftest-check-parallel]] only once... if this is a `.cljc` file only run it for the `:clj` analysis, no point
  ;; in running it twice.
  (when (or (not cljc)
            (= lang :clj))
    (deftest-check-parallel node))
  (deftest-check-not-horrifically-long node)
  {:node node})

;;; this is a hacky way to determine whether these namespaces are required in the `ns` form or not... basically `:ns`
;;; will come back as `nil` if they are not.
(defn- approximately-equal-ns-required? []
  (= (:ns (hooks/resolve {:name 'beesbuddy.test-runner.assert-exprs.approximately-equal/=?-report}))
     'beesbuddy.test-runner.assert-exprs.approximately-equal))

(defn- malli-equals-ns-required? []
  (= (:ns (hooks/resolve {:name 'beesbuddy.test-runner.assert-exprs.malli-equals/malli=-report}))
     'beesbuddy.test-runner.assert-exprs.malli-equals))

(defn- warn-about-missing-test-expr-requires-in-cljs [{:keys [children], :as _is-node}]
  (let [[_is assertion-node] children]
    (when (hooks/list-node? assertion-node)
      (let [[assertion-symb-node] (:children assertion-node)]
        (when (hooks/token-node? assertion-symb-node)
          (let [assertion-symb-token (hooks/sexpr assertion-symb-node)
                warn!                (fn [ns-to-require]
                                       (hooks/reg-finding!
                                        (assoc (meta assertion-symb-node)
                                               :message (format "You must require %s to use %s in ClojureScript"
                                                                ns-to-require
                                                                assertion-symb-token)
                                               :type :beesbuddy/missing-test-expr-requires-in-cljs)))]
            (condp = assertion-symb-token
              '=?
              (when-not (approximately-equal-ns-required?)
                (warn! 'beesbuddy.test-runner.assert-exprs.approximately-equal))

              'malli=
              (when-not (malli-equals-ns-required?)
                (warn! 'beesbuddy.test-runner.assert-exprs.malli-equals))

              nil)))))))

(defn- warn-about-schema= [{[_is assertion-node] :children, :as _is-node}]
  (let [{[assertion-symb-node] :children} assertion-node]
    (when (and (hooks/token-node? assertion-symb-node)
               (= (hooks/sexpr assertion-symb-node) 'schema=))
      (hooks/reg-finding! (assoc (meta assertion-symb-node)
                                 :message "Use =? or malli= instead of schema="
                                 :type :beesbuddy/warn-about-schema=)))))

(defn is [{:keys [node lang]}]
  (when (= lang :cljs)
    (warn-about-missing-test-expr-requires-in-cljs node))
  (warn-about-schema= node)
  {:node node})

(defn use-fixtures [{:keys [node]}]
  (warn-about-disallowed-parallel-forms node)
  {:node node})
