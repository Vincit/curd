(defproject vincit/curd "0.1.3-alpha3"
  :description "Easy and sweet crud without hassle"
  :url "https://github.com/vincit/curd"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/spec.alpha "0.1.123"]
                 [org.clojure/java.jdbc "0.7.0-alpha3"]
                 [camel-snake-kebab "0.4.0"]
                 [io.aviso/pretty "0.1.33"]]
  :profiles {:dev {:dependencies [[ragtime "0.6.3"]
                                  [org.postgresql/postgresql "9.4.1210.jre7"]]
                   :plugins [[com.jakemccrary/lein-test-refresh "0.16.0"]]}}
  :test-refresh {:changes-only true})