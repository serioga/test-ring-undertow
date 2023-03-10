(ns app.$-example.core
  (:require [app.$-example.impl.handler :as handler]
            [app.rum.core #_"React components"]
            [app.webapp.ring-handler :as ring-handler]
            [lib.clojure.ns :as ns]))

(set! *warn-on-reflection* true)

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(ns/require-dir 'app.$-example.handler._)

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn- example-routes
  []
  (ring-handler/collect-routes handler/route-path))

(comment
  (example-routes))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn example-http-handler
  "HTTP server handler for `example` webapp."
  [config]
  (ring-handler/webapp-http-handler handler/example-handler, (example-routes), config))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(comment
  (def -req {:headers {"accept" "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                       "accept-encoding" "gzip, deflate",
                       "accept-language" "ru,en;q=0.8,de;q=0.6,uk;q=0.4,be;q=0.2",
                       "connection" "keep-alive",
                       "cookie" "JSESSIONID=Mpk7s_F3vXFJs0pUHhxy00OSha6Z9xu1pnDPrmNr",
                       "host" "example.localtest.me:8080",
                       "referer" "http://example.localtest.me:8080/",
                       "sec-gpc" "1",
                       "upgrade-insecure-requests" "1",
                       "user-agent" "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/109.0"},
             :query-string "value=Test+Value",
             :remote-addr "0:0:0:0:0:0:0:1",
             :request-method :get,
             :scheme :http,
             :server-name "example.localtest.me",
             :server-port 8080,
             :uri "/example-path-param/Test%20Name"})
  (def -h (ring-handler/webapp-http-handler (fn [_] {:status 200}) (example-routes)
                                            {:dev-mode true, :hosts #{"example.localtest.me"}, :name "example"}))
  (-h -req)
  ;; Execution time mean : 5.394125 µs
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
