# Getting a feel for closeables

_2023-11-05_

The other day, I stumbled upon [this article by Maciej Szajna][1] which presents
a minimalist way to declare and manage runtime state in your Clojure programs.
Having used [Components][2], [Integrant][3] and then [Clip][4], this felt almost
like cheating. Can something so simple actually work? Well, it actually does
pretty well. Let's illustrate that by implementing a web server, pretty
barebone, but with some "reloaded workflow". This won't be much more than a
special case for the pattern demonstrated in [Maciej's article][1], but it
will serve as a foundation to build upon in future posts.

## A barebone webserver

If you want to evaluate the code for yourself, you can find the following
examples in [this repository][5].

First, let's add the `closeable` helper:

```clj
(defn closeable
  ([value] (closeable value identity))
  ([value close]
   (reify
     clojure.lang.IDeref (deref [_] value)
     java.io.Closeable (close [_] (close value)))))
```

Now, let's add a very basic webserver, that shows only one page. It increments
and displays a counter each time it is served:

```clj
(require '[ring.adapter.jetty :refer [run-jetty]])

(defn run-with-webserver [config f]
  (with-open
    [counter (closeable (atom 42))
     handler (closeable (fn [_req]
                          {:status 200
                           :body (str "Counter: "
                                      (swap! @counter inc))}))
     webserver (closeable (run-jetty @handler {:port (:port config)
                                               :join? false})
                          #(.stop %))]
    (f @webserver)))
```

Compared to [Maciej's article][1], you may notice 2 main differences in this
example:

1. It does not return a function that closes over the configuration, and it does
   not bother building an associative map with every binding declared in
   `with-open`.

2. `run-with-webserver` is much more specific than the generic `with-my-system`.
    The main side effect of calling this function is to open up a port on the
    host where it is run, so I prefer to narrow the meaning to reflect that.

We can see it in action by evaluating the following expression:

```clj
(run-with-webserver {:port 54321}
                    (fn [_webserver]
                      (println "The server is live:"
                               (slurp "http://localhost:54321"))))
```

It should print `The server is live: Counter: 43` in your REPL (among other log
statements). That is well and good, but if you try to access
[http://localhost:54321](http://localhost:54321) from your browser, you'll see
that the server is not actually running anymore. As explained in [Maciej's
article][1], once the function we pass to `run-with-webserver` returns, the
opened resources are released. In order to keep the server running indefinitely,
we can use [`.join` on the Jetty Server][6].

```clj
(run-with-webserver {:port 54321} #(.join %))
```

__NB: Depending on your tooling, evaluating the previous expression can block
your REPL. You'll need to interupt the evaluation to stop the webserver.__

## Testing

It felt odd at first having the "run" function not do its task indefinitely.
After all, Clojure was made for [situated programs][7], long running processes
tangled with outside world. But it makes it a lot easier to work with our system
in various ways. Testing is effortless for example:

```clj
(require '[clojure.test :refer [deftest is run-tests]])

(deftest test-webserver
  (let [url "http://localhost:12345"]
    (is (thrown? java.net.ConnectException (slurp url)))
    (run-with-webserver {:port 12345}
                        (fn [_webserver]
                          (is (= (slurp url) "Counter: 43"))
                          (is (= (slurp url) "Counter: 44"))))
    (is (thrown? java.net.ConnectException (slurp url)))))

(comment (run-tests))
```

## The famous "reloaded" workflow

Finally, let's add some convenient handles (in `user.clj`) to play with our
webserver from the REPL.

```clj
(defonce *live-server (atom (future ::not-initialized-yet)))

(defn start! []
  (reset! *live-server (future (run-with-webserver {:port 54321}
                                                   #(.join %)))))
(defn stop! []
  (future-cancel @*live-server))

(comment (start!)
         (stop!))
```

After evaluating this code and calling `(start!)`, you should be able to visit
[http://localhost:54321](http://localhost:54321) and see the counter for
yourself. But do not evaluate this by hand! Your editor probably has some
integration with tools.namespace via a plugin. For example for Emacs and Cider,
I usually declare a `.dir-locals.el` at the root of the projet with the
following:

```emacs
((clojure-mode . ((cider-ns-refresh-before-fn . "user/stop!")
                  (cider-ns-refresh-after-fn  . "user/start!"))))
```

I can then call `cider-ns-refresh` to stop my system, refresh the namespaces,
and start the system again. Notice that I still `defonce` the reference to the
live server, so that I do not lose it if I eval the whole buffer. I also provide
a dummy future in that reference so that I can call `cider-ns-refresh` to start
my system the first time.


## Conclusion

Even though I do not yet have had a lot experience with this approach, there are
already [positive reports][8] of its use, so I'm eager to use it in my projects
going forward. In the [next post][9], we'll explore a slightly meatier example
of web server.


[1]: https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98
[2]: https://github.com/stuartsierra/component
[3]: https://github.com/weavejester/integrant/
[4]: https://github.com/juxt/clip
[5]: https://github.com/chpill/demo-closeable/tree/master/barebone-webserver
[6]: https://eclipse.dev/jetty/javadoc/jetty-11/org/eclipse/jetty/server/Server.html#join()
[7]: https://youtu.be/2V1FtfBDsLU?t=646
[8]: https://www.juxt.pro/blog/clojure-in-griffin/
[9]: /en/posts/rapid-feedback-webdev-with-reitit.html
