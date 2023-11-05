# Rapid feedback webdev with closeables and reitit

_2023-11-xx_ TODO

In the [previous post][1], we got aquainted with `closeables` to manage runtime
state in our Clojure programs. It worked like a charm, but it was (vonluntarily)
excessively simple. In real world applications, it can be painful to stop and
start a system every time you make a change, because you need to populate a
development database with mocked data for example. That's why [Integrant][2]
features a suspend/resume mechanism, to get a kind of "soft reload" of your
system. Obviously, we cannot do that here, but let me share a trick mentionned
the [Reitit][3] docs that will help you if you are building a website or an api
server.


## Simple routing fragments

For this example, we'll to create a bunch of files to mimick a more complex
project structure. The code is available in this [repo][4]. First, let's create
a namespace where we'll put the incrementing counter function from last time.

```clj
(ns demo-closeable.deeply-nested)


(defn real-worldish-function [{:as _req :keys [counter]}]
  {:status 200
   :body (str "Real-worldish counter: " (swap! counter inc))})

(defn routes []
  ["/counter" real-worldish-function])
```

The counter is now provided through a custom key associated to the request
argument. Notice that we provide a fragment of a reitit routing table, but
we do it in a seemingly strange way, by wrapping it in a `routes` function of no
arguments. We'll explain why in a short while. Let's add another namespace that
will require the first one.


```clj
(ns demo-closeable.nested
  (:require [demo-closeable.deeply-nested :as deeply-nested]))


(defn routes []
  [["/ping" (fn [_req] {:status 200
                        :body "nested pong"})]
   ["/deeply-nested" (deeply-nested/routes)]])
```

Again, the same weird pattern. The `routes` function of the previous namespace
is called inside the routing fragment of the current namespace. Let's go up
another level, and create a namespace that will build the complete routing
table.


## Root handler

```clj
(ns demo-closeable.root-handler
  (:require [demo-closeable.nested :as nested]
            [reitit.ring :as rr]))


(defn routes []
  [""
   ["/nested" (nested/routes)]
   ["/ping" (fn [_req] {:status 200
                        :body "pong"})]])

(defn inject-counter [counter]
  (fn [handler]
    (fn inject-counter-middleware [req]
      (handler (assoc req :counter counter)))))

(defn make [counter]
  (rr/ring-handler (rr/router (routes))
                   (rr/create-default-handler
                    {:not-found (constantly
                                 {:status 404
                                  :body "Real-worldish 404 page"})})
                   {:middleware [(inject-counter counter)]}))

(defn make-reloading [counter]
  (rr/reloading-ring-handler #(make counter)))
```

Things get more interesting here:

* The `inject-counter` function returns a very simple ring middleware that will
expose our modest living "source of truth" to any handler below.
* The `make` function turns our routing table into a fully fledged handler.
* The `make-reloading` function wraps `make` TODO.

Note that `make` and `make-reloading` must have the same signatures for this
trick to work. If there was another piece of runtime state that we wanted to
expose to our handlers, we'd have to also pass it there. For example, if we
added a `cache`, the signatures would become `(defn make [counter cache] ...)`
and `(defn make-reloading [counter cache] ...)`.


## The system

And now, the entry point of our webserver, the final namespace:

```clj
(ns demo-closeable.meatier-webserver
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [demo-closeable.root-handler :as handler]))

(defn closeable
  ([value] (closeable value identity))
  ([value close]
   (reify
     clojure.lang.IDeref (deref [_] value)
     java.io.Closeable (close [_] (close value)))))

(defn run-with-webserver [config f]
  (with-open
    [counter (closeable (atom 42))
     handler (closeable ((if (:dev config) handler/make-reloading handler/make)
                         @counter))
     webserver (closeable (run-jetty @handler {:port (:port config)
                                               :join? false})
                          #(.stop %))]
    (f @webserver)))
```


Compared to our previous post, the only difference here is our handler, where we
check the config to decide which flavor of handler we want: `(if (:dev config)
handler/make-reloading handler/make)`. Use the new config parameter we
introduced to launch the server:

```clj
(run-with-webserver {:port 54321 :dev true}
                    #(.join %))
```

Open
[http://localhost:54321/nested/deeply-nested/counter](http://localhost:54321/nested/deeply-nested/counter)
in your browser, you should see our new counter page.

Now, try changing and re-evaluating the leaf handlers and the routing fragments.
You'll see that the changes are picked up as soon as you refresh the page in
your browser.


```clj
(ns demo-closeable.meatier-webserver-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [demo-closeable.deeply-nested :as dn]
            [demo-closeable.meatier-webserver :refer [run-with-webserver]]))


(deftest test-reloading-webserver
  (let [port 12345
        url (str "http://localhost:" port "/nested/deeply-nested/counter")]
    (run-with-webserver
     {:port port :dev true}
     (fn [_webserver]
       (is (= "Real-worldish counter: 43" (slurp url)))

       (let [original-function-value dn/real-worldish-function]
         (alter-var-root
          #'dn/real-worldish-function
          (fn [_f] (fn [_req] {:status 200
                               :body "LOCALLY RELOADED!"})))

         (is (= "LOCALLY RELOADED!" (slurp url)))

         (alter-var-root #'dn/real-worldish-function
                         (fn [_f] original-function-value))

         (is (= "Real-worldish counter: 44" (slurp url))))))))
```

There you have it, a localized code reload that keeps the existing runtime state
of the system. Any work happening on the handlers of the application will rarely
need a full system reload.


## Conclusion

We have illustrated an web project structure that allows for fast feedback
without full system reloads. The structure should also be very easy to extend
upon. Although it was specific to reitit, it can probably be replicated with any
routing library out there, and hopefully, to other use cases as well.


[1]: /en/posts/getting-a-feel-for-closeables.html
[2]: https://github.com/weavejester/integrant/
[3]: https://github.com/metosin/reitit/
[4]: https://github.com/chpill/demo-closeable/tree/master/meatier-webserver
