---
title: Reusing a nixpkgs revision across multiple flakes
author: Etienne Spillemaeker
published: 2025-01-12
updated: 2025-07-03
---

**Update 2025-07-03**

The approach described initially does not work anymore since Nix now [disallows
indirect flake references][4] as flake inputs (they still work when using the
CLI, eg. `nix run my-indirect-ref#...`).

I now use the [proxy-flake][3] pattern with [github.com/chpill/proxy-flake](https://github.com/chpill/proxy-flake).

To use it, simply include the following in the `inputs` of a flake:

```nix
inputs = {
  proxy-flake = "github:chpill/proxy-flake";
  nixpkgs.follows = "proxy-flake/nixpkgs";
  ...
}
```

**Initial post 2025-01-12**

I have been using NixOS as my daily driver for a little over 2 years now, with
one configuration to build 2 targets: a laptop and a desktop workstation. I
still have mixed feelings about Nix. I really like the idea of an immutable
store of packages, but it's trying to do so much at the same time that it is
overwhelming (if you don't know much about Nix, [here is the best introduction I
have come across BY FAR][1]). In an attempt to deepen my undestanding of it, I
am starting to use Nix for development work, to try and realize good isolated
developer environments and maybe deployments.

I tried the following: Each project would setup its own little world described
in a flake.nix file, and thanks to the flake.lock file, this little world would
be reproducible much more easily wherever I need (the other dev machine, CI,
hopefully deployment targets in the future). Flake really delivers on that
promise, but I encountered a major annoyance when working with multiple projects
in this fashion: Every project would have its own revision of nixpkgs, and so
they would all require very similar, but different versions of the same tools,
resulting in 1) a massive bloat in the nix store, and 2) a massive waste of time
on certain occasions. I often work on trains, sharing connections with my phone,
and having Nix try to download one GiB of dependencies here and there to get
tools which I already have locally (in a slightly different revision...) is
infuriating.

Ideally, I would like to have all the projects I'm working on (like this very
blog for example) to use the same nixpkgs revisions for stable and unstable as
the host NixOS system, which is also built with a flake in this case. If you are
not using flake, you do not have this issue by the way, but you might lose on
the reproducibility if you are not manually pinning nixpkgs.

My first idea was to edit the lockfile by hand to set the revision I wanted, but
it's very tedious and error prone. Thanksfully, I found this [comment on the
NixOS discourse][2] that proposed a workaround to do exactly what I want. I did
not use part #4, because I did not see the point of getting rid of the global
registry. I also added another registry entry for `nixpkgs-unstable`, as I use
both in my system flake, and as I want to be able to reach for the freshest
packages when I do dev work (although this will probably be at the cost of more
package churn in the store, I should reassess this decision in a few months to
check if the benefits outweigh the costs).

I now set my flakes with `inputs.nixpkgs.url = "nixpkgs";` (or a the other
flavor `nixpkgs-unstable`), and when I `nix flake --update`, this will make the
revision in the local flake.lock the same as the one on my host system
flake.lock.

[1]: https://fzakaria.com/2024/07/05/learn-nix-the-fun-way.html
[2]: https://discourse.nixos.org/t/my-painpoints-with-flakes/9750/14
[3]: https://hugosum.com/blog/syncronizing-inputs-across-flakes#synchronizing-inputs-with-proxy-flake
[4]: https://determinate.systems/posts/changelog-determinate-nix-342/#indirect-flake-refs
