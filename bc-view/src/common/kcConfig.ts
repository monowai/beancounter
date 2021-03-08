import { KeycloakConfig, KeycloakInitOptions } from "keycloak-js";
import { bcConfig } from "./config";

export const KcConfig = typeof window !== "undefined" ? require("keycloak-js") : null;

export const keycloakConfig: KeycloakConfig = {
  url: bcConfig.kcUrl,
  realm: bcConfig.kcRealm,
  clientId: bcConfig.kcClient,
};

export const initConfig: KeycloakInitOptions = {
  checkLoginIframe: true,
  onLoad: "login-required",
};
