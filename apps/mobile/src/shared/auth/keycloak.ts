import Keycloak from "keycloak-js";
import { env } from "../config/env";

export const keycloak = new Keycloak({
  url: env.keycloak.url,
  realm: env.keycloak.realm,
  clientId: env.keycloak.clientId,
});

const redirectUri = () =>
  env.keycloak.redirectUri ??
  (typeof window !== "undefined" ? window.location.origin : undefined);

export function login() {
  return keycloak.login({
    redirectUri: redirectUri(),
    pkceMethod: "S256",
  });
}

export function register() {
  return keycloak.register({
    redirectUri: redirectUri(),
    pkceMethod: "S256",
  });
}

export function logout() {
  return keycloak.logout({
    redirectUri: redirectUri(),
  });
}
