{
  description = "Technical blog";
  inputs.nixpkgs.url = "nixpkgs-unstable";
  outputs = { self, nixpkgs }@inputs:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          pandoc
          (clojure.override { jdk = jdk23; })
          # To view the pages locally:
          # cd publish && httplz
          httplz
          # To check that the feed is valid xml
          # xmllint --noout **/*.xml
          libxml2
        ];
      };
    };
}
