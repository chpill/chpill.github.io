{
  description = "Technical blog";
  inputs = {
    proxy-flake.url = "github:chpill/proxy-flake";
    nixpkgs.follows = "proxy-flake/nixpkgs";
    # TODO find how to make a "deep" follow of nixpkgs
    # Maybe use
    # https://fzakaria.com/2024/07/31/automatic-nix-flake-follows
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.nixpkgs.follows = "proxy-flake/nixpkgs";
    };
  };
  outputs = { self, nixpkgs, clj-nix, ... }@inputs: let
    system = "x86_64-linux";
    pkgs = nixpkgs.legacyPackages.${system};
    lib = pkgs.lib;
    content-files = lib.fileset.unions [
      ./pandoc-gfm.css
      ./atom-feed-icon.svg
      ./index.md
      ./en
    ];
  in {
    devShells.${system}.default = pkgs.mkShell {
      packages = with pkgs; [
        pandoc
        (clojure.override { jdk = jdk24; })
        # To view the pages locally:
        # cd publish && python -m http.server
        python3
        # To check that the feed is valid xml
        # xmllint --noout **/*.xml
        libxml2
      ];
    };
    packages.${system} = let
      website = import ./gen.nix pkgs;
      clj-files = lib.fileset.unions [
        ./deps.edn
        ./src
      ];
    in {
      inherit website;
      default = website;
      clj-nix-deps-lock = clj-nix.packages.${system}.deps-lock;
      clj-website-gen = clj-nix.lib.mkCljApp {
          inherit pkgs;
          modules = [
            {
              # TODO using the fileset does not work?
              projectSrc = ./.; # clj-files;
              name = "chpill.blog/render";
              main-ns = "render.core";
            }
          ];
      };
      previousWebsiteInClojure = pkgs.runCommandLocal "previousWebsiteInClojure" {
        nativeBuildInputs = [
          self.packages.${system}.clj-website-gen
          pkgs.pandoc
        ];
      } ''
          cp ${./pandoc-gfm.css} "pandoc-gfm.css"
          cp ${./atom-feed-icon.svg} "atom-feed-icon.svg"
          cp ${./index.md} "index.md"
          cp -r ${./en} "en/"
          render $out
        '';
    };
    checks.${system} = let
      site = self.packages.${system}.default;
      previous-site = self.packages.${system}.previousWebsiteInClojure;
    in {
      # TODO add a test using xmllint
      dirCountTest = pkgs.runCommandLocal "basicDirCountTest" {
        src = content-files;
        nativeBuildInputs = [ site ];
      } ''
          mkdir $out
          sourceFilesCount=$(find "${./en/posts}"    -maxdepth 1 -type f | wc -l)
          resultFilesCount=$(find "${site}/en/posts" -maxdepth 1 -type l | wc -l)
          [ "$sourceFilesCount" -eq "$resultFilesCount" ]
        '';
      # Does not work... TODO find how to run the proper clojure test.clj with clj-nix
      clojureWebsiteComparisonTest = pkgs.runCommandLocal "clojureWebsiteComparisonTest" {
        src = content-files;
        nativeBuildInputs = [
          site
          previous-site
          pkgs.babashka
        ];
      } ''
          mkdir $out
          bb -i "(assert (= (xml/parse-str (slurp \"${site}/en/feed.xml\"))\
                            (xml/parse-str (slurp \"${previous-site}/en/feed.xml\"))))"
        '';
    };
    nixosConfigurations.container = nixpkgs.lib.nixosSystem {
      inherit system;
      modules = [{
        system.stateVersion = "25.05";
        boot.isContainer = true;
        networking.firewall.allowedTCPPorts = [ 80 ];
        # For some unadequately explored reasons, this delays the start of the container
        networking.useDHCP = false;
        services.nginx = {
          enable = true;
          virtualHosts."container.local" = {
            default = true;
            locations."/".root = self.packages.${system}.website;
          };
        };
      }];
    };
  };
}
