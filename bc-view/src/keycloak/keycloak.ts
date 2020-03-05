import { KeycloakConfig, KeycloakInitOptions } from "keycloak-js";
import { runtimeConfig } from "../config";
import { KeycloakStub } from "./defaults";

export const Keycloak = typeof window !== "undefined" ? require("keycloak-js") : null;

const keycloakConfig: KeycloakConfig = {
  url: runtimeConfig().kcUrl,
  realm: runtimeConfig().kcRealm,
  clientId: runtimeConfig().kcClient
};

export const getKeycloakInstance =
  typeof window === "undefined" ? KeycloakStub : new Keycloak(keycloakConfig);

export const keycloakProviderInitConfig: KeycloakInitOptions = {
  checkLoginIframe: false
  // onLoad: "login-required"
};
