(ns tekton-watcher.scratch
  (:require [tekton-watcher.main :as main]
            [tekton-watcher.runs :as runs]
            [tekton-watcher.pulls :as pulls]))

#_(def c  (main/get-context))

#_(def t (runs/get-succeeded-tasks c))

#_(pulls/update-commit-status-of-all-runs c [t] #:status{:state "success" :description "hello"})
