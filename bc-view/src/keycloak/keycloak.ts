import { KeycloakConfig, KeycloakInitOptions, KeycloakInstance } from "keycloak-js";
import { runtimeConfig } from "../config";
import { KeycloakStub } from "./defaults";
import logger from "../common/ConfigLogging";

export const Keycloak = typeof window !== "undefined" ? require("keycloak-js") : null;

const keycloakConfig: KeycloakConfig = {
  url: runtimeConfig().kcUrl,
  realm: runtimeConfig().kcRealm,
  clientId: runtimeConfig().kcClient
};

export const keycloakProviderInitConfig: KeycloakInitOptions = {
  checkLoginIframe: false
  // onLoad: "login-required"
};

export class Factory {
  static get kc(): KeycloakInstance {
    return this._kc;
  }

  private static makeKeycloakInstance(): KeycloakInstance {
    logger.debug("making keycloak instance");

    if (this._kc) {
      return this._kc;
    }
    return new Keycloak(keycloakConfig);
  }

  private static _kc =
    typeof window === "undefined" ? KeycloakStub : Factory.makeKeycloakInstance();
}
