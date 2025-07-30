(ns render.feed-test
  (:require [clojure.xml :as xml])
  (:gen-class))

(defn -main [old-site new-site & args]
  (assert
   (= (xml/parse (str old-site "/en/feed.xml"))
      (xml/parse (str new-site "/en/feed.xml")))))
