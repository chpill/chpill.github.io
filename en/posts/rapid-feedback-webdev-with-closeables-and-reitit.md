# Rapid feedback webdev with closeables and Reitit

_2023-11-xx_ TODO

In the [previous post][1], we got aquainted with `closeables` to manage runtime
state in our Clojure programs. It worked like a charm, but it was (purposefully)
very simple. In real world applications, it can be painful to stop and start a
system every time you make a change (for example: if you need to populate a
development database with a lot of data). That's why [Integrant][2] features a
suspend/resume mechanism, to get a kind of "soft reload" of your system.
Obviously, we cannot do that here, but let me share a DIY trick mentionned in
the [Reitit documentation][3] that will help keep your feedback loop short as
you are building a website or an api server. We'll then dig a little deeper into
why it works and when it doesn't, so that you may reuse this pattern outside of
webdev as well.


## Simple routing fragments

For this example, we'll create a bunch of files to mimick a more complex project
structure. The code is available in this [repository][4]. First, let's create a
namespace where we'll put the incrementing counter function from the [previous
post][1].

```clj
(ns demo-closeable.deeply-nested)


(defn real-worldish-function [{:as _req :keys [counter]}]
  {:status 200
   :body (str "Real-worldish counter: " (swap! counter inc))})

(defn routes []
  ["/counter" real-worldish-function])
```

The counter atom is now retrieved through a custom key associated to the request
argument. Notice that we provide a fragment of a Reitit routing table, but we do
it in a seemingly strange way, by wrapping it in a `routes` function of no
arguments. We'll explain why later. Let's add another namespace that will
require the first one.


```clj
(ns demo-closeable.nested
  (:require [demo-closeable.deeply-nested :as deeply-nested]))


(defn routes []
  [["/ping" (fn [_req] {:status 200
                        :body "nested pong"})]
   ["/deeply-nested" (deeply-nested/routes)]])
```

Again, the same pattern. The `routes` function of the previous namespace is
called inside the routing fragment of the current namespace. Let's go up another
level, and create a namespace that will build the complete routing table.


## Root handler

```clj
(ns demo-closeable.root-handler
  (:require [demo-closeable.nested :as nested]
            [reitit.ring :as rr]))


(defn complete-routes []
  [""
   ["/nested" (nested/routes)]
   ["/ping" (fn [_req] {:status 200
                        :body "pong"})]])

(defn inject-counter [counter]
  (fn [handler]
    (fn inject-counter-middleware [req]
      (handler (assoc req :counter counter)))))

(defn make [counter]
  (rr/ring-handler (rr/router (complete-routes))
                   (rr/create-default-handler
                    {:not-found (constantly
                                 {:status 404
                                  :body "Real-worldish 404 page"})})
                   {:middleware [(inject-counter counter)]}))

(defn make-reloading [& args]
  (rr/reloading-ring-handler #(apply make args)))
```

Now, we get to more interesting functions:

* `complete-routes` returns the complete routing table of the application.
* `inject-counter` returns a synchronous ring middleware that will provide the
  counter atom to any handler it is applied to.
* `make` turns the routing table into a fully fledged handler, with the previous
  middleware being applied globally.
* `make-reloading` calls a handy development helper from Reitit: the
  [reloading-ring-handler][5]. It will call `make` to recreate the handler on
  every request.

Because we have made every routing fragment a function, when `make` is called on
each request, it in turn call every one of them, and the resulting complete routing
table will contain the last evaluated value of every `routes` and leaf handler,
however nested they may be.


## The system

And now, the entry point of the webserver:

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
     handler (closeable ((if (:dev config)
                           handler/make-reloading
                           handler/make)
                         @counter))
     webserver (closeable (run-jetty @handler {:port (:port config)
                                               :join? false})
                          #(.stop %))]
    (f @webserver)))
```

Compared to the previous post, the only difference here is the handler, where we
check the config to decide which flavor of handler we want: `(if (:dev config)
handler/make-reloading handler/make)`. Use the new config parameter we
introduced to launch the server:

```clj
(run-with-webserver {:port 54321 :dev true}
                    #(.join %))
```

Open [http://localhost:54321/nested/deeply-nested/counter](http://localhost:54321/nested/deeply-nested/counter)
in your browser, you should see the new counter page.

Now, try changing and re-evaluating the leaf handlers and the routing fragments.
You'll see that the changes are picked up as soon as you refresh the page in
your browser. For my particular workflow with emacs and CIDER, that mean I will
simply `cider-eval-defun-at-point` to re-evaluate the top-level form I'm
currently editing, and then I can refresh the page in a browser for example.


To verify this behaviour a little more rigorously, we can write a test like
this:

```clj
(ns demo-closeable.meatier-webserver-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [demo-closeable.deeply-nested :as dn]
            [demo-closeable.meatier-webserver
             :refer [run-with-webserver]]))


(deftest test-reloading-webserver
  (let [port 12345
        url (str "http://localhost:" port
                 "/nested/deeply-nested/counter")]
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

(comment (run-tests))
```


## Where if falls short

To dig a little deeper, this works because when clojure functions are evaluated
(which specifically means "compiled" by the Clojure compiler here), a
distinction is made between symbols referencing global [vars][7] and others in
the lexical scope. You can get the full picture by reading about [Clojure's
evaluation][8]. When the function is invoked, every var in its body will be
dereferenced (or "traversed"), so you will see the last evaluated value of those
vars (unless you are compiling with [direct-linking][6] enabled, but that's not
usually the case during development).

We can observe this difference when the value of a function is captured in the
closure of another. Let's add an example of this to the `deeply-nested`
namespace:

```clj
(ns demo-closeable.deeply-nested)


(defn real-worldish-function [{:as _req :keys [counter]}]
  {:status 200
   :body (str "Real-worldish counter: " (swap! counter inc))})


(defn example-fn [_]
  {:status 200
   :body "Re-evaluate me if you can!"})

(defn smug-middleware [handler]
  (fn inner-function [req]
    (update (handler req)
            :body str " -- Sent from my server. I use Clojure btw.")))

(def captured-fn (smug-middleware example-fn))

(defn routes []
  [["/counter" real-worldish-function]
   ["/closure-issue" captured-fn]])
```

Here, we "augmented" `example-fn` using the `smug-middleware`, and `def`ed the
result in the `captured-fn` var. If you make a change to `example-fn` and
naively re-evaluate it, you will notice that the change is not picked up when
you visit [http://lol:54321/nested/deeply-nested/closure-issue](http://lol:54321/nested/deeply-nested/closure-issue).
That is because the `example-fn` var is not in the body of the `inner-function`
that was given to the routing fragment. If you need to convince yourself of this
behaviour, try changing the middleware like so:

```clj
(defn smug-middleware [handler]
  (fn inner-function [req]
    (update (example-fn req)
            :body str " -- I like hammocks and private jokes.")))
```

Do a full namespace eval, or a full system reload, and then try changing and
evaluating only `example-fn` again. This time, the change will be picked up. The
indirection of the middleware is still the same, the only difference is that we
did not use the `handler` value that was captured in the closure of the
`inner-function` (which still contains the previous value). Instead, we directly
used the var `example-fn`, which is dereferenced on every call of
`inner-function` behind the scenes. To be more precise, it works because of the
[interning of vars][9]. This is a really important thing to grasp, so be sure to
also check this [section of the official clojure REPL guide][10].


(If you are curious in seeing exactly how this plays out in the lower level
code, you can use the [clj-java-decompiler][11] to compare the 2 functions, but
it isn't vital to the point being made.)

<details>
<summary>Show me the low level stuff anyway</summary>

Here's the first version of the `smug-middleware`:

```clj
(require '[clj-java-decompiler.core :refer [decompile]])

(->> (defn smug-middleware [handler]
         (fn inner-function [req]
           (update (handler req)
                   :body str " -- Sent from my server. I use Clojure btw.")))
       decompile
       with-out-str
       (spit "/tmp/decompiled-original.java"))
```

```java
// Decompiling class: demo_closeable/deeply_nested$smug_middleware
package demo_closeable;

import clojure.lang.*;

public final class deeply_nested$smug_middleware extends AFunction
{
    public static Object invokeStatic(final Object handler) {
        return new deeply_nested$smug_middleware$inner_function__13650(handler);
    }

    @Override
    public Object invoke(final Object handler) {
        return invokeStatic(handler);
    }
}


// Decompiling class: demo_closeable/deeply_nested$smug_middleware$inner_function__13650
package demo_closeable;

import clojure.lang.*;

public final class deeply_nested$smug_middleware$inner_function__13650 extends AFunction
{
    Object handler;
    public static final Var __update;
    public static final Keyword const__1;
    public static final Var __str;

    public deeply_nested$smug_middleware$inner_function__13650(final Object handler) {
        this.handler = handler;
    }

    @Override
    public Object invoke(final Object req) {
        final IFn fn = (IFn)__update.getRawRoot();
        final Object invoke = ((IFn)this.handler).invoke(req);
        final Keyword const__1 = const__1;
        final Object rawRoot = __str.getRawRoot();
        final String s = " -- Sent from my server. I use Clojure btw.";
        this = null;
        return fn.invoke(invoke, const__1, rawRoot, s);
    }

    static {
        __update = RT.var("clojure.core", "update");
        const__1 = RT.keyword(null, "body");
        __str = RT.var("clojure.core", "str");
    }
}
```

Here's the second version of the `smug-middleware`

```clj
(->> (defn smug-middleware [handler]
         (fn inner-function [req]
           (update (example-fn req)
                   :body str " -- I like hammocks and private jokes.")))
       decompile
       with-out-str
       (spit "/tmp/decompiled-with-modification.java"))
```

```java
// Decompiling class: demo_closeable/deeply_nested$smug_middleware
package demo_closeable;

import clojure.lang.*;

public final class deeply_nested$smug_middleware extends AFunction
{
    public static Object invokeStatic(final Object handler) {
        return new deeply_nested$smug_middleware$inner_function__13666();
    }

    @Override
    public Object invoke(final Object handler) {
        return invokeStatic(handler);
    }
}


// Decompiling class: demo_closeable/deeply_nested$smug_middleware$inner_function__13666
package demo_closeable;

import clojure.lang.*;

public final class deeply_nested$smug_middleware$inner_function__13666 extends AFunction
{
    public static final Var __update;
    public static final Var __ddeeply_nested_example_fn;
    public static final Keyword const__2;
    public static final Var __str;

    @Override
    public Object invoke(final Object req) {
        final IFn fn = (IFn)__update.getRawRoot();
        final Object invoke = __ddeeply_nested_example_fn.invoke(req);
        final Keyword const__2 = const__2;
        final Object rawRoot = __str.getRawRoot();
        final String s = " -- I like hammocks and private jokes.";
        this = null;
        return fn.invoke(invoke, const__2, rawRoot, s);
    }

    static {
        __update = RT.var("clojure.core", "update");
        __ddeeply_nested_example_fn = RT.var("demo-closeable.deeply-nested", "example-fn");
        const__2 = RT.keyword(null, "body");
        __str = RT.var("clojure.core", "str");
    }
}
```

</details>


Thanksfully, you will generally not be bothered by this particular use case as
Reitit provides a more convenient way of applying middlewares to handlers in the
routes data:

```clj
(defn routes []
  [["/counter" real-worldish-function]
   ["/alternative" {:middleware [smug-middleware]}
    ["" example-fn]]])
```

Now, all the vars are once again used directly in the body of the `routes`
function, so any local re-evaluation will be picked up as expected. Still, as we
tend to use a lot of higher-order functions, it is frequent to encounter a
situation like this one. Often, we resign ourself to do full system reloads
every-time, as we can't be bothered to check where exactly is the closed over
value that is messing with our REPL workflow. And often, a little change in the
code structure can fix it, and we are left wondering why did we not make that
little change weeks or months ago. The price paid when you extend the duration
of the feedback loop will compound quickly over time. I have been guilty of
doing this too many times, so this post also exists to try to atone for my past
sins. Let's be honest, I'll probably do it again. Shame on me.


## Conclusion

We have illustrated a web project structure that allows for fast feedback
without full system reloads. The structure should also be very easy to extend
upon, adding new routes, handlers, or pieces of runtime state.

Vars are the unsung heroes of rapid-feedback worklow when doing Clojure. They
are a lower level abstraction compared to a full "reloaded" workflow (Stuart
Halloway gives a good comparison in his talk [Running With Scissors][12]).
Although what this post showed was specific to Reitit, the only [helper from the
library we used][5] is dead simple. This can probably be replicated with any
routing library out there without altering the structure too much.



[1]: /en/posts/getting-a-feel-for-closeables.html
[2]: https://github.com/weavejester/integrant/#suspending-and-resuming
[3]: https://cljdoc.org/d/metosin/reitit/0.7.0-alpha7/doc/advanced/dev-workflow
[4]: https://github.com/chpill/demo-closeable/tree/master/meatier-webserver
[5]: https://github.com/metosin/reitit/blob/620d0c271175a4e11d91d922b26c8162660db3f9/modules/reitit-ring/src/reitit/ring.cljc#L371-L385
[6]: https://clojuredocs.org/clojure.core/*compiler-options*
[7]: https://clojure.org/reference/vars
[8]: https://clojure.org/reference/evaluation
[9]: https://clojure.org/reference/vars#interning
[10]: https://clojure.org/guides/repl/enhancing_your_repl_workflow#writing-repl-friendly-programs
[11]: https://github.com/clojure-goes-fast/clj-java-decompiler
[12]: https://www.youtube.com/watch?v=Qx0-pViyIDU&t=1799s
