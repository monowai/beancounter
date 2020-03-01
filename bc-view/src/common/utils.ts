import { propOr } from "ramda";
import isNode from "detect-node";
import axios, { AxiosInstance } from "axios";

export const isServer = (): boolean => isNode && typeof window === "undefined";

export const serverEnv = (key: string, def: string): string => propOr(def, key, process.env);

export const axiosBff = (): AxiosInstance => {
  return axios.create();
};
