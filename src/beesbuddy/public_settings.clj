(ns beesbuddy.public-settings
  (:require ;;  [beesbuddy.models.interface :as mi]
 ;;  [beesbuddy.models.setting :as setting :refer [defsetting]]
            [beesbuddy.config :as config] ;;  [beesbuddy.models.interface :as mi]
            ;; [beesbuddy.models.setting :as setting]
            [beesbuddy.plugins.classloader :as classloader] ;;  [beesbuddy.public-settings.premium-features :as premium-features]
            [beesbuddy.util :as u] ;;  [beesbuddy.util.fonts :as u.fonts]
            [beesbuddy.util.i18n
    :as i18n
    :refer [tru]] ;;  [beesbuddy.util.password :as u.password]
            [clojure.string :as str] ;;  [beesbuddy.api.common :as api]
))

(set! *warn-on-reflection* true)

;; These modules register settings but are otherwise unused. They still must be imported.
(comment premium-features/keep-me)

;; (defsetting application-name
;;   (deferred-tru "Replace the word “beesbuddy” wherever it appears.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    "beesbuddy")

(defn application-name-for-setting-descriptions
  "Returns the value of the [[application-name]] setting so setting docstrings can be generated during the compilation stage.
   Use this instead of `application-name` in descriptions, otherwise the `application-name` setting's
   `:enabled?` function will be called during compilation, which will fail because it will attempt to perform i18n, which is
   not allowed during compilation."
  []
  "beesbuddy")
  ;; (if *compile-files*
  ;;   "beesbuddy"
  ;;   (binding [config/*disable-setting-cache* true]
  ;;     (application-name))))

(defn- google-auth-enabled? []
  false)

(defn- ldap-enabled? []
  false)
  ;; (classloader/require 'beesbuddy.api.ldap)
  ;; ((resolve 'beesbuddy.api.ldap/ldap-enabled)))

(defn- ee-sso-configured? []
  (when-let [varr (resolve 'beesbuddy-enterprise.sso.integrations.sso-settings/other-sso-enabled?)]
    (varr)))

(defn sso-enabled?
  "Any SSO provider is configured and enabled"
  []
  (or (google-auth-enabled?)
      (ldap-enabled?)
      (ee-sso-configured?)))

;; (defsetting check-for-updates
;;   (deferred-tru "Identify when new versions of beesbuddy are available.")
;;   :type    :boolean
;;   :audit   :getter
;;   :default true)

;; (defsetting version-info
;;   (deferred-tru "Information about available versions of beesbuddy.")
;;   :type    :json
;;   :audit   :never
;;   :default {}
;;   :doc     false)

;; (defsetting version-info-last-checked
;;   (deferred-tru "Indicates when beesbuddy last checked for new versions.")
;;   :visibility :public
;;   :type       :timestamp
;;   :audit      :never
;;   :default    nil
;;   :doc        false)

;; (defsetting startup-time-millis
;;   (deferred-tru "The startup time in milliseconds")
;;   :visibility :public
;;   :type       :double
;;   :audit      :never
;;   :default    0.0
;;   :doc        false)

;; (defsetting site-name
;;   (deferred-tru "The name used for this instance of {0}."
;;     (application-name-for-setting-descriptions))
;;   :default    "beesbuddy"
;;   :audit      :getter
;;   :visibility :settings-manager
;;   :export?    true)

;; (defsetting custom-homepage
;;   (deferred-tru "Pick one of your dashboards to serve as homepage. Users without dashboard access will be directed to the default homepage.")
;;   :default    false
;;   :type       :boolean
;;   :audit      :getter
;;   :visibility :public)

;; (defsetting custom-homepage-dashboard
;;   (deferred-tru "ID of dashboard to use as a homepage")
;;   :type       :integer
;;   :visibility :public
;;   :audit      :getter)

;; (defsetting site-uuid
;;   ;; Don't i18n this docstring because it's not user-facing! :)
;;   "Unique identifier used for this instance of {0}. This is set once and only once the first time it is fetched via
;;   its magic getter. Nice!"
;;   :visibility :authenticated
;;   :base       setting/uuid-nonce-base
;;   :doc        false)

;; (defsetting site-uuid-for-premium-features-token-checks
;;   "In the interest of respecting everyone's privacy and keeping things as anonymous as possible we have a *different*
;;   site-wide UUID that we use for the EE/premium features token feature check API calls. It works in fundamentally the
;;   same way as [[site-uuid]] but should only be used by the token check logic
;;   in [[beesbuddy.public-settings.premium-features/fetch-token-status]]. (`site-uuid` is used for anonymous
;;   analytics/stats and if we sent it along with the premium features token check API request it would no longer be
;;   anonymous.)"
;;   :visibility :internal
;;   :base       setting/uuid-nonce-base
;;   :doc        false)

;; (defsetting site-uuid-for-version-info-fetching
;;   "A *different* site-wide UUID that we use for the version info fetching API calls. Do not use this for any other
;;   applications. (See [[site-uuid-for-premium-features-token-checks]] for more reasoning.)"
;;   :visibility :internal
;;   :base       setting/uuid-nonce-base)

;; (defsetting site-uuid-for-unsubscribing-url
;;   "UUID that we use for generating urls users to unsubscribe from alerts. The hash is generated by
;;   hash(secret_uuid + email + subscription_id) = url. Do not use this for any other applications. (See #29955)"
;;   :visibility :internal
;;   :base       setting/uuid-nonce-base)

(defn- normalize-site-url [^String s]
  (let [;; remove trailing slashes
        s (str/replace s #"/$" "")
        ;; add protocol if missing
        s (if (str/starts-with? s "http")
            s
            (str "http://" s))]
    ;; check that the URL is valid
    (when-not (u/url? s)
      (throw (ex-info (tru "Invalid site URL: {0}" (pr-str s)) {:url (pr-str s)})))
    s))

(declare redirect-all-requests-to-https!)

;; This value is *guaranteed* to never have a trailing slash :D
;; It will also prepend `http://` to the URL if there's no protocol when it comes in
;; (defsetting site-url
;;   (deferred-tru
;;     (str "This URL is used for things like creating links in emails, auth redirects, and in some embedding scenarios, "
;;          "so changing it could break functionality or get you locked out of this instance."))
;;   :visibility :public
;;   :audit      :getter
;;   :getter     (fn []
;;                 (try
;;                   (some-> (setting/get-value-of-type :string :site-url) normalize-site-url)
;;                   (catch clojure.lang.ExceptionInfo e
;;                     (log/error e "site-url is invalid; returning nil for now. Will be reset on next request."))))
;;   :setter     (fn [new-value]
;;                 (let [new-value (some-> new-value normalize-site-url)
;;                       https?    (some-> new-value (str/starts-with?  "https:"))]
;;                   ;; if the site URL isn't HTTPS then disable force HTTPS redirects if set
;;                   (when-not https?
;;                     (redirect-all-requests-to-https! false))
;;                   (setting/set-value-of-type! :string :site-url new-value)))
;;   :doc "This URL is critical for things like SSO authentication, email links, embedding and more.
;;         Even difference with `http://` vs `https://` can cause problems.
;;         Make sure that the address defined is how beesbuddy is being accessed.")

;; (defsetting site-locale
;;   (deferred-tru
;;     (str "The default language for all users across the {0} UI, system emails, pulses, and alerts. "
;;          "Users can individually override this default language from their own account settings.")
;;     (application-name-for-setting-descriptions))
;;   :default    "en"
;;   :visibility :public
;;   :export?    true
;;   :audit      :getter
;;   :getter     (fn []
;;                 (let [value (setting/get-value-of-type :string :site-locale)]
;;                   (when (i18n/available-locale? value)
;;                     value)))
;;   :setter     (fn [new-value]
;;                 (when new-value
;;                   (when-not (i18n/available-locale? new-value)
;;                     (throw (ex-info (tru "Invalid locale {0}" (pr-str new-value)) {:status-code 400}))))
;;                 (setting/set-value-of-type! :string :site-locale (some-> new-value i18n/normalized-locale-string))))

;; (defsetting admin-email
;;   (deferred-tru "The email address users should be referred to if they encounter a problem.")
;;   :visibility :authenticated
;;   :audit      :getter)

;; (defsetting anon-tracking-enabled
;;   (deferred-tru "Enable the collection of anonymous usage data in order to help {0} improve."
;;     (application-name-for-setting-descriptions))
;;   :type       :boolean
;;   :default    true
;;   :visibility :public
;;   :audit      :getter)

;; (defsetting map-tile-server-url
;;   (deferred-tru "The map tile server URL template used in map visualizations, for example from OpenStreetMaps or MapBox.")
;;   :default    "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
;;   :visibility :public
;;   :audit      :getter)

;; (defn- coerce-to-relative-url
;;   "Get the path of a given URL if the URL contains an origin.
;;    Otherwise make the landing-page a relative path."
;;   [landing-page]
;;   (cond
;;     (u/url? landing-page) (-> landing-page io/as-url .getPath)
;;     (empty? landing-page) ""
;;     (not (str/starts-with? landing-page "/")) (str "/" landing-page)
;;     :else landing-page))

;; (defsetting landing-page
;;   (deferred-tru "Enter a URL of the landing page to show the user. This overrides the custom homepage setting above.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :default    ""
;;   :audit      :getter
;;   :setter     (fn [new-landing-page]
;;                 (when new-landing-page
;;                   ;; If the landing page is a valid URL or mailto, sms, or file, then check with if site-url has the same origin.
;;                   (when (and (or (re-matches #"^(mailto|sms|file):(.*)" new-landing-page) (u/url? new-landing-page))
;;                              (not (str/starts-with? new-landing-page (site-url))))
;;                     (throw (ex-info (tru "This field must be a relative URL.") {:status-code 400}))))
;;                 (setting/set-value-of-type! :string :landing-page (coerce-to-relative-url new-landing-page))))

;; (defsetting enable-public-sharing
;;   (deferred-tru "Enable admins to create publicly viewable links (and embeddable iframes) for Questions and Dashboards?")
;;   :type       :boolean
;;   :default    true
;;   :visibility :authenticated
;;   :audit      :getter)

;; (defsetting enable-nested-queries
;;   (deferred-tru "Allow using a saved question or Model as the source for other queries?")
;;   :type       :boolean
;;   :default    true
;;   :visibility :authenticated
;;   :export?    true
;;   :audit      :getter)

;; (defsetting enable-query-caching
;;   (deferred-tru "Enabling caching will save the results of queries that take a long time to run.")
;;   :type       :boolean
;;   :default    false
;;   :visibility :authenticated
;;   :audit      :getter)

;; (defsetting persisted-models-enabled
;;   (deferred-tru "Allow persisting models into the source database.")
;;   :type       :boolean
;;   :default    false
;;   :visibility :public
;;   :export?    true
;;   :audit      :getter)

;; (defsetting persisted-model-refresh-cron-schedule
;;   (deferred-tru "cron syntax string to schedule refreshing persisted models.")
;;   :type       :string
;;   :default    "0 0 0/6 * * ? *"
;;   :visibility :admin
;;   :audit      :getter)

(def ^:private ^:const global-max-caching-kb
  "Although depending on the database, we can support much larger cached values (1GB for PG, 2GB for H2 and 4GB for
  MySQL) we are not curretly setup to deal with data of that size. The datatypes we are using will hold this data in
  memory and will not truly be streaming. This is a global max in order to prevent our users from setting the caching
  value so high it becomes a performance issue. The value below represents 200MB"
  (* 200 1024))

;; (defsetting query-caching-max-kb
;;   (deferred-tru "The maximum size of the cache, per saved question, in kilobytes:")
;;   ;; (This size is a measurement of the length of *uncompressed* serialized result *rows*. The actual size of
;;   ;; the results as stored will vary somewhat, since this measurement doesn't include metadata returned with the
;;   ;; results, and doesn't consider whether the results are compressed, as the `:db` backend does.)
;;   :type    :integer
;;   :default 2000
;;   :audit   :getter
;;   :setter  (fn [new-value]
;;              (when (and new-value
;;                         (> (cond-> new-value
;;                              (string? new-value) Integer/parseInt)
;;                            global-max-caching-kb))
;;                (throw (IllegalArgumentException.
;;                        (str
;;                         (tru "Failed setting `query-caching-max-kb` to {0}." new-value)
;;                         " "
;;                         (tru "Values greater than {0} ({1}) are not allowed."
;;                              global-max-caching-kb (u/format-bytes (* global-max-caching-kb 1024)))))))
;;              (setting/set-value-of-type! :integer :query-caching-max-kb new-value)))

;; (defsetting query-caching-max-ttl
;;   (deferred-tru "The absolute maximum time to keep any cached query results, in seconds.")
;;   :type    :double
;;   :default (* 60.0 60.0 24.0 35.0) ; 35 days
;;   :audit   :getter)

;; (defsetting notification-link-base-url
;;   (deferred-tru "By default \"Site Url\" is used in notification links, but can be overridden.")
;;   :visibility :internal
;;   :type       :string
;;   :feature    :whitelabel
;;   :audit      :getter)

;; (defsetting deprecation-notice-version
;;   (deferred-tru "beesbuddy version for which a notice about usage of deprecated features has been shown.")
;;   :visibility :admin
;;   :doc        false
;;   :audit      :never)

;; (defsetting loading-message
;;   (deferred-tru "Choose the message to show while a query is running.")
;;   :visibility :public
;;   :export?    true
;;   :feature    :whitelabel
;;   :type       :keyword
;;   :default    :doing-science
;;   :audit      :getter)

;; (defsetting application-colors
;;   (deferred-tru "Choose the colors used in the user interface throughout beesbuddy and others specifically for the charts. You need to refresh your browser to see your changes take effect.")
;;   :visibility :public
;;   :export?    true
;;   :type       :json
;;   :feature    :whitelabel
;;   :default    {}
;;   :audit      :getter
;;   :doc "To change the user interface colors:

;; ```
;; {
;;  \"brand\":\"#ff003b\",
;;  \"filter\":\"#FF003B\",
;;  \"summarize\":\"#FF003B\"
;; }
;; ```

;; To change the chart colors:

;; ```
;; {
;;  \"accent0\":\"#FF0005\",
;;  \"accent1\":\"#E6C367\",
;;  \"accent2\":\"#B9E68A\",
;;  \"accent3\":\"#8AE69F\",
;;  \"accent4\":\"#8AE6E4\",
;;  \"accent5\":\"#8AA2E6\",
;;  \"accent6\":\"#B68AE6\",
;;  \"accent7\":\"#E68AD0\"
;; }
;; ```")

;; (defsetting application-font
;;   (deferred-tru "Replace “Lato” as the font family.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :default    "Lato"
;;   :feature    :whitelabel
;;   :audit      :getter
;;   :setter     (fn [new-value]
;;                 (when new-value
;;                   (when-not (u.fonts/available-font? new-value)
;;                     (throw (ex-info (tru "Invalid font {0}" (pr-str new-value)) {:status-code 400}))))
;;                 (setting/set-value-of-type! :string :application-font new-value)))

;; (defsetting application-font-files
;;   (deferred-tru "Tell us where to find the file for each font weight. You don’t need to include all of them, but it’ll look better if you do.")
;;   :visibility :public
;;   :export?    true
;;   :type       :json
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :doc "Example value:

;; ```
;; [
;;   {
;;     \"src\": \"https://example.com/resources/font-400\",
;;     \"fontFormat\": \"ttf\",
;;     \"fontWeight\": 400
;;   },
;;   {
;;     \"src\": \"https://example.com/resources/font-700\",
;;     \"fontFormat\": \"woff\",
;;     \"fontWeight\": 700
;;   }
;; ]
;; ```

;; See [fonts](../configuring-beesbuddy/fonts.md).")

;; (defn application-color
;;   "The primary color, a.k.a. brand color"
;;   []
;;   (or (:brand (application-colors)) "#509EE3"))

;; (defn secondary-chart-color
;;   "The first 'Additional chart color'"
;;   []
;;   (or (:accent3 (application-colors)) "#EF8C8C"))

;; (defsetting application-logo-url
;;   (deferred-tru "Upload a file to replace the beesbuddy logo on the top bar.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    "app/assets/img/logo.svg"
;;   :doc "Inline styling and inline scripts are not supported.")

;; (defsetting application-favicon-url
;;   (deferred-tru "Upload a file to use as the favicon.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    "app/assets/img/favicon.ico")

;; (defsetting show-metabot
;;   (deferred-tru "Enables Metabot character on the home page")
;;   :visibility :public
;;   :export?    true
;;   :type       :boolean
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    true)

;; (defsetting login-page-illustration
;;   (deferred-tru "Options for displaying the illustration on the login page.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    "default")

;; (defsetting login-page-illustration-custom
;;   (deferred-tru "The custom illustration for the login page.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel)

;; (defsetting landing-page-illustration
;;   (deferred-tru "Options for displaying the illustration on the landing page.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    "default")

;; (defsetting landing-page-illustration-custom
;;   (deferred-tru "The custom illustration for the landing page.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel)

;; (defsetting no-data-illustration
;;   (deferred-tru "Options for displaying the illustration when there are no results after running a question.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    "default")

;; (defsetting no-data-illustration-custom
;;   (deferred-tru "The custom illustration for when there are no results after running a question.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel)

;; (defsetting no-object-illustration
;;   (deferred-tru "Options for displaying the illustration when there are no results after searching.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :default    "default")

;; (defsetting no-object-illustration-custom
;;   (deferred-tru "The custom illustration for when there are no results after searching.")
;;   :visibility :public
;;   :export?    true
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel)

;; (def ^:private help-link-options
;;   #{:beesbuddy :hidden :custom})

;; (defsetting help-link
;;   (deferred-tru
;;     (str
;;      "Keyword setting to control whitelabeling of the help link. Valid values are `:beesbuddy`, `:hidden`, and "
;;      "`:custom`. If `:custom` is set, the help link will use the URL specified in the `help-link-custom-destination`, "
;;      "or be hidden if it is not set."))
;;   :type       :keyword
;;   :audit      :getter
;;   :visibility :public
;;   :feature    :whitelabel
;;   :default    :beesbuddy
;;   :setter     (fn [value]
;;                 (when-not (help-link-options (keyword value))
;;                   (throw (ex-info (tru "Invalid help link option")
;;                                   {:value value
;;                                    :valid-options help-link-options})))
;;                 (setting/set-value-of-type! :keyword :help-link value)))

;; (defn- validate-help-url
;;   "Checks that the provided URL is either a valid HTTP/HTTPS URL or a `mailto:` link. Returns `nil` if the input is valid;
;;   throws an exception if it is not."
;;   [url]
;;   (let [validation-exception (ex-info (tru "Please make sure this is a valid URL")
;;                                       {:url url})]
;;     (if-let [matches (re-matches #"^mailto:(.*)" url)]
;;       (when-not (u/email? (second matches))
;;         (throw validation-exception))
;;       (when-not (u/url? url)
;;         (throw validation-exception)))))

;; (defsetting help-link-custom-destination
;;   (deferred-tru "Custom URL for the help link.")
;;   :visibility :public
;;   :type       :string
;;   :audit      :getter
;;   :feature    :whitelabel
;;   :setter     (fn [new-value]
;;                 (let [new-value-string (str new-value)]
;;                   (validate-help-url new-value-string)
;;                   (setting/set-value-of-type! :string :help-link-custom-destination new-value-string))))

;; (defsetting show-beesbuddy-links
;;   (deferred-tru (str "Whether or not to display beesbuddy links outside admin settings."))
;;   :type       :boolean
;;   :default    true
;;   :visibility :public
;;   :audit      :getter
;;   :feature    :whitelabel)

;; (defsetting enable-password-login
;;   (deferred-tru "Allow logging in by email and password.")
;;   :visibility :public
;;   :type       :boolean
;;   :default    true
;;   :feature    :disable-password-login
;;   :audit      :raw-value
;;   :getter     (fn []
;;                 ;; if `:enable-password-login` has an *explict* (non-default) value, and SSO is configured, use that;
;;                 ;; otherwise this always returns true.
;;                 (let [v (setting/get-value-of-type :boolean :enable-password-login)]
;;                   (if (and (some? v)
;;                            (sso-enabled?))
;;                     v
;;                     true))))

;; (defsetting breakout-bins-num
;;   (deferred-tru
;;     (str "When using the default binning strategy and a number of bins is not provided, "
;;          "this number will be used as the default."))
;;   :type    :integer
;;   :export? true
;;   :default 8
;;   :audit   :getter)

;; (defsetting breakout-bin-width
;;   (deferred-tru
;;     (str "When using the default binning strategy for a field of type Coordinate (such as Latitude and Longitude), "
;;          "this number will be used as the default bin width (in degrees)."))
;;   :type    :double
;;   :default 10.0
;;   :audit   :getter)

;; (defsetting custom-formatting
;;   (deferred-tru "Object keyed by type, containing formatting settings")
;;   :type       :json
;;   :export?    true
;;   :default    {}
;;   :visibility :public
;;   :audit      :getter)

;; (defsetting enable-xrays
;;   (deferred-tru "Allow users to explore data using X-rays")
;;   :type       :boolean
;;   :default    true
;;   :visibility :authenticated
;;   :export?    true
;;   :audit      :getter)

;; (defsetting show-homepage-data
;;   (deferred-tru
;;     (str "Whether or not to display data on the homepage. "
;;          "Admins might turn this off in order to direct users to better content than raw data"))
;;   :type       :boolean
;;   :default    true
;;   :visibility :authenticated
;;   :export?    true
;;   :audit      :getter)

;; (defsetting show-homepage-xrays
;;   (deferred-tru
;;     (str "Whether or not to display x-ray suggestions on the homepage. They will also be hidden if any dashboards are "
;;          "pinned. Admins might hide this to direct users to better content than raw data"))
;;   :type       :boolean
;;   :default    true
;;   :visibility :authenticated
;;   :export?    true
;;   :audit      :getter)

;; (defsetting show-homepage-pin-message
;;   (deferred-tru
;;     (str "Whether or not to display a message about pinning dashboards. It will also be hidden if any dashboards are "
;;          "pinned. Admins might hide this to direct users to better content than raw data"))
;;   :type       :boolean
;;   :default    true
;;   :visibility :authenticated
;;   :export?    true
;;   :doc        false
;;   :audit      :getter)

;; (defsetting source-address-header
;;   (deferred-tru "Identify the source of HTTP requests by this header's value, instead of its remote address.")
;;   :default "X-Forwarded-For"
;;   :export? true
;;   :audit   :getter
;;   :getter  (fn [] (some-> (setting/get-value-of-type :string :source-address-header)
;;                           u/lower-case-en)))

;; (defn remove-public-uuid-if-public-sharing-is-disabled
;;   "If public sharing is *disabled* and `object` has a `:public_uuid`, remove it so people don't try to use it (since it
;;   won't work). Intended for use as part of a `post-select` implementation for Cards and Dashboards."
;;   [object]
;;   (if (and (:public_uuid object)
;;            (not (enable-public-sharing)))
;;     (assoc object :public_uuid nil)
;;     object))

;; (defsetting available-fonts
;;   "Available fonts"
;;   :visibility :public
;;   :export?    true
;;   :setter     :none
;;   :getter     u.fonts/available-fonts
;;   :doc        false)

;; (defsetting available-locales
;;   "Available i18n locales"
;;   :visibility :public
;;   :export?    true
;;   :setter     :none
;;   :getter     available-locales-with-names
;;   :doc        false)

;; (defsetting available-timezones
;;   "Available report timezone options"
;;   :visibility :public
;;   :export?    true
;;   :setter     :none
;;   :getter     (comp sort t/available-zone-ids)
;;   :doc        false)

;; (defsetting has-sample-database?
;;   "Whether this instance has a Sample Database database"
;;   :visibility :authenticated
;;   :setter     :none
;;   :getter     (fn [] (t2/exists? :model/Database, :is_sample true))
;;   :doc        false)

;; (defsetting password-complexity
;;   "Current password complexity requirements"
;;   :visibility :public
;;   :setter     :none
;;   :getter     u.password/active-password-complexity)

;; (defsetting session-cookies
;;   (deferred-tru "When set, enforces the use of session cookies for all users which expire when the browser is closed.")
;;   :type       :boolean
;;   :visibility :public
;;   :default    nil
;;   :audit      :getter)

;; (defsetting version
;;   "beesbuddy's version info"
;;   :visibility :public
;;   :setter     :none
;;   :getter     (constantly config/mb-version-info)
;;   :doc        false)

;; (defsetting token-features
;;   "Features registered for this instance's token"
;;   :visibility :public
;;   :setter     :none
;;   :getter     (fn [] {:advanced_permissions           (premium-features/enable-advanced-permissions?)
;;                       :audit_app                      (premium-features/enable-audit-app?)
;;                       :cache_granular_controls        (premium-features/enable-cache-granular-controls?)
;;                       :config_text_file               (premium-features/enable-config-text-file?)
;;                       :content_verification           (premium-features/enable-content-verification?)
;;                       :dashboard_subscription_filters (premium-features/enable-dashboard-subscription-filters?)
;;                       :disable_password_login         (premium-features/can-disable-password-login?)
;;                       :email_allow_list               (premium-features/enable-email-allow-list?)
;;                       :email_restrict_recipients      (premium-features/enable-email-restrict-recipients?)
;;                       :embedding                      (premium-features/hide-embed-branding?)
;;                       :hosting                        (premium-features/is-hosted?)
;;                       :official_collections           (premium-features/enable-official-collections?)
;;                       :sandboxes                      (premium-features/enable-sandboxes?)
;;                       :session_timeout_config         (premium-features/enable-session-timeout-config?)
;;                       :snippet_collections            (premium-features/enable-snippet-collections?)
;;                       :sso_google                     (premium-features/enable-sso-google?)
;;                       :sso_jwt                        (premium-features/enable-sso-jwt?)
;;                       :sso_ldap                       (premium-features/enable-sso-ldap?)
;;                       :sso_saml                       (premium-features/enable-sso-saml?)
;;                       :upload_management              (premium-features/enable-upload-management?)
;;                       :whitelabel                     (premium-features/enable-whitelabeling?)
;;                       :llm_autodescription            (premium-features/enable-llm-autodescription?)})
;;   :doc        false)

;; (defsetting redirect-all-requests-to-https
;;   (deferred-tru "Force all traffic to use HTTPS via a redirect, if the site URL is HTTPS")
;;   :visibility :public
;;   :type       :boolean
;;   :default    false
;;   :audit      :getter
;;   :setter     (fn [new-value]
;;                 ;; if we're trying to enable this setting, make sure `site-url` is actually an HTTPS URL.
;;                 (when (if (string? new-value)
;;                         (setting/string->boolean new-value)
;;                         new-value)
;;                   (assert (some-> (site-url) (str/starts-with? "https:"))
;;                           (tru "Cannot redirect requests to HTTPS unless `site-url` is HTTPS.")))
;;                 (setting/set-value-of-type! :boolean :redirect-all-requests-to-https new-value)))

;; (defsetting start-of-week
;;   (deferred-tru
;;     (str "This will affect things like grouping by week or filtering in GUI queries. "
;;          "It won''t affect most SQL queries, "
;;          "although it is used to set the WEEK_START session variable in Snowflake."))
;;   :visibility :public
;;   :export?    true
;;   :type       :keyword
;;   :default    :sunday
;;   :audit      :raw-value
;;   :getter     (fn []
;;                 ;; if something invalid is somehow in the DB just fall back to Sunday
;;                 (when-let [value (setting/get-value-of-type :keyword :start-of-week)]
;;                   (if (#{:monday :tuesday :wednesday :thursday :friday :saturday :sunday} value)
;;                     value
;;                     :sunday)))
;;   :setter      (fn [new-value]
;;                  (when new-value
;;                    (assert (#{:monday :tuesday :wednesday :thursday :friday :saturday :sunday} (keyword new-value))
;;                            (trs "Invalid day of week: {0}" (pr-str new-value))))
;;                  (setting/set-value-of-type! :keyword :start-of-week new-value)))

;; (defsetting cloud-gateway-ips
;;   (deferred-tru "beesbuddy Cloud gateway IP addresses, to configure connections to DBs behind firewalls")
;;   :visibility :public
;;   :type       :string
;;   :setter     :none
;;   :getter (fn []
;;             (when (premium-features/is-hosted?)
;;               (some-> (setting/get-value-of-type :string :cloud-gateway-ips)
;;                       (str/split #",")))))

;; (defsetting show-database-syncing-modal
;;   (deferred-tru
;;     (str "Whether an introductory modal should be shown after the next database connection is added. "
;;          "Defaults to false if any non-default database has already finished syncing for this instance."))
;;   :visibility :admin
;;   :type       :boolean
;;   :audit      :never
;;   :getter     (fn []
;;                 (let [v (setting/get-value-of-type :boolean :show-database-syncing-modal)]
;;                   (if (nil? v)
;;                     (not (t2/exists? :model/Database
;;                                      :is_sample false
;;                                      :is_audit false
;;                                      :initial_sync_status "complete"))
;;                     ;; frontend should set this value to `true` after the modal has been shown once
;;                     v))))

;; (defsetting uploads-enabled
;;   (deferred-tru "Whether or not uploads are enabled")
;;   :visibility :authenticated
;;   :export?    true
;;   :type       :boolean
;;   :audit      :getter
;;   :default    false)

;; (defn- not-handling-api-request?
;;   []
;;   (nil? @api/*current-user*))

;; (defn set-uploads-database-id!
;;   "Sets the :uploads-database-id setting, with an appropriate permission check."
;;   [new-id]
;;   (if (or (not-handling-api-request?)
;;           (mi/can-write? :model/Database new-id))
;;     (setting/set-value-of-type! :integer :uploads-database-id new-id)
;;     (api/throw-403)))

;; (defsetting uploads-database-id
;;   (deferred-tru "Database ID for uploads")
;;   :visibility :authenticated
;;   :export?    true
;;   :type       :integer
;;   :audit      :getter
;;   :setter     set-uploads-database-id!)

;; (defsetting uploads-schema-name
;;   (deferred-tru "Schema name for uploads")
;;   :visibility :authenticated
;;   :export?    true
;;   :type       :string
;;   :audit      :getter)

;; (defsetting uploads-table-prefix
;;   (deferred-tru "Prefix for upload table names")
;;   :visibility :authenticated
;;   :type       :string
;;   :audit      :getter)

;; (defsetting attachment-table-row-limit
;;   (deferred-tru "Maximum number of rows to render in an alert or subscription image.")
;;   :visibility :internal
;;   :type       :positive-integer
;;   :default    20
;;   :audit      :getter
;;   :getter     (fn []
;;                 (let [value (setting/get-value-of-type :positive-integer :attachment-table-row-limit)]
;;                   (if-not (pos-int? value)
;;                     20
;;                     value))))

;; ;; This is used by the embedding homepage
;; (defsetting example-dashboard-id
;;   (deferred-tru "The ID of the example dashboard.")
;;   :visibility :authenticated
;;   :export?    false
;;   :type       :integer
;;   :setter     :none
;;   :getter     (fn []
;;                 (let [id (setting/get-value-of-type :integer :example-dashboard-id)]
;;                   (when (and id (t2/exists? :model/Dashboard :id id :archived false))
;;                     id)))
;;   :doc        false)

;; (defsetting sql-parsing-enabled
;;   (deferred-tru "SQL Parsing is disabled")
;;   :visibility :internal
;;   :export?    false
;;   :default    true
;;   :type       :boolean)