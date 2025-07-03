{
  description = "Technical blog";
  inputs = {
    proxy-flake.url = "github:chpill/proxy-flake";
    nixpkgs.follows = "proxy-flake/nixpkgs";
  };
  outputs = { self, nixpkgs , ... }@inputs:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
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
    };
}
