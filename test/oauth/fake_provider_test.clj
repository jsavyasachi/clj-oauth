(ns oauth.fake-provider-test
  (:import (com.sun.net.httpserver HttpExchange HttpHandler HttpServer)
           (java.net InetSocketAddress)
           (java.nio.charset StandardCharsets))
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [oauth.client :as oauth]))

(defn- body [^HttpExchange exchange]
  (slurp (.getRequestBody exchange)))

(defn- respond! [^HttpExchange exchange status response]
  (let [bytes (.getBytes response StandardCharsets/UTF_8)]
    (.sendResponseHeaders exchange status (long (count bytes)))
    (with-open [output (.getResponseBody exchange)]
      (.write output bytes))))

(defn- fake-provider []
  (let [requests (atom [])
        server (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)
        handler (reify HttpHandler
                  (handle [_ exchange]
                    (let [path (.getPath (.getRequestURI exchange))
                          request-body (body exchange)
                          authorization (get (.getRequestHeaders exchange) "Authorization")]
                      (swap! requests conj {:path path
                                             :body request-body
                                             :authorization authorization})
                      (cond
                        (= path "/oauth/request_token")
                        (respond! exchange 200 "oauth_token=request-token&oauth_token_secret=request-secret&oauth_callback_confirmed=true")

                        (= path "/oauth/access_token")
                        (respond! exchange 200 "oauth_token=access-token&oauth_token_secret=access-secret")

                        (= path "/resource")
                        (respond! exchange 200 "protected")

                        :else
                        (respond! exchange 404 "not found")))))]
    (.createContext server "/" handler)
    (.start server)
    {:server server
     :requests requests
     :base-url (str "http://127.0.0.1:" (.getPort (.getAddress server)))}))

(deftest oauth-flows-work-against-a-local-provider
  (let [{:keys [server requests base-url]} (fake-provider)]
    (try
      (let [consumer (oauth/make-consumer "consumer-key" "consumer-secret"
                                          (str base-url "/oauth/request_token")
                                          (str base-url "/oauth/access_token")
                                          (str base-url "/oauth/authorize")
                                          :hmac-sha1)
            options {:oauth-nonce-fn (constantly "nonce")
                     :oauth-timestamp-fn (constantly 1700000000)}
            request-token (oauth/request-token consumer "https://client/callback" nil options)
            approval-uri (oauth/user-approval-uri consumer (:oauth_token request-token)
                                                  {:state "ready"})
            access-token (oauth/access-token consumer request-token "verifier" options)
            refreshed (oauth/refresh-token consumer access-token nil options)
            xauth-token (oauth/xauth-access-token consumer "user" "password" options)
            response (oauth/signed-request consumer
                                           (:oauth_token access-token)
                                           (:oauth_token_secret access-token)
                                           :GET
                                           (str base-url "/resource")
                                           options)]
        (is (= "request-token" (:oauth_token request-token)))
        (is (= (str base-url "/oauth/authorize?oauth_token=request-token&state=ready")
               approval-uri))
        (is (= "access-token" (:oauth_token access-token)))
        (is (= "access-token" (:oauth_token refreshed)))
        (is (= "access-token" (:oauth_token xauth-token)))
        (is (= "protected" (:body response)))
        (is (= 5 (count @requests)))
        (is (every? :authorization @requests))
        (is (some #(string/includes? (:body %) "x_auth_username=user") @requests)))
      (finally
        (.stop ^HttpServer server 0)))))
