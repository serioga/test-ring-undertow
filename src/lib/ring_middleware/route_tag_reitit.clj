(ns lib.ring-middleware.route-tag-reitit
  (:require [lib.clojure.perf :as p]
            [reitit.core :as reitit]
            [strojure.zmap.core :as zmap]))

(set! *warn-on-reflection* true)

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn- get-path-fn
  [reitit-router, route-tag]
  (let [match (reitit/match-by-name reitit-router, route-tag)]
    (if (reitit/partial-match? match)
      ;; Route with path parameters.
      (let [required (:required match)]
        (fn get-param-path
          ([]
           (reitit/match-by-name! reitit-router, route-tag))
          ([params]
           (let [match (reitit/match-by-name! reitit-router, route-tag, (select-keys params required))]
             (if (== (count required) (count params))
               (reitit/match->path match)
               (reitit/match->path match (remove #(required (key %)) params)))))))
      ;; Route without path parameters.
      (fn get-simple-path
        ([]
         (reitit/match->path match))
        ([params]
         (reitit/match->path match params))))))

(defn- path-for-route-fn
  [reitit-router]
  (let [get-path-fns (reduce (fn [m tag] (assoc m tag (get-path-fn reitit-router, tag)))
                             {} (reitit/route-names reitit-router))]
    (fn path-for-route
      ([route-tag]
       (when-some [get-path (get-path-fns route-tag)]
         (get-path)))
      ([route-tag, params]
       (when-some [get-path (get-path-fns route-tag)]
         (get-path params))))))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn route-tag-request
  "Adds route-tag data in request. Returns `(fn [request])` for 1-arity."
  ([reitit-router]
   (assert (reitit/router? reitit-router))
   (fn [request]
     (route-tag-request request reitit-router)))
  ([request reitit-router]
   (let [match (reitit/match-by-path reitit-router (request :uri))
         route-tag (-> match :data :name)
         path-params (-> match :path-params not-empty)]
     (cond-> (assoc request :route-tag/path-for-route (path-for-route-fn reitit-router))
       route-tag,, (assoc :route-tag route-tag)
       path-params (-> (assoc :path-params path-params)
                       (zmap/update :path-or-query-params (fn merge-route-params [params]
                                                            (p/merge-not-empty params path-params)))
                       (zmap/wrap))))))

(defn wrap-route-tag
  "Wrap handler with route-tag functionality."
  [handler, reitit-router]
  {:pre [(reitit/router? reitit-router)]}
  (fn route-tag
    [request]
    (handler (route-tag-request request reitit-router))))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••
