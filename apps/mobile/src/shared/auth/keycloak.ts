import Keycloak from "keycloak-js";
import { env } from "../config/env";

let client: Keycloak | undefined;
let initialization: Promise<boolean> | undefined;

export function getKeycloak(): Keycloak {
  if (!client) {
    client = new Keycloak({
      url: env.keycloak.url,
      realm: env.keycloak.realm,
      clientId: env.keycloak.clientId,
    });
  }
  return client;
}

export function initializeKeycloak(): Promise<boolean> {
  if (!initialization) {
    initialization = getKeycloak().init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
    });
  }
  return initialization;
}

const redirectUri = () =>
  env.keycloak.redirectUri ??
  (typeof window !== "undefined" ? window.location.origin : undefined);

export function login() {
  return getKeycloak().login({ redirectUri: redirectUri() });
}

export function register() {
  return getKeycloak().register({ redirectUri: redirectUri() });
}

export function logout() {
  return getKeycloak().logout({ redirectUri: redirectUri() });
}
