(ns macros.beesbuddy.query-processor.streaming)

(defmacro streaming-response [[x y z] & body]
  `(let [~x [~y ~z]]
     ~@body))
