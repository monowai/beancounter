import { keycloakDefault } from "./defaults";
import { KeycloakConfig, KeycloakInitOptions } from "keycloak-js";
import { runtimeConfig } from "../config";

export const Keycloak = typeof window !== "undefined" ? require("keycloak-js") : null;

const keycloakConfig: KeycloakConfig = {
  url: runtimeConfig().kcUrl,
  realm: runtimeConfig().kcRealm,
  clientId: runtimeConfig().kcClient
};

export const keycloak =
  typeof window === "undefined" ? keycloakDefault : new Keycloak(keycloakConfig);

export const keycloakProviderInitConfig: KeycloakInitOptions = {
  checkLoginIframe: false
  // onLoad: "login-required"
};
