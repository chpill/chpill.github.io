{
  description = "Technical blog";
  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-23.11";
  outputs = { self, nixpkgs }@inputs:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          pandoc
          clojure
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
