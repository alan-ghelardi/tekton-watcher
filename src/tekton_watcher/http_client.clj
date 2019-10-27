(ns tekton-watcher.http-client
  (:require [clojure.core.async :as async :refer [<!!]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [org.httpkit.client :as httpkit-client])
  (:import java.net.URLEncoder))

(def http-defaults
  {:as               :stream
   :follow-redirects false
   :user-agent       "tekton-watcher"
   :keepalive        120000
   :timeout          30000})

(def ^:private default-mime-type "application/json; charset=utf-8")

(defn- log-request
  [{:keys [method url] :as request}]
  (log/info :sending-request :method method :url url)
  request)

(defn form-encoder
  "Turns a Clojure map into a string in the x-www-form-urlencoded
  format."
  [data]
  (string/join "&"
               (map (fn [[k v]]
                      (str (name k)
                           "=" (URLEncoder/encode (str v) "UTF-8")))
                    data)))

(def json-request-parser
  "JSON parser for request's bodies."
  #(json/write-str % :key-fn name))

(defn json-response-parser
  "JSON parser for response's bodies."
  [body]
  (json/read (io/reader body)
             :key-fn #(if (re-find #"[/\.]" %)
                        %
                        (keyword %))))

(defn- normalize-header-names
  "Given a map containing header names and their values, transforms all
  keys to downcased strings."
  [headers]
  (letfn [(downcase [[k v]]
            [(string/lower-case (name k)) v])]
    (walk/postwalk #(if-not (map-entry? %)
                      %
                      (downcase %)) headers)))

(defn handle-http-response
  [{:keys [status body]}]
  (log/info :response :status 200)
  (json-response-parser body))

(defn- add-query-string
  "If `:http/query` is given, appends the query string to the
  request url."
  [request {:http/keys [query-params]}]
  (if-not query-params
    request
    (update request :url #(str % "?" (form-encoder query-params)))))

(defn- expand-path-params
  [request {:http/keys [url path-params]}]
  (assoc request :url
         (string/replace url #"\{([^\}]+)\}" (fn [match]
                                               (get path-params (keyword (last match))
                                                    (first match))))))

(defn- parse-request-body
  "If `:http/payload` is given, parses it according to the supplied
  content-type and assoc's the parsed value as `:body` into the
  request."
  [request {:http/keys [payload]}]
  (if-not payload
    request
    (assoc request :body
           (json-request-parser payload))))

(defn- add-headers
  "When :http/headers is present, includes additional headers in
  the request."
  [request {:http/keys [headers]}]
  (if-not headers
    request
    (update request :headers #(merge % (normalize-header-names headers)))))

(defn- add-oauth-token
  [request {:http/keys [oauth-token]}]
  (if oauth-token
    (assoc-in request [:headers "authorization"] (str "Bearer " oauth-token))
    request))

(defn build-http-request
  "Returns a suited HTTP request map for the supplied Slack method."
  [{:http/keys [verb url produces] :as req-data}]
  (-> {:method  verb
       :url     url
       :headers {"content-type" (or produces default-mime-type)
                 "accept"       default-mime-type}}
      (merge http-defaults)
      (add-oauth-token req-data)
      (add-headers req-data)
      (expand-path-params req-data)
      (parse-request-body req-data)
      (add-query-string req-data)))

(defn send-async
  "Sends an asynchronous HTTP request and returns a core.async channel
  filled out with the response."
  [req-data]
  (let [channel (async/chan)]
    (-> (build-http-request req-data)
        log-request
        (httpkit-client/request #(async/put! channel (handle-http-response %))))
    channel))

(defn send-and-await
  [req-data]
  (<!! (send-async req-data)))
