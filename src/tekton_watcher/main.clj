(ns tekton-watcher.main
  (:require [clojure.core.async :as async]
            [tekton-watcher.pulls :as pulls]
            [tekton-watcher.runs :as runs]))

(defn get-context
  []
  {:k8s/host           "http://localhost:8080"
   :k8s/namespace      "default"
   :github/oauth-token (slurp (str (System/getProperty "user.home") "/.github-token"))})

(defn -main
  [& _]
  (let [running-channel       (async/chan)
        completed-channel     (async/chan)
        context               (get-context)
        running-publication   (async/pub running-channel :events/kind)
        completed-publication (async/pub completed-channel :events/kind)
        o1                    (async/chan)
        o2                    (async/chan)
        o3                    (async/chan)
        o4                    (async/chan)]
    (async/sub running-publication :task-runs/running o1)
    (async/sub running-publication :task-runs/running o2)
    (async/sub completed-publication :task-runs/succeeded o3)
    (async/sub completed-publication :task-runs/succeeded o4)
    (runs/watch-running-tasks running-channel context)
    (runs/watch-succeeded-tasks completed-channel context)
    (runs/run-started o1 context)
    (pulls/run-started o2 context)
    (runs/run-succeeded o3 context)
    (pulls/run-succeeded o4 context)
    (println "Watcher started")))
