(defproject mdb-test-yaml-driver "0.1.0-SNAPSHOT"
  :description "YAML driver for MDB Runtime Tester"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure     "1.10.1"]
                 [mdb-runtime-tester-core "0.1.0-SNAPSHOT"]
                 [clj-commons/clj-yaml    "0.7.0"]]
  :main ^:skip-aot mdb-test-yaml-driver.core
  :local-repo "../local-m2"
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
