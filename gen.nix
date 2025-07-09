pkgs : let
  extract-meta-hack = pkgs.writeText "extract-meta-hack.pandoc-tpl" "$\{meta-json\}";

  page-data = dir : name : let
    s = "${dir}/${name}";
    path = ./. + ("/" + s);
    url = (builtins.substring 0 ((builtins.stringLength s) - 3) s) + ".html";
    body = builtins.readFile(
      pkgs.runCommand "render-post"
        { buildInputs = [ pkgs.pandoc ]; }
        "pandoc --from=gfm ${path} > $out"
    );
  in {
    inherit dir name url body;
  } // builtins.fromJSON(
    builtins.readFile(
      pkgs.runCommand "extract-post-meta"
        { buildInputs = [ pkgs.pandoc ]; }
        "pandoc --template ${extract-meta-hack} ${path} > $out"
    ));

  posts = map
    ( name: page-data "en/posts" name )
    (builtins.attrNames (builtins.readDir ./en/posts));

  anti-chrono-comparator = a : b : builtins.lessThan b.published a.published;

  sorted-posts = builtins.sort anti-chrono-comparator posts;

  site-title = "Chpill’s (Over) Engineering Log";

  page = { title ? "", published ? "", body, ...} : pkgs.writeText "i-dont-care" ''
    <!DOCTYPE html>
    <html>
      <head>
          <title>${title}</title>
          <meta charset="utf-8"/>
          <link href="/assets/pandoc-gfm.css" rel="stylesheet"/>
      </head>
      <body>
        <nav style="align-items:center;display:flex;justify-content:space-around;">
          <h4>
              <a href="/">${site-title}</a>
          </h4>
          <a href="/en/feed.xml">
            <svg height="30px" width="30px">
              <use href="/assets/atom-feed-icon.svg#icon"></use>
            </svg>
          </a>
        </nav>
        <main>
          ${if title != "" then "<h1>${title}</h1>" else ""}
          ${if published != "" then "<p><i>${published}</p></i>" else ""}
          ${body}
        </main>
        <footer style="margin-top:3rem;text-align:center;">
          <hr />
          <p><small>Made with <a href="https://pandoc.org/">Pandoc</a></small></p>
        </footer>
      </body>
    </html>
  '';

  append-table-of-content = { body, ... }@data : data // {
    body = body + ''
      <div>
        <h3>All posts</h3>
        <ul>
        ${toString (map
          ( { url, title, ...} : "<li><a href='${url}'>${title}</a></li>" )
          sorted-posts)}
        </ul>
      </div >
    '';
  };

  index-data = page-data "" "index.md";
in (pkgs.linkFarm "plop"
  ([{ name = "index.html"; path = page (append-table-of-content index-data); }
    { name = "assets/pandoc-gfm.css"; path = ./pandoc-gfm.css; }
    { name = "assets/atom-feed-icon.svg"; path = ./atom-feed-icon.svg; }] ++ (map
      ( { url, ...}@post : { name = url; path = page post; })
      sorted-posts)))
