(ns beesbuddy.core.initialization-status
  "Code related to tracking the progress of beesbuddy initialization.
   This is kept in a separate, tiny namespace so it can be loaded right away when the application launches
   (and so we don't need to wait for `beesbuddy.core` to load to check the status).")

(defonce ^:private progress-atom
  (atom 0))

(defn complete?
  "Is Beesbuddy initialized and ready to be served?"
  []
  (= @progress-atom 1.0))

(defn progress
  "Get the current progress of Beesbuddy initialization."
  []
  @progress-atom)

(defn set-progress!
  "Update the Beesbuddy initialization progress to a new value, a floating-point value between `0` and `1`."
  [^Float new-progress]
  {:pre [(float? new-progress) (<= 0.0 new-progress 1.0)]}
  (reset! progress-atom new-progress))

(defn set-complete!
  "Complete the Beesbuddy initialization by setting its progress to 100%."
  []
  (set-progress! 1.0))
