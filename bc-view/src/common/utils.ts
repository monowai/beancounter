import { propOr } from "ramda";

export const serverEnv = (key: string, def: string): string => propOr(def, key, process.env);
