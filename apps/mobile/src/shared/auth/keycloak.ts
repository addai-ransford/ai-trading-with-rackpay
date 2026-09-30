import Keycloak from "keycloak-js";
import { env } from "../config/env";

export const keycloak = new Keycloak({
  url: env.keycloak.url,
  realm: env.keycloak.realm,
  clientId: env.keycloak.clientId,
});
