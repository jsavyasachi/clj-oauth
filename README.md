# OAuth support for Clojure

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/clj-oauth.svg)](https://clojars.org/net.clojars.savya/clj-oauth)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/clj-oauth)](https://cljdoc.org/d/net.clojars.savya/clj-oauth/CURRENT)
[![test](https://github.com/jsavyasachi/clj-oauth/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/clj-oauth/actions/workflows/test.yml)

`clj-oauth` provides [OAuth](http://oauth.net) client support for Clojure programs.

This is a maintained fork of [drone29a/clj-oauth](https://github.com/drone29a/clj-oauth),
published as `net.clojars.savya/clj-oauth`. It releases the previously unreleased
HMAC-SHA256 support and moves onto current, security-supported dependencies
(BouncyCastle `jdk18on`, clj-http 3.13, Clojure 1.12).

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://clojure.org/guides/deps_and_cli"><img src="https://img.shields.io/badge/deps.edn-5881D8?style=flat&logo=clojure&logoColor=fff" alt="deps.edn" /></a>
<a href="https://clojure.github.io/tools.build/"><img src="https://img.shields.io/badge/tools.build-5881D8?style=flat&logo=clojure&logoColor=fff" alt="tools.build" /></a>
<a href="https://github.com/dakrone/clj-http"><img src="https://img.shields.io/badge/clj--http-5881D8?style=flat&logo=clojure&logoColor=fff" alt="clj-http" /></a>
<a href="https://www.bouncycastle.org"><img src="https://img.shields.io/badge/Bouncy%20Castle-2D3748?style=flat" alt="Bouncy Castle" /></a>

## Installing

deps.edn:

```clojure
net.clojars.savya/clj-oauth {:mvn/version "1.6.1"}
```

Leiningen:

```clojure
[net.clojars.savya/clj-oauth "1.6.1"]
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

This runs the deterministic unit tests without credentials or network access.
The live Twitter tests are tagged `^:integration` and skipped by default.

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

## Authors

Development funded by LikeStream LLC (Don Jackson and Shirish Andhare).

Designed and developed by Matt Revelle. Contributions from Richard Newman.

Maintenance fork (2026) by Savyasachi; original:
[drone29a/clj-oauth](https://github.com/drone29a/clj-oauth).

## License

Copyright © 2009 Matt Revelle.

Distributed under the [BSD 2-Clause License](https://opensource.org/licenses/BSD-2-Clause),
preserving the original license.
