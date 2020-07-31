;; Basic helper functions needed for OS interaction
;;
(ns prototyping.os-helpers
  (:require [net.n01se.clojure-jna  :as jna])
  (:import  java.lang.ProcessBuilder))

(defn get-hostname
  "Get the hostname of the current host"
  []
  (-> (java.net.InetAddress/getLocalHost)
      .getCanonicalHostName))

(defn spawn-process
  "Helper function that starts an external process on the local machine"
  [process-parameters]
  (.waitFor (-> (ProcessBuilder. process-parameters) .inheritIO .start)))

(defn kill-local-process
  "Terminate a local process either via signals 9 or 15"
  [pid force]
  (if force
    (jna/invoke Integer c/kill pid 9)
    (jna/invoke Integer c/kill pid 15)))

  
