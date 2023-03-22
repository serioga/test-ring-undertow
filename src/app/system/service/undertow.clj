(ns app.system.service.undertow
  (:require [integrant.core :as ig]
            [lib.clojure-tools-logging.logger :as logger]
            [lib.clojure.core :as c]
            [strojure.ring-undertow.server :as server]
            [strojure.undertow.api.exchange :as exchange]
            [strojure.undertow.handler :as handler]))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn- set-virtual-hosts
  [vhost-map, {webapp-name :name :as webapp}]
  (logger/debug (logger/get-logger *ns*) (c/pr-str* "Start webapp" webapp-name (webapp :options)))
  (let [handler (webapp :handler)]
    (reduce (fn [m host] (assoc m host handler))
            vhost-map (webapp :hosts))))

(def ^:private log-csp-report-handler
  (-> (fn [exchange]
        (logger/error (logger/get-logger "csp-report")
                      (-> (exchange/get-input-stream exchange)
                          (.readAllBytes)
                          (String.))))
      (handler/with-exchange)
      (handler/dispatch)))

(defn- start-server
  [{:keys [options, webapps, dev/prepare-webapp, dev-mode]}]
  (let [{:keys [host port]} options
        prepare-webapp (or prepare-webapp identity)
        running-webapps (keep (fn [webapp] (when (get webapp :webapp-is-enabled true)
                                             (prepare-webapp webapp)))
                              webapps)]
    (-> (server/start {:handler [{:type handler/security
                                  :csp {:policy
                                        (cond-> {"default-src" :none
                                                 "script-src" ["http:" "https:" :nonce :strict-dynamic :unsafe-inline]
                                                 "style-src" [:self :unsafe-inline]
                                                 "img-src" :self
                                                 "base-uri" :self
                                                 "form-action" :self
                                                 "frame-ancestors" :none}
                                          dev-mode
                                          (-> (update "script-src" conj :unsafe-eval)
                                              (assoc "connect-src" ["ws:" :self])))
                                        :report-handler log-csp-report-handler}}
                                 {:type handler/resource :resource-manager :classpath-files}
                                 {:type handler/proxy-peer-address}
                                 {:type handler/session}
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
