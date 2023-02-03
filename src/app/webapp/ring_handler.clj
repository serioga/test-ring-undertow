(ns app.webapp.ring-handler
  "Ring-based definition for request-response handling."
  (:require [lib.clojure.core :as c]
            [lib.ring-middleware.error-exception :as error-exception]
            [lib.ring-middleware.error-not-found :as error-not-found]
            [lib.ring-middleware.response-logger :as debug-response]
            [lib.ring-middleware.route-tag-reitit :as route-tag]
            [lib.slf4j.mdc :as mdc]
            [reitit.core :as reitit]
            [strojure.ring-control.config.ring-middleware-defaults :as ring-defaults]
            [strojure.ring-control.handler :as handler]
            [strojure.ring-lib.middleware.params :as params])
  (:import (java.util UUID)))

(set! *warn-on-reflection* true)

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn collect-routes
  "Returns vector of reitit routes defined by `route-path` multimethod."
  [route-path]
  (->> (keys (methods route-path))
       (group-by route-path)
       (sort-by first)
       (mapv (fn [[path tags]]
               (c/assert (= 1 (count tags)) (c/pr-str* "Duplicate route-path" path "for tags" tags))
               [path (first tags)]))))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn- wrap-error-exception
  [dev-mode]
  {:name `wrap-error-exception
   :wrap (fn [handler]
           (error-exception/wrap-error-exception handler dev-mode))})

(defn- req-params
  [opts]
  {:name `req-params
   :enter (params/params-request-fn opts)})

(defn- req-route-tag
  [reitit-router]
  {:name `req-route-tag
   :enter (route-tag/route-tag-request reitit-router)})

(defn- wrap-mdc
  []
  {:name `wrap-mdc
   :wrap (fn [handler]
           (fn [request]
             (with-open [_ (mdc/put-closeable "hostname" (request :server-name))
                         _ (mdc/put-closeable "route-tag" (some-> (request :route-tag) (str)))
                         _ (mdc/put-closeable "session" (some-> (request :session) (str)))
                         _ (mdc/put-closeable "request-id" (.toString (UUID/randomUUID)))]
               (handler request))))})

(defn- wrap-debug-response
  []
  {:name `wrap-debug-response
   :wrap debug-response/wrap-response-logger})

(defn- resp-error-not-found
  [dev-mode]
  {:name `resp-error-not-found
   :leave (fn [response request]
            (error-not-found/error-not-found-response response request dev-mode))})

#_(defn- wrap-mdc-
    [handler]
    (fn [request]
      (with-open [_ (mdc/put-closeable "hostname" (request :server-name))
                  _ (mdc/put-closeable "route-tag" (some-> (request :route-tag) (str)))
                  _ (mdc/put-closeable "session" (some-> (request :session) (str)))
                  _ (mdc/put-closeable "request-id" (.toString (UUID/randomUUID)))]
        (handler request))))

#_(defn webapp-http-handler
    "Build HTTP server handler for webapp with common middleware."
    [http-handler, routes, {:keys [dev-mode]}]
    (-> http-handler
        (error-not-found/wrap-error-not-found dev-mode)
        (debug-response/wrap-response-logger)
        (wrap-mdc-)
        (route-tag/wrap-route-tag (reitit/router routes))
        (defaults/wrap-defaults (-> defaults/site-defaults
                                    (assoc-in [:security :anti-forgery] false)
                                    (assoc-in [:security :frame-options] false)
                                    (dissoc :session)))
        (error-exception/wrap-error-exception dev-mode)))

(defn webapp-http-handler
  "Build HTTP server handler for webapp with common middleware."
  [http-handler, routes, {:keys [dev-mode]}]
  (let [config [[(wrap-error-exception dev-mode)]
                (ring-defaults/config
                  {#_#_:params {:urlencoded true
                                :multipart true
                                :keywordize true}
                   :cookies true                            ; + 1.3 µs (without cookie header)
                   #_#_:security {:content-type-options :nosniff}
                   #_#_:responses {:not-modified-responses true
                                   :absolute-redirects true}})
                [(req-params {:param-key-fn keyword})       ; + 1.0 µs
                 (req-route-tag (reitit/router routes))     ; + 2.5 µs
                 (wrap-mdc)                                 ; + 1.2 µs
                 (wrap-debug-response)
                 (resp-error-not-found dev-mode)]]]
    (handler/build http-handler (apply concat config))))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••
