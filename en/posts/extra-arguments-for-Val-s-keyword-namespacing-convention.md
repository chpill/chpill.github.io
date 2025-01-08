Extra arguments about adopting [Valentin's hot take on clojure namespaced keyword][1]


The name of the game is ubiquity. Here are some extra places your attribute
keywods will reach thanks to that convention:

* reitit wildcard in urls (eg "/post/:my_post_id/edit")

* when rendering hiccup, only the "name" of keywords are used as attribute values:

```clojure
(html [:input {:name :traditional.ns/k}]) ; => <input name="k" />
(html [:input {:name :unorthodox_ns_k}])  ; => <input name="unorthodox_ns_k" />
```

(I'm not too sure why it is that way...)


[1]: https://vvvvalvalval.github.io/posts/clojure-key-namespacing-convention-considered-harmful.html
[2]: https://github.com/weavejester/hiccup/blob/327d5408af94b4ef9560c39ab0afcfe5afe3c9a5/src/hiccup/util.clj#L28
