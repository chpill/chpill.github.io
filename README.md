# Over-Engineering Log

Basic static site generator and content used to create the pages of
[chpill.github.io](https://chpill.github.io) The generated content is on the gh-pages
branch, following the workflow described [here](https://github.com/mmzsource/mxmmz#publish).

To render the static pages, `nix shell nixpkgs#babashka nixpkgs#pandoc`, then
`bb render.clj`.
