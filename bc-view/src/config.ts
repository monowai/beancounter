import { serverEnv } from "./common/utils";
import { BcOptions } from "./types/beancounter";

export function runtimeConfig(): BcOptions {
  return typeof window !== "undefined" && window.env !== "undefined"
    ? {
        // client
        bcService: window.env
          ? window.env.bcService
          : serverEnv("BC_SERVICE", "http://localhost:9500/api")
      }
    : {
        // server
        bcService: serverEnv("BC_SERVICE", "http://localhost:9500/api")
      };
}
