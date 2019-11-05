import { propOr } from "ramda";

export const getEnv = (key: string, def: string): string =>
  propOr(def, key, process.env);
