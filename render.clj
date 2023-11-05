(ns chpill.over-engineering-log.publish
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
   [:nav
    (into [:p
           [:span {:style {:padding-right "2rem"}}
            "Over-Engineering Log"]]
          (interpose " - ")
          [[:a {:href "/"} "about"]
           [:a {:href "/en/posts"} "posts"]])]])

;; TODO make an article footer with a link to the source on github, for easy
;; feedback on spelling, typos and so on.
(def footer
  [:footer {:style {:text-align "center" :margin-top "3rem"}}
   [:hr]
   [:p [:small "Made with " [:a {:href "https://hackage.haskell.org/package/pandoc-cli"} "Pandoc"]]]])

(defn page [main-content title]
  (str "<!DOCTYPE html>\n"
       (hiccup2/html [:html (head title)
                      [:body header [:main main-content] footer]])))

(def pub-dir "publish")

(defn posts-paths [lang sub-path]
  (let [source-path (str lang "/posts/" sub-path)]
    {:source-path source-path
     :dest-path (str pub-dir "/" (str/replace source-path ".md" ".html"))}))

(defn toc [lang posts]
  (page
   (hiccup2/html
    [:div
     [:h1 "All posts"]
     (into [:ul]
           (map (fn [[sub-path title]]
                  [:li [:a {:href (subs (:dest-path (posts-paths lang sub-path))
                                        (count pub-dir))}
                        title]]))
           posts)])
   "All posts"))

(defn spit-page! [{:keys [source-path dest-path]} title]
  (spit dest-path
        (page (hiccup2/raw
               (:out (sh "pandoc" "--from=gfm" source-path)))
              title)))

;; TODO find a way to extract metadata from the markdown files themselves, for example, date, title...
(def posts-entries {"en" [["getting-a-feel-for-closeables.md" "Getting a feel for closeables"]
                          ;; TODO 1 finish this post, and link it from the first one
                          #_["rapid-feedback-webdev-with-closeables-and-reitit.md" "Rapid feedback webdev with closeables and reitit"]
                          ;; TODO backport old medium post about transducer on this blog?
                          ;; This would need a disclaimer at the begining, explaining that clojure.core/into has since been "fixed". A study of that fix could also make a good article.
                          ;; https://medium.com/@chpill_/deep-dive-into-a-clojure-transducer-3d4117784fa6
                          ]})

(do
  (sh "mkdir" "-p" (str pub-dir "/assets"))
  (sh "cp" "pandoc-gfm.css" (str pub-dir "/assets/"))
  (spit-page! {:source-path "index.md" :dest-path "publish/index.html"} "Over-Engineering Log")
  ;; TODO mimick the source directories structure instead of reproducing it manually
  (doseq [[lang posts] posts-entries]
    (sh "mkdir" "-p" (str pub-dir "/" lang "/posts"))
    (spit (str pub-dir "/" lang "/posts/index.html")
          (toc lang posts))
    (doseq [[sub-path title] posts]
      (spit-page! (posts-paths lang sub-path) title))))
