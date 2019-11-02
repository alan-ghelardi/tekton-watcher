(ns tekton-watcher.pulls
  (:require [tekton-watcher.api :refer [defsub]]
            [tekton-watcher.http-client :as http-client]))

(defn- get-sha
  [git-resource]
  (first (keep #(when (= "revision" (:name %))
                  (:value %))
               (get-in git-resource [:spec :params]))))

(defn- get-git-resource
  [{:cluster/keys [host namespace]} task-run]
  (->> task-run
       :spec
       :inputs
       :resources
       (map #(get-in % [:resourceRef :name]))
       (map #(http-client/send-and-await #:http{:url         "{host}/apis/tekton.dev/v1alpha1/namespaces/{namespace}/pipelineresources/{resource-name}"
                                                :path-params {:host          host
                                                              :namespace     namespace
                                                              :resource-name %}}))
       (filter #(get-in % [:metadata :annotations "tekton-watcher/statuses-url"]))
       first))

(defn update-commit-status
  [{:github/keys [oauth-token] :as config} task-run {:status/keys [state description]}]
  (let [task-name    (get-in task-run [:metadata :labels "tekton.dev/pipelineTask"])
        git-resource (get-git-resource config task-run)]
    (when git-resource
      (let [sha          (get-sha git-resource)
            statuses-url (get-in git-resource [:metadata :annotations "tekton-watcher/statuses-url"])]
        (http-client/send-async #:http{:verb        :post
                                       :url         statuses-url
                                       :oauth-token oauth-token
                                       :path-params {:sha sha}
                                       :payload     {:state       state
                                                     :context     (str "nubank/ci-cd: " task-name)
                                                     :description description}})))))

(defsub run-started :task-run/running
  [task-run config]
  (update-commit-status config task-run #:status{:state       "pending"
                                                 :description "Tekton is running your tests..."}))

(defsub run-succeeded :task-run/succeeded
  [task-run config]
  (update-commit-status config task-run #:status{:state       "success"
                                                 :description "your tests passed on Tekton!"}))
