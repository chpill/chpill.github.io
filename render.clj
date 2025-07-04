(ns chpill.over-engineering-log.publish
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [hiccup2.core :as hiccup2])
  (:import (java.io File)))

;; Redundant with the meta of the index.html?
(def site-title "Chpill's (Over) Engineering Log")
(def site-url "https://chpill.github.io")

(defn head [title]
  [:head
   [:title title]
   [:meta {:charset "utf-8"}]
   [:link {:rel "stylesheet" :href "/assets/pandoc-gfm.css"}]])

(def header
  [:nav {:style {:display "flex"
                    :align-items "center"
                    :justify-content "space-around"}}
   [:h4 [:a {:href "/"} site-title]]
   [:a {:href "/en/feed.xml"}
    [:svg {:width "30px" :height "30px"}
     [:use {:href "/assets/atom-feed-icon.svg#icon"}]]]])

;; TODO make an article footer with a link to the source control, for easy
;; feedback on spelling, typos and so on.
(def footer
  [:footer {:style {:text-align "center" :margin-top "3rem"}}
   [:hr]
   [:p [:small "Made with " [:a {:href "https://pandoc.org/"} "Pandoc"]]]])

(defn page [main-content title]
  (str "<!DOCTYPE html>\n"
       (hiccup2/html [:html (head title)
                      [:body header [:main main-content] footer]])))

(defn article-page [{:keys [title published inner-html]}]
  (page (list [:h1 title]
              [:p [:i published]]
              (hiccup2/raw inner-html))
        title))

(defn make-posts-data [dir-path]
  (let [hack-template-path "/tmp/extract-meta-hack.pandoc-tpl"
        _ (spit hack-template-path "${meta-json}")
        posts-data
        (->> (.listFiles (io/file dir-path))
             (map #(let [filename (.getName ^File %)
                         source-path (str dir-path "/" filename)
                         post-inner-meta
                         (-> (sh "pandoc" "--template" hack-template-path source-path)
                             :out
                             (json/parse-string true))
                         post-inner-html (:out (sh "pandoc" "--from=gfm" source-path))]
                     (-> post-inner-meta
                         (assoc :inner-html post-inner-html
                                :source-path source-path
                                :slug (str/replace filename #"\.md$" "")))))
             (sort-by :published (comp - compare)))]
    posts-data))

(comment (make-posts-data "en/posts")
         (article-page (first (make-posts-data "en/posts"))))

(defn to-href [source-path]
  (str "/" (str/replace source-path ".md" ".html")))

(defn toc [posts-data]
  [:div
   [:h3 "All posts"]
   (into [:ul]
         (map (fn [{:as post-data :keys [source-path published title]}]
                [:li [:a {:href (to-href source-path)}
                      published " - " title]]))
         posts-data)])

(defn to-iso-datetime [iso-local-date]
  (str iso-local-date "T00:00:00Z"))

(comment (to-iso-datetime "2023-11-18"))

;; check out https://github.com/tonsky/tonsky.me/blob/main/src/site/pages/atom.clj#L26
(defn feed [lang posts]
  (into [:feed {:xmlns "http://www.w3.org/2005/Atom"}
         [:title site-title]
         [:link {:href site-url :rel "self"}]
         [:id site-url]
         [:updated (->> posts (mapcat (juxt :published :updated)) sort last to-iso-datetime)]
         [:author
          [:name "Etienne Spillemaeker"]
          [:uri site-url]]]
        (map (fn [{:keys [published updated title slug inner-html source-path]
                   :or {updated published}}]
               [:entry
                [:title title]
                [:published (to-iso-datetime published)]
                [:updated (to-iso-datetime updated)]
                ;; [:id (str "tag:" site-url "," published ":" slug)]
                [:id (str site-url (to-href source-path))]
                [:content {:type "html" "xml:lang" lang} inner-html]]))
        posts))

(defn hiccup->xml-str [feed-hiccup]
  (->> feed-hiccup
       (hiccup2/html {:mode :xml :escpape-string? false})
       (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>")))

(comment (feed "en" (make-posts-data "en/posts"))
         ;; copy this into https://validator.w3.org/feed/#validate_by_input
         (spit "/tmp/feed3.xml"
               (hiccup->xml-str (feed "en" (make-posts-data "en/posts")))))

(def pub-dir "publish")
(def lang "en")
(def posts-data (filter :published (make-posts-data "en/posts")))

(do
  (sh "mkdir" "-p" (str pub-dir "/assets"))
  (sh "cp" "pandoc-gfm.css" (str pub-dir "/assets/"))
  (sh "cp" "atom-feed-icon.svg" (str pub-dir "/assets/"))
  (sh "mkdir" "-p" (str pub-dir "/" lang "/posts"))
  (spit "publish/index.html"
        (page [:div
               (hiccup2/raw (:out (sh "pandoc" "--from=gfm" "index.md")))
               (toc posts-data)]
              site-title))
  (spit (str pub-dir "/" lang "/feed.xml")
        (hiccup->xml-str (feed lang posts-data)))
  (doseq [{:as post-data :keys [source-path]} posts-data]
    (spit (str pub-dir (to-href source-path))
          (article-page post-data))))
