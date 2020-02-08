import { propOr } from "ramda";
import logger from "../ConfigLogging";

export const getEnv = (key: string, def: string): string => propOr(def, key, process.env);

export const getApiUrl = (): string => {
  const result = getEnv("BC_SERVICE", "http://localhost:9500/api");
  logger.debug("bc-api@%s", result);
  return result;
};
