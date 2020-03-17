import { KeycloakConfig, KeycloakInitOptions } from "keycloak-js";
import { runtimeConfig } from "./config";

export const KcConfig = typeof window !== "undefined" ? require("keycloak-js") : null;

export const keycloakConfig: KeycloakConfig = {
  url: runtimeConfig().kcUrl,
  realm: runtimeConfig().kcRealm,
  clientId: runtimeConfig().kcClient
};

export const initConfig: KeycloakInitOptions = {
  checkLoginIframe: false
};
