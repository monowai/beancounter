import {propOr} from 'ramda';
import axios, {AxiosInstance} from 'axios';

export const serverEnv = (key: string, def: string): string => propOr(def, key, process.env);

export const axiosBff = (): AxiosInstance => {
  return axios.create();
};
