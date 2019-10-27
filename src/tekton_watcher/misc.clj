(ns tekton-watcher.misc
  (:require [clojure.core.async :as async :refer [<! >! go-loop]]
            [clojure.tools.logging :as log]))

(def ^:private digits-and-letters
  (keep (comp #(when (Character/isLetterOrDigit %)
                 %) char) (range 48 123)))

(defn correlation-id
  "Returns a random string composed of numbers ([0-9]) and
  letters ([a-zA-Z]) to be used as a correlation id."
  []
  (apply str (repeatedly 7 #(rand-nth digits-and-letters))))

(defn handle-incoming-messages
  [handler subscriber-name config in-channel out-channel]
  (go-loop []
    (let [{:message/keys [cid kind resource]
           :or           {cid "default"}
           :as           message} (<! in-channel)
          resource-name           (get-in resource [:metadata :name])]
      (try
        (log/info :in-message :subscriber subscriber-name :cid cid :topic kind :resource-name resource-name)
        (>! out-channel (handler message config))
        (catch Throwable t
          (log/error t :in-message-error :subscriber subscriber-name :cid cid :topic kind :resource-name resource-name)))
      (recur))))

(defn subscriber
  [publisher subscriber-name topic]
  (let [channel (async/chan)]
    (log/info :starting-subscriber :subscriber-name subscriber-name :topic topic)
    (async/sub publisher topic channel)
    channel))

(defn subscriber-name
  [ns def-name]
  (keyword (str ns "/" def-name)))

(defmacro defsub
  [name topic args & body]
  (let [def-name (str name)]
    `(defn ~name
       [publisher# config# out-channel#]
       (let [subscriber-name# (subscriber-name *ns* ~def-name)
             in-channel#      (subscriber publisher# subscriber-name# ~topic)]
         (handle-incoming-messages (fn ~args ~@body)
                                   subscriber-name# config# in-channel# out-channel#)))))
