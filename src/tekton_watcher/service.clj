(ns tekton-watcher.service
  (:require [clojure.tools.logging :as log]
            [tekton-watcher.api :as api]
            [tekton-watcher.pulls :as pulls]
            [tekton-watcher.runs :as runs]))

(def publishers
  [runs/watch-running-tasks
   runs/watch-completed-tasks])

(def subscribers
  [runs/run-started
   runs/run-succeeded
   pulls/run-started
   pulls/run-succeeded])

(defn read-config
  []
  {:cluster/host       "http://localhost:8080"
   :cluster/namespace  "default"
   :github/oauth-token (slurp (str (System/getProperty "user.home") "/.github-token"))})

(defn -main
  [& _]
  (log/info "Starting tekton-watcher...")
  (api/start-messaging publishers subscribers (read-config))
  (log/info "tekton-watcher started")
  (.. Thread currentThread join))