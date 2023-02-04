(ns app.system.service.undertow
  (:require [integrant.core :as ig]
            [lib.clojure-tools-logging.logger :as logger]
            [lib.clojure.core :as c]
            [strojure.ring-undertow.server :as server]
            [strojure.undertow.handler :as handler])
  (:import (io.undertow.server HttpHandler ResponseCommitListener)
           (io.undertow.util Headers)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn- set-virtual-hosts
  [vhost-map, {webapp-name :name :as webapp}]
  (logger/debug (logger/get-logger *ns*) (c/pr-str* "Start webapp" webapp-name (webapp :options)))
  (let [handler (webapp :handler)]
    (reduce (fn [m host] (assoc m host handler))
            vhost-map (webapp :hosts))))

(defn- set-content-type-options
  []
  (let [listener (reify ResponseCommitListener
                   (beforeCommit [_ exchange]
                     (let [headers (.getResponseHeaders exchange)]
                       (when (.contains headers Headers/CONTENT_TYPE)
                         (.put headers Headers/X_CONTENT_TYPE_OPTIONS "nosniff")))))]
    (fn [^HttpHandler handler]
      (reify HttpHandler
        (handleRequest [_ exchange]
          (.addResponseCommitListener exchange listener)
          (.handleRequest handler exchange))))))

(defn- start-server
  [{:keys [options, webapps, dev/prepare-webapp]}]
  (let [{:keys [host port]} options
        prepare-webapp (or prepare-webapp identity)
        running-webapps (keep (fn [webapp] (when (get webapp :webapp-is-enabled true)
                                             (prepare-webapp webapp)))
                              webapps)]
    (-> (server/start {:handler [{:type handler/graceful-shutdown}
                                 (set-content-type-options)
                                 {:type handler/resource :resource-manager :classpath-files}
                                 {:type handler/proxy-peer-address}
                                 {:type handler/virtual-host
                                  :host (reduce set-virtual-hosts {} running-webapps)}]
                       :port {port {:host host}}})
        (vary-meta assoc :running-webapps
                   (map (fn [{webapp-name :name :as webapp}]
                          [webapp-name (merge options (webapp :options))])
                        running-webapps)))))

(defn- stop-server
  [server]
  (server/stop server))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defmethod ig/init-key :app.system.service/undertow
  [_ options]
  (start-server options))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defmethod ig/halt-key! :app.system.service/undertow
  [_ server]
  (stop-server server))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
