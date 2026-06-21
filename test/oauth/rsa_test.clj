(ns oauth.rsa-test
  "RSA-SHA256 signing and PKCS#1/PKCS#8 private-key support.

  Signatures are checked by verifying them against the matching public key
  (a stronger spec than a frozen base64 blob), so the fixtures can be any
  RSA-2048 key. RSA/PKCS#1 v1.5 signing is deterministic, so the
  same-input-same-output property is also pinned."
  (:require [oauth.signature :as sig]
            [clojure.java.io :as io])
  (:use clojure.test)
  (:import (java.io StringReader)
           (java.security Security Signature)
           (java.util Base64)
           (org.bouncycastle.jce.provider BouncyCastleProvider)
           (org.bouncycastle.openssl PEMParser)
           (org.bouncycastle.openssl.jcajce JcaPEMKeyConverter)))

(Security/addProvider (BouncyCastleProvider.))

(defn- resource-pem [name] (slurp (io/resource name)))

(def pkcs1-key (resource-pem "oauth/rsa_2048_pkcs1.pem"))
(def pkcs8-key (resource-pem "oauth/rsa_2048_pkcs8.pem"))
(def public-pem (resource-pem "oauth/rsa_2048_pub.pem"))

(defn- public-key []
  (let [conv (doto (JcaPEMKeyConverter.) (.setProvider "BC"))
        info (-> public-pem StringReader. (PEMParser.) .readObject)]
    (.getPublicKey conv info)))

(defn- rsa-verifies? [alg ^String base-string ^String b64-sig]
  (let [v (doto (Signature/getInstance alg "BC")
            (.initVerify (public-key))
            (.update (.getBytes base-string "UTF-8")))]
    (.verify v (.decode (Base64/getDecoder) b64-sig))))

(def base-string
  (sig/base-string "POST"
                   "https://photos.example.net/request_token"
                   {:oauth_consumer_key "dpf43f3p2l4k3l03"
                    :oauth_signature_method "RSA-SHA256"
                    :oauth_timestamp "1191242090"
                    :oauth_nonce "hsu94j3884jdopsl"
                    :oauth_version "1.0"}))

(deftest signature-method-registered
  (is (= "RSA-SHA256" (sig/signature-methods :rsa-sha256))))

(deftest rsa-sha256-pkcs1-signature
  (let [s (sig/sign {:secret pkcs1-key :signature-method :rsa-sha256} base-string)]
    (is (rsa-verifies? "SHA256withRSA" base-string s))))

(deftest rsa-sha256-pkcs8-signature
  (let [s (sig/sign {:secret pkcs8-key :signature-method :rsa-sha256} base-string)]
    (is (rsa-verifies? "SHA256withRSA" base-string s))))

(deftest rsa-sha1-pkcs8-signature
  ;; PKCS#8 keys must also work with the existing rsa-sha1 method.
  (let [s (sig/sign {:secret pkcs8-key :signature-method :rsa-sha1} base-string)]
    (is (rsa-verifies? "SHA1withRSA" base-string s))))

(deftest rsa-sha256-deterministic
  (let [c {:secret pkcs1-key :signature-method :rsa-sha256}]
    (is (= (sig/sign c base-string) (sig/sign c base-string)))))
