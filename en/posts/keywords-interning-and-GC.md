---
title: Clojure keywords interning and garbage collection
author: Etienne Spillemaeker
---


TODO make a small demo synthetically creating a lot of keywords, then triggering
a GC in VisualVM, then creating a new keyword to trigger the pruning of the
keyword cache.

```clojure
(let [private-field (.getDeclaredField clojure.lang.Keyword "table")]
    (.setAccessible private-field true)
    (count (.get private-field nil)))
```

* Interned with "classic" references in August 2007 ([commit](https://github.com/clojure/clojure/commit/f93cacece6c5bb48d20fdbbf7b228fcdb1477981#diff-de61670e4aaaa472749d1982e665f1bc7ac6f1aa0cdd601e3c1f7a4f767fc8b2L30))
* Interned with soft references in July 2010 ([commit](https://github.com/clojure/clojure/commit/02559a4aad442253b601870f7c9aa04c91baf235))
* Interned with weak references in marsh 2011 ([commit](https://github.com/clojure/clojure/commit/5ee542d3de7e22d68e923c0f9c63267960cd1647))

([Here's a nice explanations of the difference between a soft and a weak references][3])



I read a long time ago that when parsing some user input data such as the url
encoded query and form parameters of HTTP requests, one should not use the
"keywordize" options. The reasoning was that because keywords are interned, an
attacker could send a stream of requests with unique parameters to be interned
as keyword, and progressively fill up the process memory.

And so, I religiously avoided the [keyword-params][1] middleware from Ring,
until the day I looked at the collection of default settings for Ring
middlewares in [ring-defaults][2], and discovered that the middleware was used
by default. And it had been for, hmmm, [more than 10 years][3]... At that point,
I was (at last!) starting to doubt. And I finally did what I should have done
from the start: an experiment. As it turns out, it's extremely easy to measure
in this case, thanks to [oha][oha] and [visualvm][visualvm].

First, let's make a very small Ring server with that will "keywordize" any query
parameters in the URL.

```clojure
(clojure.repl.deps/add-libs '{ring/ring-defaults {:mvn/version "0.5.0"}
                              ring/ring-jetty-adapter {:mvn/version "1.13.0"}})

(require '[ring.middleware.defaults :refer [wrap-defaults site-defaults]]
         '[ring.adapter.jetty :refer [run-jetty]])

(-> (fn [{:keys [params]}]
      {:status 200
       :headers {"content-type" "text/html"}
       :body (str "<h1>query-params: " params "</h1>")})
    (wrap-defaults site-defaults)
    (run-jetty {:port 4321 :join? false}))
```

Once that is running, check that it works in your web browser of choice. Then
launch VisualVM, find the jvm process the ring server lives in, and go the the
"Monitor" tab, which displays the heap usage of the process.

Finally, we will use [oha][oha] to send as many HTTP GET requests to our server
as it can for 1 minute, with a random URL query parameter name and a fixed
value:

```
oha -z 1m --rand-regex-url 'http://localhost:4321\?[a-z]{10}=plop'
```

You should see a roller coaster happening in Visual VM during this long but
entertaining minute. And it should be pretty clear by the end that, no, the
memory of the process did not get out of control, with a peak usage at 358MiB on
my system. After the end of the run, I triggered a manual GC in the visual VM
interface, an saw the heap usage go back down its starting point (around 15MiB
in my case).

Oha indicates that the server handled over 3.8 million requests during the 1
minute test, let's check how many keywords are currently "interned":


As they say: *Measure, don't guess*

** When you do it 2 times, the keywords the cache is not garbage collected anymore? => if the keyword was already interned, the cache is not cleared (validated)


[1]: https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/keyword_params.clj
[2]: https://github.com/ring-clojure/ring-defaults/
[3]: https://github.com/ring-clojure/ring-defaults/commit/f070c9dca4116c75840818e2ae7bf6cd132dc500
[oha]: https://github.com/hatoo/oha
[visualvm]: https://visualvm.github.io/
[4]: https://stackoverflow.com/questions/299659/whats-the-difference-between-softreference-and-weakreference-in-java
