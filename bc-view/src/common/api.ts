import { propOr } from "ramda";
import logger from "../ConfigLogging";

export const getEnv = (key: string, def: string): string => propOr(def, key, process.env);

export const getApiUrl = (): string => {
  const result = getEnv("RAZZLE_SERVICE", "http://localhost:9500/api");
  logger.debug("BFF on ", result);
  return result;
};
