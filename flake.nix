{
  description = "Technical blog";
  inputs.nixpkgs-unstable.url = "nixpkgs-unstable";
  outputs = { self, nixpkgs-unstable }@inputs:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs-unstable.legacyPackages.${system};
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
