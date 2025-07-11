{
  description = "Technical blog";
  inputs = {
    proxy-flake.url = "github:chpill/proxy-flake";
    nixpkgs.follows = "proxy-flake/nixpkgs";
  };
  outputs = { self, nixpkgs , ... }@inputs: let
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
    packages.${system} = let website = import ./gen.nix pkgs; in {
      inherit website;
      default = website;
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
