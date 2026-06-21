(ns oauth.client-twitter-test
  (:refer-clojure :exclude [key])
  (:require [oauth.client :as oc])
  (:use clojure.test))

;; Live Twitter credentials for the ^:integration tests below. Read from the
;; environment so this namespace always compiles without a credentials file.
;; Run `lein test :integration` with TWITTER_CONSUMER_KEY and
;; TWITTER_CONSUMER_SECRET set to exercise these against the live Twitter API.
(def key (System/getenv "TWITTER_CONSUMER_KEY"))
(def secret (System/getenv "TWITTER_CONSUMER_SECRET"))

(def consumer-hmac-sha1 (oc/make-consumer key
                                secret
                                "https://api.twitter.com/oauth/request_token"
                                "https://api.twitter.com/oauth/access_token"
                                "https://api.twitter.com/oauth/authorize"
                                :hmac-sha1))
(deftest
    ^:integration
    #^{:doc "Test requesting a token from Twitter.
            Considered to pass if no exception is thrown."}
    hmac-sha1-request-token-test
  (oc/request-token consumer-hmac-sha1))

(deftest
    ^:integration
    #^{:doc "Considered to pass if no exception is thrown."}
    hmac-sha1-user-approval-uri-test
  (is (instance? String (oc/user-approval-uri consumer-hmac-sha1 (:oauth_token (oc/request-token consumer-hmac-sha1))))))

(def consumer-hmac-sha256 (oc/make-consumer key
                                  secret
                                  "https://api.twitter.com/oauth/request_token"
                                  "https://api.twitter.com/oauth/access_token"
                                  "https://api.twitter.com/oauth/authorize"
                                  :hmac-sha256))
(deftest
    ^:integration
    #^{:doc "Test requesting a token from Twitter.
            Considered to pass if no exception is thrown."}
    hmac-sha256-request-token-test
  (oc/request-token consumer-hmac-sha256))

(deftest
    ^:integration
    #^{:doc "Considered to pass if no exception is thrown."}
    hmac-sha256-user-approval-uri-test
  (is (instance? String (oc/user-approval-uri consumer-hmac-sha256 (:oauth_token (oc/request-token consumer-hmac-sha256))))))

#_(deftest
    #^{:doc "Considered to pass if no exception is thrown."}
  access-token
  (let [request-token (oc/request-token consumer)]
    (oc/access-token consumer request-token ...verifier...)))
