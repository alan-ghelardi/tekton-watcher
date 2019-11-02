#!/usr/bin/env bash
set -euo pipefail

java -cp ${TW_HOME}/tekton-watcher.jar clojure.main -m tekton-watcher.service
