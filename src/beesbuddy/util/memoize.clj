(ns beesbuddy.util.memoize
  (:require
   [clojure.core.memoize :as memoize]
   [beesbuddy.shared.util.namespaces :as shared.ns]))

(comment
  memoize/keep-me)

(shared.ns/import-fns
 [memoize
  lru
  memoizer])
