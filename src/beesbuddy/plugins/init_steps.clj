(ns beesbuddy.plugins.init-steps
  "Logic for performing the `init-steps` listed in a beesbuddy plugin's manifest. For driver plugins that specify that we
  should `lazy-load`, these steps are lazily performed the first time non-trivial driver methods (such as connecting
  to a Database) are called; for all other beesbuddy plugins these are perfomed during launch.

  The entire list of possible init steps is below, as impls for the `do-init-step!` multimethod."
  (:require
   [beesbuddy.plugins.classloader :as classloader]
   [beesbuddy.plugins.jdbc-proxy :as jdbc-proxy]
   [beesbuddy.util :as u]
   [beesbuddy.util.log :as log]))

(defmulti ^:private do-init-step!
  "Perform a driver init step. Steps are listed in `init:` in the plugin manifest; impls for each step are found below
  by dispatching off the value of `step:` for each step. Other properties specified for that step are passed as a map."
  {:arglists '([m])}
  (comp keyword :step))

(defmethod do-init-step! :load-namespace [{nmspace :namespace}]
  (log/debug (u/format-color 'blue "Loading plugin namespace %s..." nmspace))
  (classloader/require (symbol nmspace)))

(defmethod do-init-step! :register-jdbc-driver [{class-name :class}]
  (jdbc-proxy/create-and-register-proxy-driver! class-name))

(defn do-init-steps!
  "Perform the initialization steps for a beesbuddy plugin as specified under `init:` in its plugin
  manifest (`beesbuddy-plugin.yaml`) by calling `do-init-step!` for each step."
  [init-steps]
  (doseq [step init-steps]
    (do-init-step! step)))
