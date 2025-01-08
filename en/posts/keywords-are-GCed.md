---
title: Keywords are GCed
author: Etienne Spillemaeker
---

I had read somewhere a long time ago that when faced with a situation where one
is parsing some user input data, one should not use the "keywordise" options,
JSON, url encoded query and form parameters in HTTP requests and so on. The
reasoning was that keywords are interned, and an attacker could send a stream of
requests with new strings to be interned as keyword each time, and progressively
fill up the process memory. Since then, I had religiously avoided the
[keyword-params][1] middleware from Ring, until very recently while I looked at
the collection of default settings form Ring middlewares in [ring-defaults][2].
To my horror, the middleware was used by default! And it had been for, hmmm,
[more than 10 years][3]... At that point, I was at last starting to doubt. Ring
is usually very well maintained, how can this be? Is this kind of attack
infeasible?

Fortunately, this was pretty easy to test, thanks [oha][4]. It has an option to 

```
oha -z 1m --rand-regex-url 'http://localhost:4321\?[a-z]{10}=plop'
```


https://github.com/chpill/clj-keyword-experiment




Despite the fact keywords interned, they are still garbage collected.


There is a cache that is cleaned-up when creating keywords


VisualVM can help check out what is happening



* Interned with strong references in August 2007 ([commit](https://github.com/clojure/clojure/commit/f93cacece6c5bb48d20fdbbf7b228fcdb1477981#diff-de61670e4aaaa472749d1982e665f1bc7ac6f1aa0cdd601e3c1f7a4f767fc8b2L30))
* Interned with soft references in July 2010 ([commit](https://github.com/clojure/clojure/commit/02559a4aad442253b601870f7c9aa04c91baf235))
* Interned with weak references in marsh 2011 ([commit](https://github.com/clojure/clojure/commit/5ee542d3de7e22d68e923c0f9c63267960cd1647))

([Here's a nice explanations of the difference between a soft and a weak references][3])






** When you do it 2 times, the keywords the cache is not garbage collected anymore? => if the keyword was already interned, the cache is not cleared (validated)


[1]: https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/keyword_params.clj
[2]: https://github.com/ring-clojure/ring-defaults/
[3]: https://github.com/ring-clojure/ring-defaults/commit/f070c9dca4116c75840818e2ae7bf6cd132dc500
[4]: https://github.com/hatoo/oha
[3]: https://stackoverflow.com/questions/299659/whats-the-difference-between-softreference-and-weakreference-in-java
