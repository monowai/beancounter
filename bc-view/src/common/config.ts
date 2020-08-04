import { serverEnv } from "./utils";
import { BcOptions } from "../types/app";

// ENV keys
const SVC_POSITION = "SVC_POSITION";
const SVC_DATA = "SVC_DATA";
const KAFKA_URL = "KAFKA_URL";
const KAFKA_TOPIC_TRN = "KAFKA_TOPIC_TRN";
const KC_URL = "KC_URL";
const AUTH_REALM = "AUTH_REALM";
const AUTH_CLIENT = "AUTH_CLIENT";

function runtimeConfig(): BcOptions {
  return typeof window !== "undefined" && window.env !== "undefined"
    ? {
        // Don't leak server side values to the client
        bcPositions: "undefined",
        bcData: "undefined",
        kafkaUrl: "undefined",
        topicCsvTrn: "undefined",
        kcUrl: window.env ? window.env.kcUrl : serverEnv(KC_URL, "http://keycloak:9620/auth"),
        kcRealm: window.env ? window.env.kcRealm : serverEnv(AUTH_REALM, "bc-dev"),
        kcClient: window.env ? window.env.kcClient : serverEnv(AUTH_CLIENT, "bc-dev"),
      }
    : {
        // server
        bcPositions: serverEnv(SVC_POSITION, "http://localhost:9500"),
        bcData: serverEnv(SVC_DATA, "http://localhost:9510"),
        kafkaUrl: serverEnv(KAFKA_URL, "kafka:9092"),
        topicCsvTrn: serverEnv(KAFKA_TOPIC_TRN, "bc-trn-csv-dev"),
        kcUrl: serverEnv(KC_URL, "http://keycloak:9620/auth"),
        kcRealm: serverEnv(AUTH_REALM, "bc-dev"),
        kcClient: serverEnv(AUTH_CLIENT, "bc-dev"),
      };
}

export const bcConfig: BcOptions = {
  bcData: runtimeConfig().bcData,
  bcPositions: runtimeConfig().bcPositions,
  kafkaUrl: runtimeConfig().kafkaUrl,
  kcUrl: runtimeConfig().kcUrl,
  kcRealm: runtimeConfig().kcRealm,
  kcClient: runtimeConfig().kcClient,
  topicCsvTrn: runtimeConfig().topicCsvTrn,
};
