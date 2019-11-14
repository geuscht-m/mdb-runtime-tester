(defproject prototyping "0.1.0-SNAPSHOT"
  :description "Sketches for a prototype of a mongodb runtime deployment tester"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.novemberain/monger "3.5.0"]
                 [com.taoensso/timbre        "4.10.0"] ;; Mainly used to get a chance to deal with the chatty Java driver
                 [com.fzakaria/slf4j-timbre  "0.3.14"] ;; Attempt to send all log output through timbre
                 [org.slf4j/jul-to-slf4j     "1.7.14"]
                 [org.clojure/tools.trace    "0.7.10"]]
 
  :main prototyping.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
