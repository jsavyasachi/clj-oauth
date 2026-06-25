(defproject net.clojars.savya/clj-oauth "1.6.0"
  :url "https://github.com/jsavyasachi/clj-oauth"
  :license {:name "Simplified BSD License"
            :url "https://opensource.org/licenses/BSD-2-Clause"
            :distribution :repo}
  :description "OAuth support for Clojure"
  :scm {:name "git"
        :url "https://github.com/jsavyasachi/clj-oauth"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[commons-codec/commons-codec "1.22.0"]
                 [org.bouncycastle/bcprov-jdk18on "1.84"]
                 [org.bouncycastle/bcpkix-jdk18on "1.84"]
                 [clj-http "3.13.1"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.12.0"]]}
             :clojure-1-10 {:dependencies [[org.clojure/clojure "1.10.3"]]}
             :clojure-1-11 {:dependencies [[org.clojure/clojure "1.11.4"]]}
             :clojure-1-12 {:dependencies [[org.clojure/clojure "1.12.0"]]}}
  :aliases {"all" ["with-profile" "+clojure-1-10:+clojure-1-11:+clojure-1-12"]}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :sign-releases false
                                    :username :env/clojars_username
                                    :password :env/clojars_password}]]
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :all (constantly true)})
