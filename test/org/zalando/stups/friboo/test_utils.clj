(ns org.zalando.stups.friboo.test-utils)

(defn track
  "Adds a tuple on call for an action."
  ([a action]
   (fn [& all-args]
     (swap! a conj {:key  action
                    :args (into [] all-args)}))))