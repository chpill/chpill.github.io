(ns test
  (:require [clojure.xml :as xml]
            [clojure.data]))


;; true
(= (xml/parse "./publish/en/feed.xml")
   (xml/parse "https://chpill.github.io/en/feed.xml"))

(assert (= (xml/parse "./plouf/en/feed.xml")
           (xml/parse "./result/en/feed.xml")))
