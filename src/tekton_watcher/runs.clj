(ns tekton-watcher.runs
  (:require [clojure.core.async :as async :refer [<! >! go-loop]]
            [tekton-watcher.http-client :as http-client]))

(defn- contains-reason?
  [run reason]
  (->> run
       :status
       :conditions
       (some #(= reason (:reason %)))))

(defn- list-runs
  [{:k8s/keys [host namespace]} label-selectors reason]
  (->> (http-client/send-and-await #:http{:url          "{host}/apis/tekton.dev/v1alpha1/namespaces/{namespace}/taskruns"
                                          :path-params  {:host      host
                                                         :namespace namespace}
                                          :query-params {:labelSelector label-selectors}})
       :items
       (filter #(contains-reason? % reason))))

(defn get-running-tasks
  [context]
  (list-runs context "!tekton-watcher/running-event-fired,!tekton-watcher/completed-event-fired" "Running"))

(defn get-succeeded-tasks
  [context]
  (list-runs context "tekton-watcher/running-event-fired,!tekton-watcher/completed-event-fired" "Succeeded"))

(defn- add-label
  [{:k8s/keys [host]} run label-key]
  (http-client/send-async #:http{:verb        :patch
                                 :url         "{host}{link}"
                                 :produces    "application/json-patch+json"
                                 :path-params {:host host
                                               :link (get-in run [:metadata :selfLink])}
                                 :payload     [{:op    "add"
                                                :path  (str "/metadata/labels/tekton-watcher~1" label-key)
                                                :value "true"}]}))

(defn watch-running-tasks
  [channel context]
  (go-loop []
    (let [task-runs (get-running-tasks context)]
      (when (seq task-runs)
        (doseq [task-run task-runs]
          (>! channel #:events{:kind      :task-runs/running
                               :resource task-run})))
      (<! (async/timeout 100))
      (recur))))

(defn watch-succeeded-tasks
  [channel context]
  (go-loop []
    (let [task-runs (get-succeeded-tasks context)]
      (when (seq task-runs)
        (doseq [task-run task-runs]
          (>! channel #:events{:kind      :task-runs/succeeded
                               :resource task-run})))
      (<! (async/timeout 100))
      (recur))))

(defn run-started
  [channel context]
  (go-loop []
    (let [{task-run :events/resource} (<! channel)]
      (add-label context task-run "running-event-fired")
      (recur))))

(defn run-succeeded
  [channel context]
  (go-loop []
    (let [{task-run :events/resource} (<! channel)]
      (add-label context task-run "completed-event-fired")
      (recur))))
