(ns
    #^{:author "Matt Revelle"
       :doc "OAuth client library for Clojure."}
  oauth.client
  (:require [oauth.signature :as sig]
            [clj-http.client :as httpclient]
            [clojure.string :refer [join split upper-case]]))

(defrecord #^{:doc "OAuth consumer"}
    Consumer [key secret request-uri
              access-uri authorize-uri signature-method])
(defn make-consumer
  "Make a consumer struct map."
  [key secret request-uri access-uri authorize-uri signature-method]
  (Consumer.
          key
          secret
          request-uri
          access-uri
          authorize-uri
          signature-method))

(defn user-approval-uri
  "Build the URI for the Service Provider. The User approves the Consumer's
access to the User's account there. You can include extra parameters in a map."
  ([consumer token]
   (user-approval-uri consumer token {}))
  ([consumer token extra-params]
   (str (:authorize-uri consumer)
        "?" (httpclient/generate-query-string (merge {:oauth_token token} extra-params)))))

(defn authorization-header
  "Format OAuth credentials for the Authorization HTTP header."
  ([oauth-params]
     (str "OAuth "
          (join ", "
                (map (fn [[k v]]
                       (str (-> k sig/as-str sig/url-encode)
                            "=\"" (-> v sig/as-str sig/url-encode) "\""))
                     oauth-params))))
  ([oauth-params realm]
     (authorization-header (assoc oauth-params :realm realm))))

(defn form-decode
  "Parse form-encoded bodies in OAuth responses."
  [s]
  (if s
    (into {}
          (map (fn [kv]
                 (let [[k v] (split kv #"=" 2)
                       k (or k "")
                       v (or v "")]
                   [(keyword (sig/url-decode k)) (sig/url-decode v)]))
               (split s #"&")))
    nil))

(defn- check-success-response [m]
  (let [code (:status m)]
    (if (or (< code 200)
            (>= code 300))
      (throw (new Exception (str "Got non-success code: " code ". "
                                 "Content: " (:body m))))
      m)))

(defn build-request
  "Build a request from prepared parameters."
  [oauth-params & [form-params]]
  (let [req (merge
             {:headers {"Authorization" (authorization-header
                                         oauth-params)}}
             (when form-params {:form-params form-params}))]
    req))

(defn post-request-body-decoded [url & [req]]
  (form-decode
   (:body (check-success-response
           (httpclient/post url req)))))

(defn credentials
  "Return authorization credentials for protected resources. The returned map
contains key-value pairs. Add them to the Authorization HTTP header or as query
parameters in the request."
  ([consumer token token-secret request-method request-uri & [request-params]]
     (let [unsigned-oauth-params (sig/oauth-params consumer
                                                   (sig/rand-str 30)
                                                   (sig/msecs->secs (System/currentTimeMillis))
                                                   token)
           unsigned-params (merge request-params
                                  unsigned-oauth-params)
           signature (sig/sign consumer
                               (sig/base-string (-> request-method
                                                    sig/as-str
                                                    upper-case)
                                                request-uri
                                                 unsigned-params)
                               token-secret)]
       (assoc unsigned-oauth-params :oauth_signature signature))))

(defn- execute-request [request-method url request-options]
  (case request-method
    "GET" (httpclient/get url request-options)
    "POST" (httpclient/post url request-options)
    "PUT" (httpclient/put url request-options)
    "DELETE" (httpclient/delete url request-options)
    (throw (IllegalArgumentException.
            (str "Unsupported request method: " request-method)))))

(defn signed-request
  "Execute a signed OAuth request.

  `request-options` accepts `:oauth-params` for additional OAuth parameters.
  All other options are passed to clj-http unchanged, including query and form
  parameters, which are included in the signature."
  ([consumer token token-secret request-method url]
   (signed-request consumer token token-secret request-method url {}))
  ([consumer token token-secret request-method url request-options]
   (let [method (-> request-method sig/as-str upper-case)
         signing-params (merge (:oauth-params request-options)
                               (:query-params request-options)
                               (:form-params request-options))
         oauth-params (merge (:oauth-params request-options)
                             (credentials consumer token token-secret method url signing-params))
         request-options (-> request-options
                             (dissoc :oauth-params)
                             (update :headers merge
                                     {"Authorization" (authorization-header oauth-params)}))]
     (execute-request method url request-options))))

(defn get-request
  "Execute a signed GET request."
  ([consumer token token-secret url]
   (signed-request consumer token token-secret :GET url))
  ([consumer token token-secret url request-options]
   (signed-request consumer token token-secret :GET url request-options)))

(defn post-request
  "Execute a signed POST request."
  ([consumer token token-secret url]
   (signed-request consumer token token-secret :POST url))
  ([consumer token token-secret url request-options]
   (signed-request consumer token token-secret :POST url request-options)))

(defn put-request
  "Execute a signed PUT request."
  ([consumer token token-secret url]
   (signed-request consumer token token-secret :PUT url))
  ([consumer token token-secret url request-options]
   (signed-request consumer token token-secret :PUT url request-options)))

(defn delete-request
  "Execute a signed DELETE request."
  ([consumer token token-secret url]
   (signed-request consumer token token-secret :DELETE url))
  ([consumer token token-secret url request-options]
   (signed-request consumer token token-secret :DELETE url request-options)))

(defn build-oauth-token-request
  "Build an OAuth request."
  ([consumer uri unsigned-oauth-params & [extra-params token-secret]]
     (let [signature (sig/sign consumer
                               (sig/base-string "POST" uri (merge unsigned-oauth-params extra-params))
                               token-secret)
           oauth-params (assoc unsigned-oauth-params :oauth_signature signature)]
       (build-request oauth-params extra-params))))

(defn request-token
  "Get a request token for the Consumer."
  ([consumer]
     (request-token consumer "oob" nil))
  ([consumer callback-uri]
     (request-token consumer callback-uri nil))
  ([consumer callback-uri extra-params]
     (let [unsigned-params (-> (sig/oauth-params consumer
                                                 (sig/rand-str 30)
                                                 (sig/msecs->secs (System/currentTimeMillis)))
                               (assoc :oauth_callback callback-uri))]
       (post-request-body-decoded (:request-uri consumer)
                                  (build-oauth-token-request consumer
                                                             (:request-uri consumer)
                                                             unsigned-params
                                                             extra-params)))))

(defn access-token
  "Exchange a request token for an access token.
  With two arguments, this function follows OAuth 1.0.
  With three arguments, it uses a verifier."
  ([consumer request-token]
     (access-token consumer request-token nil))
  ([consumer request-token verifier]
     (let [unsigned-oauth-params (if verifier
                                   (sig/oauth-params consumer
                                                     (sig/rand-str 30)
                                                     (sig/msecs->secs (System/currentTimeMillis))
                                                     (:oauth_token request-token)
                                                     verifier)
                                   (sig/oauth-params consumer
                                                     (sig/rand-str 30)
                                                     (sig/msecs->secs (System/currentTimeMillis))
                                                     (:oauth_token
                                                      request-token)))
           token-secret (:oauth_token_secret request-token)]
       (post-request-body-decoded (:access-uri consumer)
                                  (build-oauth-token-request consumer
                                                             (:access-uri consumer)
                                                             unsigned-oauth-params
                                                             nil
                                                             token-secret)))))
(defn build-xauth-access-token-request
  ([consumer username password nonce timestamp]
   (build-xauth-access-token-request consumer nil username password nonce timestamp))
  ([consumer {token :oauth_token secret :oauth_token_secret} username password nonce timestamp]
   (let [oauth-params (if token
                        (sig/oauth-params consumer nonce timestamp token)
                        (sig/oauth-params consumer nonce timestamp))
         post-params {:x_auth_username username
                      :x_auth_password password
                      :x_auth_mode "client_auth"}
         signature-base (sig/base-string "POST"
                                         (:access-uri consumer)
                                         (merge oauth-params
                                                post-params))
         signature (if secret (sig/sign consumer signature-base secret) (sig/sign consumer signature-base))
         params (assoc oauth-params
                       :oauth_signature signature)]
     (build-request params post-params))))

(defn refresh-token
  "Exchange an expired access token for a new access token."
  ([consumer expired-token]
   (refresh-token consumer expired-token nil))
  ([consumer expired-token verifier]
   (let [base-oauth-params (if verifier
                             (sig/oauth-params consumer
                                               (sig/rand-str 30)
                                               (sig/msecs->secs (System/currentTimeMillis))
                                               (:oauth_token expired-token)
                                               verifier)
                             (sig/oauth-params consumer
                                               (sig/rand-str 30)
                                               (sig/msecs->secs (System/currentTimeMillis))
                                               (:oauth_token expired-token)))
         unsigned-oauth-params (assoc base-oauth-params
                                 :oauth_session_handle (:oauth_session_handle expired-token))]
     (post-request-body-decoded (:access-uri consumer)
                                (build-oauth-token-request consumer
                                                           (:access-uri consumer)
                                                           unsigned-oauth-params
                                                           nil
                                                           (:oauth_token_secret expired-token))))))

(defn xauth-access-token
  "Request an xAuth access token with a username and password."
  [consumer username password]
  (post-request-body-decoded (:access-uri consumer)
                             (build-xauth-access-token-request consumer
                                                               username
                                                               password
                                                               (sig/rand-str 30)
                                                               (sig/msecs->secs (System/currentTimeMillis)))))
