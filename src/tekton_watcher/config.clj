(ns tekton-watcher.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [tekton-watcher.misc :as misc]))

(defn- file-exists?
  "Returns true if the file in question exists or false otherwise."
  [file]
  (.exists file))

(defn- read-edn
  "Reads data in EDN format from the file in question.

  Returns nil when the file doesn't exist."
  [file]
  (when (file-exists? file)
    (edn/read-string (slurp file))))

(defn read-resource
  "Reads config data from the resource in question."
  [^String file-name]
  (-> (str "tekton_watcher/" file-name)
      io/resource
      io/file
      read-edn))

(defn read-file
  [^String file-name]
  (read-edn (io/file file-name)))

(defn read-waterfall
  [& sources]
  (apply merge (map #(%) sources)))

(defn- read-github-oauth-token
  [{:github.oauth-token/keys [path] :as config}]
  (let [file (io/file path)]
    (if (file-exists? file)
      (assoc config :github/oauth-token (slurp file))
      (throw (ex-info "Github oauth token not found. Did you forget to create a secret named `github-statuses-updater`?"
                      {:path path})))))

(defn render-config
  [config]
  (misc/map-vals #(if-not (string? %)
                    %
                    (misc/render % config))
                 config))

(def resource (partial read-resource "config.edn"))

(def config-map (partial read-file "/etc/tekton-watcher/config.edn"))

(defn read-config
  []
  (-> (read-waterfall resource config-map)
      render-config
      read-github-oauth-token))
