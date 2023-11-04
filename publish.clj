(ns chpill.publish
  (:require [clojure.java.shell :refer [sh]]
            [clojure.java.io    :as io]
            [clojure.string :as str]
            [hiccup2.core :as hiccup2]))


(defn head [title]
  [:head
   [:title title]
   [:meta {:charset "utf-8"}]
   [:link {:rel "stylesheet" :href "/assets/pandoc-gfm.css"}]])

(def header
  [:header
   [:a {:href "/"} "chez chpill"]
   [:a {:href "/en/blog"} "blog"]])

(def footer
  [:footer {:style {:text-align "center" :margin-top "3rem"}}
   [:hr]
   [:p [:small "Made with " [:a {:href "https://hackage.haskell.org/package/pandoc-cli"} "Pandoc"]]]])

(defn page [main-content title]
  (str "<!DOCTYPE html>\n"
       (hiccup2/html [:html (head title)
                      [:body header [:main main-content] footer]])))

(def pub-dir "publish")

(defn spit-page! [source-path title]
  (let [dest-path (str pub-dir "/" (str/replace source-path ".md" ".html"))]
    (spit dest-path
          (page (hiccup2/raw
                 (:out (sh "pandoc" "--from=gfm" source-path)))
                title))))

;; TODO find a way to extract metadata from the markdown files themselves
(def blog-entries {"en" {"getting-a-feel-for-closeables.md" "Getting a feel for closeables"}})

;; TODO research the publishing workflow described in https://github.com/mmzsource/mxmmz
(do
  (sh "mkdir" "-p" (str pub-dir "/assets"))
  (sh "cp" "pandoc-gfm.css" (str pub-dir "/assets/"))
  (spit-page! "index.md" "chez chpill")
  ;; TODO mimick the source directories structure instead of reproducing it manually
  (doseq [[lang entries] blog-entries]
    (sh "mkdir" "-p" (str pub-dir "/" lang "/blog"))
    (doseq [[sub-path title] entries]
      (spit-page! (str lang "/blog/" sub-path)
                  title))))

