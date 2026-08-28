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
        "?" (sig/url-form-encode
              (concat [[:oauth_token token]]
                      (sig/param-pairs extra-params))))))

(defn authorization-header
  "Format OAuth credentials for the Authorization HTTP header."
  ([oauth-params]
     (str "OAuth "
          (join ", "
                (map (fn [[k v]]
                       (str (-> k sig/as-str sig/url-encode)
                            "=\"" (-> v sig/as-str sig/url-encode) "\""))
                     (sig/param-pairs oauth-params)))))
  ([oauth-params realm]
     (authorization-header
      (if (map? oauth-params)
        (assoc oauth-params :realm realm)
        (concat (sig/param-pairs oauth-params) [[:realm realm]])))))

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
      (throw (ex-info (str "Got non-success code: " code ". "
                          "Content: " (:body m))
                      {:status code
                       :headers (:headers m)
                       :body (:body m)
                       :oauth-params (form-decode (:body m))}))
      m)))

(defn- oauth-request-values
  "Return an injectable nonce and timestamp for an OAuth request."
  [options]
  (let [nonce-fn (or (:oauth-nonce-fn options) sig/rand-str)
        timestamp-fn (:oauth-timestamp-fn options)
        clock-fn (or (:oauth-clock-fn options) #(System/currentTimeMillis))]
    [(nonce-fn 30)
     (if timestamp-fn
       (timestamp-fn)
       (sig/msecs->secs (clock-fn)))]))

(defn- oauth-params-for
  [consumer options & [token verifier]]
  (let [[nonce timestamp] (oauth-request-values options)]
    (if verifier
      (sig/oauth-params consumer nonce timestamp token verifier)
      (if token
        (sig/oauth-params consumer nonce timestamp token)
        (sig/oauth-params consumer nonce timestamp)))))

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
  ([consumer token token-secret request-method request-uri & [request-params oauth-options]]
     (let [[nonce timestamp] (oauth-request-values oauth-options)
           unsigned-oauth-params (sig/oauth-params consumer nonce timestamp token)
           unsigned-params (concat (sig/param-pairs request-params)
                                   (sig/param-pairs unsigned-oauth-params))
           signature (sig/sign consumer
                               (sig/base-string (-> request-method
                                                    sig/as-str
                                                    upper-case)
                                                request-uri
                                                 unsigned-params)
                               token-secret)]
       (if (map? unsigned-oauth-params)
         (assoc unsigned-oauth-params :oauth_signature signature)
         (concat (sig/param-pairs unsigned-oauth-params)
                 [[:oauth_signature signature]])))))

(defn- execute-request [request-method url request-options]
  (case request-method
    "GET" (httpclient/get url request-options)
    "POST" (httpclient/post url request-options)
    "PUT" (httpclient/put url request-options)
    "DELETE" (httpclient/delete url request-options)
    (throw (IllegalArgumentException.
            (str "Unsupported request method: " request-method)))))

(defn- token-request-config [options]
  (merge {:method "POST"
          :body-encoding :form
          :response-parser form-decode}
         (:token-request options)))

(defn- configure-token-request [request body-params config]
  (let [body-params (or body-params {})
        request (case (:body-encoding config)
                  :form (assoc request :form-params body-params)
                  :query (assoc (dissoc request :form-params)
                                :query-params body-params)
                  :raw (assoc (dissoc request :form-params)
                              :body (sig/url-form-encode (sig/param-pairs body-params)))
                  (throw (IllegalArgumentException.
                          (str "Unsupported token body encoding: "
                               (:body-encoding config)))))]
    (cond-> (update request :headers merge (:headers config))
      (:content-type config) (assoc-in [:headers "Content-Type"] (:content-type config)))))

(defn- execute-token-request [url request body-params options]
  (let [config (token-request-config options)
        request (configure-token-request request body-params config)
        response (check-success-response
                  (execute-request (-> (:method config) sig/as-str upper-case)
                                   url
                                   request))]
    ((:response-parser config) (:body response))))

(defn signed-request
  "Execute a signed OAuth request.

  `request-options` accepts `:oauth-params` for additional OAuth parameters.
  All other options are passed to clj-http unchanged, including query and form
  parameters, which are included in the signature."
  ([consumer token token-secret request-method url]
   (signed-request consumer token token-secret request-method url {}))
  ([consumer token token-secret request-method url request-options]
   (let [method (-> request-method sig/as-str upper-case)
         signing-params (concat (sig/param-pairs (:oauth-params request-options))
                                (sig/param-pairs (:query-params request-options))
                                (sig/param-pairs (:form-params request-options)))
         oauth-params (concat (sig/param-pairs (:oauth-params request-options))
                              (sig/param-pairs
                               (credentials consumer token token-secret method url signing-params request-options)))
         request-options (-> request-options
                             (dissoc :oauth-params :oauth-nonce-fn :oauth-timestamp-fn :oauth-clock-fn)
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
  ([consumer uri unsigned-oauth-params & [extra-params token-secret request-config]]
     (let [signature (sig/sign consumer
                               (sig/base-string (-> (:method request-config "POST")
                                                    sig/as-str upper-case)
                                                uri
                                                (concat (sig/param-pairs unsigned-oauth-params)
                                                        (sig/param-pairs extra-params)))
                               token-secret)
           oauth-params (if (map? unsigned-oauth-params)
                          (assoc unsigned-oauth-params :oauth_signature signature)
                          (concat (sig/param-pairs unsigned-oauth-params)
                                  [[:oauth_signature signature]]))]
       (build-request oauth-params extra-params))))

(defn request-token
  "Get a request token for the Consumer."
  ([consumer]
     (request-token consumer "oob" nil))
  ([consumer callback-uri]
     (request-token consumer callback-uri nil))
  ([consumer callback-uri extra-params]
     (request-token consumer callback-uri extra-params {}))
  ([consumer callback-uri extra-params options]
     (let [unsigned-params (-> (oauth-params-for consumer options)
                               (assoc :oauth_callback callback-uri))]
       (execute-token-request (:request-uri consumer)
                              (build-oauth-token-request consumer
                                                         (:request-uri consumer)
                                                         unsigned-params
                                                         extra-params
                                                         nil
                                                         (:token-request options))
                              extra-params
                              options))))

(defn access-token
  "Exchange a request token for an access token.
  With two arguments, this function follows OAuth 1.0.
  With three arguments, it uses a verifier."
  ([consumer request-token]
     (access-token consumer request-token nil))
  ([consumer request-token verifier]
     (access-token consumer request-token verifier {}))
  ([consumer request-token verifier options]
     (let [unsigned-oauth-params (oauth-params-for consumer options
                                                   (:oauth_token request-token)
                                                   verifier)
           token-secret (:oauth_token_secret request-token)]
       (execute-token-request (:access-uri consumer)
                              (build-oauth-token-request consumer
                                                         (:access-uri consumer)
                                                         unsigned-oauth-params
                                                         nil
                                                         token-secret
                                                         (:token-request options))
                              nil
                              options))))
(defn- build-xauth-access-token-request* [consumer token username password nonce timestamp request-config]
  (let [secret (:oauth_token_secret token)
        token (:oauth_token token)
        oauth-params (if token
                       (sig/oauth-params consumer nonce timestamp token)
                       (sig/oauth-params consumer nonce timestamp))
        post-params {:x_auth_username username
                     :x_auth_password password
                     :x_auth_mode "client_auth"}
        signature-base (sig/base-string (-> (:method request-config "POST")
                                            sig/as-str upper-case)
                                        (:access-uri consumer)
                                        (merge oauth-params post-params))
        signature (if secret (sig/sign consumer signature-base secret)
                     (sig/sign consumer signature-base))
        params (assoc oauth-params :oauth_signature signature)]
    (build-request params post-params)))

(defn build-xauth-access-token-request
  ([consumer username password nonce timestamp]
   (build-xauth-access-token-request* consumer nil username password nonce timestamp nil))
  ([consumer {token :oauth_token secret :oauth_token_secret} username password nonce timestamp]
   (build-xauth-access-token-request* consumer {:oauth_token token
                                                :oauth_token_secret secret}
                                      username password nonce timestamp nil)))

(defn refresh-token
  "Exchange an expired access token for a new access token."
  ([consumer expired-token]
   (refresh-token consumer expired-token nil))
  ([consumer expired-token verifier]
   (refresh-token consumer expired-token verifier {}))
  ([consumer expired-token verifier options]
   (let [base-oauth-params (oauth-params-for consumer options
                                             (:oauth_token expired-token)
                                             verifier)
         unsigned-oauth-params (assoc base-oauth-params
                                 :oauth_session_handle (:oauth_session_handle expired-token))]
     (execute-token-request (:access-uri consumer)
                            (build-oauth-token-request consumer
                                                       (:access-uri consumer)
                                                       unsigned-oauth-params
                                                       nil
                                                       (:oauth_token_secret expired-token)
                                                       (:token-request options))
                            nil
                            options))))

(defn xauth-access-token
  "Request an xAuth access token with a username and password."
  ([consumer username password]
   (xauth-access-token consumer username password {}))
  ([consumer username password options]
   (let [[nonce timestamp] (oauth-request-values options)
         request (build-xauth-access-token-request* consumer
                                                     nil
                                                     username
                                                     password
                                                     nonce
                                                     timestamp
                                                     (:token-request options))]
     (execute-token-request (:access-uri consumer)
                            request
                            (:form-params request)
                            options))))
