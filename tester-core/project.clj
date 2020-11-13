(defproject mdb-runtime-tester-core "0.1.0-SNAPSHOT"
  :description "MongoDB runtime deployment tester that introduces various runtime error conditions. Main library."
  :url "https://github.com/geuscht-m/mdb-runtime-tester"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [;;[lein-voom "0.1.0-20190525_204305-g28f604d"]
            ;;[lein-git-deps "0.0.1-SNAPSHOT"]
            [lein-eftest "0.5.9"]]
  :dependencies [[org.clojure/clojure           "1.10.0"]
                 [org.mongodb/mongodb-driver-sync "3.12.6"]
                 [clojurewerkz/urly             "1.0.0"]
                 [com.taoensso/timbre           "4.10.0"] ;; Mainly used to get a chance to deal with the chatty Java driver
                 [com.fzakaria/slf4j-timbre     "0.3.14"] ;; Attempt to send all log output through timbre
                 [org.slf4j/jul-to-slf4j        "1.7.14"]
                 ;;[org.clojure/tools.trace       "0.7.10"]
                 [clj-ssh                       "0.5.14"]  ;; SSH client for remote execution
                 [net.n01se/clojure-jna         "1.0.0" ] ;; JNA interface to native libraries
                 [clj-pem-decoder               "0.1.0-SNAPSHOT"]]
  ;;:git-dependencies [["https://github.com/geuscht-m/clj-pem-decoder.git"]]
  ;;:local-repo "../local-m2"
  :eftest { :multithread? false :test-warn-time 20000 }
  ;;:main tester-core.core
  :target-path "target/%s"
  ;;:profiles {:uberjar {:aot :all}})
  )
