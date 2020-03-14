import { serverEnv } from "./utils";
import { BcOptions } from "../types/app";

// ENV keys
const SVC_POSITION = "SVC_POSITION";
const SVC_DATA = "SVC_DATA";
const KC_URL = "KC_URL";
const KC_REALM = "KC_REALM";
const KC_CLIENT = "KC_CLIENT";

function runtimeConfig(): BcOptions {
  return typeof window !== "undefined" && window.env !== "undefined"
    ? {
        // client
        bcPositions: window.env
          ? window.env.bcPositions
          : serverEnv(SVC_POSITION, "http://localhost:9500"),
        bcData: window.env ? window.env.bcData : serverEnv(SVC_DATA, "http://localhost:9510"),
        kcUrl: window.env ? window.env.kcUrl : serverEnv(KC_URL, "http://keycloak:9620/auth"),
        kcClient: window.env ? window.env.kcClient : serverEnv(KC_CLIENT, "bc-dev"),
        kcRealm: window.env ? window.env.kcRealm : serverEnv(KC_REALM, "bc-dev")
      }
    : {
        // server
        bcPositions: serverEnv(SVC_POSITION, "http://localhost:9500"),
        bcData: serverEnv(SVC_DATA, "http://localhost:9510"),
        kcUrl: serverEnv(KC_URL, "http://keycloak:9620/auth"),
        kcClient: serverEnv(KC_CLIENT, "bc-dev"),
        kcRealm: serverEnv(KC_REALM, "bc-dev")
      };
}

export { runtimeConfig };
