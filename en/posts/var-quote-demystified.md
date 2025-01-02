---
title: Var quote (#') demystified
author: Etienne Spillemaeker
published: 2025-01-03
---

```clojure
(require '[ring.adapter.jetty :refer [run-jetty]])

(defn handler [_req] {:status 200 :body "plop"})

(run-jetty #'handler {:port 4321 :join? false})
```

`#'handler` is equivalent to `(var handler)` which returns the var bound to the
symbol `handler` instead of the value inside that var.

This "var quoting" makes Jetty invoke the last `def`ed value of `handler`. It
works because a Clojure var [can be invoked][1], and it will in turn try to
invoke the value it currently holds.

A clojure var is also `Callable` and `Runnable`:

```clojure
(import '(java.util.concurrent Executors ExecutorService))
(require '[clojure.test :refer [deftest is]])

(defn f [] :plop)

(deftest vars-are-polyvalent
  (with-open [exec (Executors/newVirtualThreadPerTaskExecutor)]
    (is (= nil (.get (^[Runnable] ExecutorService/.submit exec #'f))))
    (is (= :plop (.get (^[Callable] ExecutorService/.submit exec #'f))))))
```

There are functions that expects a var as input, but I have not encountered many
of them:

```clojure
(clojure.test/run-test-var #'vars-are-polyvalent)
```

To dig deeper into the subject of functions and vars, I have enjoyed this article by Aaron Lahey: [8thlight.com/insights/the-relationship-between-clojure-functions-symbols-vars-and-namespaces](https://8thlight.com/insights/the-relationship-between-clojure-functions-symbols-vars-and-namespaces).

##### Bonus: how to make a StackOverflowError with a var

```clojure
(with-local-vars [a :unused-value]
  (var-set a a)
  (a))
```

[1]: https://github.com/clojure/clojure/blob/8ae9e4f95e2fbbd4ee4ee3c627088c45ab44fa68/src/jvm/clojure/lang/Var.java#L381-L708
