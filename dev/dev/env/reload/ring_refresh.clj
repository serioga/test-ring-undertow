(ns dev.env.reload.ring-refresh
  (:require [clojure.string :as string]
            [lib.clojure-tools-logging.logger :as logger]
            [ring.middleware.refresh :as refresh]
            [strojure.undertow.handler.csp :as csp])
  (:import (java.util Date UUID)))

(set! *warn-on-reflection* true)

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(def ^:private refresh-state!
  (atom {::last-modified (Date.)
         ::refresh-is-enabled false}))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn- watch-until
  [state!, pred, timeout-ms]
  (let [watch-promise (promise)
        watch-key (str (UUID/randomUUID))]
    (try
      (add-watch state! watch-key (fn [_ _ _ value]
                                    (deliver watch-promise (pred value))))
      (if-some [v (pred @state!)] v
                                  (deref watch-promise timeout-ms false))
      (finally
        (remove-watch state! watch-key)))))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn- wrap-add-csp-nonce
  [handler]
  (fn [request]
    (let [nonce (-> request :server-exchange csp/get-request-nonce)
          response (handler request)
          body (:body response)]
      (cond-> response
        (and nonce (string? body))
        (assoc :body (string/replace-first body "<script type=\"text/javascript\">"
                                           (str "<script nonce=\"" nonce "\" type=\"text/javascript\">")))))))

(defn wrap-refresh
  "Modified `ring.middleware.refresh/wrap-refresh` without dependency on
  compojure and `params` middleware."
  [handler]
  (let [handler (-> handler
                    (@#'refresh/wrap-with-script @#'refresh/refresh-script)
                    (wrap-add-csp-nonce))]
    (fn [request]
      (if (= "/__source_changed" (:uri request))
        (let [body (if-let [since (some->> (:query-string request)
                                           (re-find #"since=(\d+)")
                                           (second)
                                           (Long/parseLong))]
                     (watch-until refresh-state!
                                  (fn [{::keys [last-modified, refresh-is-enabled]}]
                                    (when (> (.getTime ^Date last-modified) since)
                                      refresh-is-enabled))
                                  60000)
                     false)]
          {:headers {"Content-Type" "text/html; charset=utf-8"}
           :status 200 :body (str body)})
        (handler request)))))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn send-refresh
  "Send response to pages with flag if they should
   reload page or just reconnect to __source_changed."
  ([] (send-refresh (::refresh-is-enabled @refresh-state!)))
  ([refresh-is-enabled]
   (when refresh-is-enabled
     (logger/info (logger/get-logger *ns*) "Send refresh command to browser pages"))
   (reset! refresh-state! {::last-modified (Date.)
                           ::refresh-is-enabled refresh-is-enabled})))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••
