(ns build
  "Build + Clojars deploy for jose-clj (tools.build + deps-deploy).

   Usage:
     clojure -T:build jar
     clojure -T:build deploy   ; needs CLOJARS_USERNAME / CLOJARS_PASSWORD"
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'net.clojars.savya/clj-oauth)
(def version "1.6.1")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "pom.xml"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/jsavyasachi/clj-oauth"
                      :connection "scm:git:https://github.com/jsavyasachi/clj-oauth.git"
                      :developerConnection "scm:git:ssh://git@github.com/jsavyasachi/clj-oauth.git"
                      :tag (str "v" version)}
                :pom-data [[:description "OAuth support for Clojure"]
                           [:url "https://github.com/jsavyasachi/clj-oauth"]
                           [:licenses
                            [:license
                             [:name "Simplified BSD License"]
                             [:url "https://opensource.org/licenses/BSD-2-Clause"]
                             [:distribution "repo"]]]]})
  (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
  (b/jar {:class-dir class-dir :jar-file jar-file})
  (println "Wrote" jar-file))

(defn deploy [_]
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
