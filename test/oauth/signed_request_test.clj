(ns oauth.signed-request-test
  (:require [clj-http.client :as http]
            [oauth.client :as oc]
            [oauth.signature :as sig]
            [clojure.test :refer [deftest is]]))

(def consumer
  (oc/make-consumer "consumer-key"
                    "consumer-secret"
                    "https://example.test/request-token"
                    "https://example.test/access-token"
                    "https://example.test/authorize"
                    :hmac-sha1))

(def token "access-token")
(def token-secret "access-token-secret")
(def request-url "https://example.test/resource?existing=yes")

(defn header-params [header]
  (into {}
        (map (fn [[_ key value]]
               [(keyword (sig/url-decode key)) (sig/url-decode value)])
             (re-seq #"(?:^OAuth |, )([^=]+)=\"([^\"]*)\"" header))))

(defn expected-signature [method options oauth-params]
  (sig/sign consumer
            (sig/base-string method
                             request-url
                             (dissoc (merge oauth-params
                                            (:query-params options)
                                            (:form-params options))
                                     :oauth_signature))
            token-secret))

(defn exercise-request [method request-fn]
  (let [seen (atom nil)
        options {:oauth-params {:oauth_callback "https://client.test/callback"}
                 :query-params {:include "profile"}
                 :form-params {:status "hello world"}
                 :headers {"X-Request-ID" "request-1"}}
        response {:status 200 :body "ok"}]
    (with-redefs-fn {request-fn (fn [url request-options]
                                  (reset! seen [url request-options])
                                  response)}
      #(do
      (is (= response (oc/signed-request consumer token token-secret method request-url options)))
      (let [[url request-options] @seen
            header (get-in request-options [:headers "Authorization"])
            oauth-params (header-params header)]
        (is (= request-url url))
        (is (= "request-1" (get-in request-options [:headers "X-Request-ID"])))
        (is (= "https://client.test/callback" (:oauth_callback oauth-params)))
        (is (re-matches #"[0-9a-z]+" (:oauth_nonce oauth-params)))
        (is (re-matches #"[0-9]+" (:oauth_timestamp oauth-params)))
        (is (= (expected-signature method
                                  options
                                  (merge (:oauth-params options) oauth-params))
               (:oauth_signature oauth-params)))
        (is (nil? (:oauth-params request-options)))
        (is (= (:form-params options) (:form-params request-options)))
        (is (= (:query-params options) (:query-params request-options))))))))

(deftest signed-get-request
  (exercise-request "GET" #'http/get))

(deftest signed-post-request
  (exercise-request "POST" #'http/post))

(deftest signed-put-request
  (exercise-request "PUT" #'http/put))

(deftest signed-delete-request
  (exercise-request "DELETE" #'http/delete))

(deftest signed-request-accepts-ordered-pairs
  (let [seen (atom nil)
        options {:oauth-params [["oauth_callback" "https://client.test/callback"]
                                ["oauth_callback" "https://client.test/second"]]
                 :query-params [["tag" "one"] ["tag" "two"]]
                 :form-params [["a" "one"] ["aa" "two"]]}
        response {:status 200 :body "ok"}]
    (with-redefs-fn {#'http/get (fn [url request-options]
                                  (reset! seen [url request-options])
                                  response)
                     #'sig/rand-str (constantly "nonce")
                     #'sig/msecs->secs (constantly 123)}
      #(do
         (is (= response (oc/signed-request consumer token token-secret :GET request-url options)))
         (let [[_ request-options] @seen
               header (get-in request-options [:headers "Authorization"])]
           (is (= 2 (count (re-seq #"oauth_callback=" header))))
           (is (= (:query-params options) (:query-params request-options)))
           (is (= (:form-params options) (:form-params request-options))))))))

(deftest signed-request-accepts-public-oauth-time-injection
  (let [seen (atom nil)
        options {:oauth-nonce-fn (constantly "injected-nonce")
                 :oauth-clock-fn (constantly 1700000000123)}]
    (with-redefs-fn {#'http/get (fn [url request-options]
                                  (reset! seen [url request-options])
                                  {:status 200 :body "ok"})}
      #(do
         (oc/signed-request consumer token token-secret :GET request-url options)
         (let [header (get-in (second @seen) [:headers "Authorization"])
               params (header-params header)]
           (is (= "injected-nonce" (:oauth_nonce params)))
           (is (= "1700000000" (:oauth_timestamp params))))))))

(deftest signed-request-accepts-public-timestamp-function
  (let [seen (atom nil)
        options {:oauth-nonce-fn (constantly "nonce")
                 :oauth-timestamp-fn (constantly 4242)}]
    (with-redefs-fn {#'http/get (fn [_ request-options]
                                  (reset! seen request-options)
                                  {:status 200 :body "ok"})}
      #(do
         (oc/signed-request consumer token token-secret :GET request-url options)
         (is (= "4242"
                (:oauth_timestamp
                 (header-params (get-in @seen [:headers "Authorization"])))))))))
