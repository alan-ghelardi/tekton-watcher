(ns tekton-watcher.runs
  (:require [tekton-watcher.api :refer [defpub defsub]]
            [tekton-watcher.http-client :as http-client]))

(defn- contains-reason?
  [run reason]
  (->> run
       :status
       :conditions
       (some #(= reason (:reason %)))))

(defn- list-runs
  [{:cluster/keys [host namespace]} label-selectors reason]
  (->> (http-client/send-and-await #:http{:url          "{host}/apis/tekton.dev/v1alpha1/namespaces/{namespace}/taskruns"
                                          :path-params  {:host      host
                                                         :namespace namespace}
                                          :query-params {:labelSelector label-selectors}})
       :items
       (filter #(contains-reason? % reason))))

(defn get-running-tasks
  [config]
  (list-runs config "!tekton-watcher/running-event-fired,!tekton-watcher/completed-event-fired" "Running"))

(defn get-succeeded-tasks
  [config]
  (list-runs config "tekton-watcher/running-event-fired,!tekton-watcher/completed-event-fired" "Succeeded"))

(defn- add-label
  [{:cluster/keys [host]} run label-key]
  (http-client/send-async #:http{:verb        :patch
                                 :url         "{host}{link}"
                                 :produces    "application/json-patch+json"
                                 :path-params {:host host
                                               :link (get-in run [:metadata :selfLink])}
                                 :payload     [{:op    "add"
                                                :path  (str "/metadata/labels/tekton-watcher~1" label-key)
                                                :value "true"}]}))

(defpub watch-running-tasks
  #{:task-run/running}
  [config]
  (->> config
       get-running-tasks
       (map #(assoc {:message/topic :task-run/running}
                    :message/resource %))))

(defpub watch-completed-tasks
  #{:task-run/succeeded :task-run/failed}
  [config]
  (->> config
       get-succeeded-tasks
       (map #(assoc {:message/topic :task-run/succeeded}
                    :message/resource %))))

(defsub run-started :task-run/running
  [task-run config]
  (add-label config task-run "running-event-fired"))

(defsub run-succeeded :task-run/succeeded
  [task-run config]
  (add-label config task-run "completed-event-fired"))
