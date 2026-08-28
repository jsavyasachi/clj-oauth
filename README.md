# OAuth support for Clojure

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/clj-oauth.svg)](https://clojars.org/net.clojars.savya/clj-oauth)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/clj-oauth)](https://cljdoc.org/d/net.clojars.savya/clj-oauth/CURRENT)
[![test](https://github.com/jsavyasachi/clj-oauth/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/clj-oauth/actions/workflows/test.yml)

`clj-oauth` provides [OAuth](http://oauth.net) client support for Clojure programs.

This maintained fork of [drone29a/clj-oauth](https://github.com/drone29a/clj-oauth) is
published as `net.clojars.savya/clj-oauth`. It includes unreleased HMAC-SHA256
support. It uses current dependencies with security support: BouncyCastle `jdk18on`,
clj-http 3.13, and Clojure 1.12.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://clojure.org/guides/deps_and_cli"><img src="https://img.shields.io/badge/deps.edn-5881D8?style=flat&logo=clojure&logoColor=fff" alt="deps.edn" /></a>
<a href="https://clojure.github.io/tools.build/"><img src="https://img.shields.io/badge/tools.build-5881D8?style=flat&logo=clojure&logoColor=fff" alt="tools.build" /></a>
<a href="https://github.com/dakrone/clj-http"><img src="https://img.shields.io/badge/clj--http-5881D8?style=flat&logo=clojure&logoColor=fff" alt="clj-http" /></a>
<a href="https://www.bouncycastle.org"><img src="https://img.shields.io/badge/Bouncy%20Castle-2D3748?style=flat" alt="Bouncy Castle" /></a>

## Installing

deps.edn:

```clojure
net.clojars.savya/clj-oauth {:mvn/version "1.9.0"}
```

Leiningen:

```clojure
[net.clojars.savya/clj-oauth "1.9.0"]
```

## Building

Build the jar:

```shell
clojure -T:build jar
```

Deploy to Clojars:

```shell
clojure -T:build deploy
```

## Running tests

```shell
clojure -M:test
```

This runs deterministic unit tests without credentials or network access.
Live Twitter tests have the `^:integration` tag and do not run by default.

## Client example

```clojure
(require '[oauth.client :as oauth])

(def consumer
  (oauth/make-consumer <consumer-token>
                       <consumer-token-secret>
                       "https://api.twitter.com/oauth/request_token"
                       "https://api.twitter.com/oauth/access_token"
                       "https://api.twitter.com/oauth/authorize"
                       :hmac-sha1))

;; The signature method may be :hmac-sha1 (the default), :hmac-sha256,
;; :rsa-sha1, :rsa-sha256, or :plaintext. RSA private keys may be given in
;; either PKCS#1 or PKCS#8 PEM format.

;; A callback URI is not required for desktop applications.
(def request-token (oauth/request-token consumer <callback-uri>))

(oauth/user-approval-uri consumer (:oauth_token request-token))

(def access-token-response
  (oauth/access-token consumer request-token <verifier>))

(def user-params {:status "posting from #clojure with #oauth"})
(def credentials
  (oauth/credentials consumer
                     (:oauth_token access-token-response)
                     (:oauth_token_secret access-token-response)
                     :POST
                     "https://api.twitter.com/1.1/statuses/update.json"
                     user-params))

(http/post "https://api.twitter.com/1.1/statuses/update.json"
           {:query-params (merge credentials user-params)})
```

## Other provider flows

OAuth 1.0a providers return a refreshable token when they support refresh
tokens. Pass the access token response to `refresh-token`:

```clojure
(def refreshed (oauth/refresh-token consumer access-token-response))
```

For providers such as Twitter that support xAuth, use a dedicated credentials
flow. Keep usernames and passwords in a secret store; the values below are
placeholders only:

```clojure
(def xauth-token (oauth/xauth-access-token consumer <username> <password>))
```

RSA consumers take the private-key PEM string as the consumer secret. Load it
from a protected file or secret manager, rather than committing it:

```clojure
(require '[clojure.java.io :as io])
(def rsa-consumer
  (oauth/make-consumer <consumer-key>
                       (slurp (io/file <private-key-path>))
                       <request-uri> <access-uri> <authorize-uri>
                       :rsa-sha256))
```

Use `:rsa-sha1` only when required by a legacy provider. Callback handling is
the same for every signature method: pass the callback URI to
`request-token`, send the user to `user-approval-uri`, then pass the returned
verifier to `access-token`.

## Signed requests

For protected resources, `signed-request` generates the OAuth nonce and
timestamp, signs the request, adds the Authorization header, and executes it
through clj-http. Put additional OAuth fields in `:oauth-params`; all other
options are passed through to clj-http. Query and form parameters are included
in the signature.

Parameter maps remain supported. Query, form, and additional OAuth parameters
may also be supplied as ordered key-value pairs, for example
`[["tag" "clojure"] ["tag" "oauth"]]`; repeated keys are retained for
RFC 5849 signature normalization.

```clojure
(require '[oauth.client :as oauth])

(oauth/signed-request consumer
                      (:oauth_token access-token-response)
                      (:oauth_token_secret access-token-response)
                      :POST
                      "https://api.twitter.com/1.1/statuses/update.json"
                      {:form-params {:status "posting from #clojure"}
                       :headers {"X-Client" "example"}})
```

Convenience functions named `get-request`, `post-request`, `put-request`, and
`delete-request` accept the same arguments without the method parameter.

For deterministic tests or coordinated retries, signed and token requests
accept `:oauth-nonce-fn`, `:oauth-timestamp-fn`, and `:oauth-clock-fn` options.
The clock returns milliseconds and is converted to the OAuth seconds value.
Token endpoints also accept an opt-in `:token-request` map with `:method`,
`:body-encoding` (`:form`, `:query`, or `:raw`), `:content-type`, `:headers`,
and `:response-parser`; omitted options retain POST/form decoding defaults.

## Authors

Development funded by LikeStream LLC (Don Jackson and Shirish Andhare), see
[likestream.org/opensource.html](http://www.likestream.org/opensource.html).

Designed and developed by Matt Revelle. Contributions from Richard Newman.

Savyasachi maintains this fork (2026). The original is:
[drone29a/clj-oauth](https://github.com/drone29a/clj-oauth).

## License

Copyright © 2009 Matt Revelle.

Distributed under the [BSD 2-Clause License](https://opensource.org/licenses/BSD-2-Clause).
This preserves the original license.
