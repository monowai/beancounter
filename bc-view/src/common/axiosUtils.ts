import axios, { AxiosInstance, AxiosRequestConfig } from "axios";
import express from "express";

export const _axios: AxiosInstance = axios.create();

export const getBearerToken = (token?: string): { Authorization: string } => ({
  Authorization: `Bearer ${token !== undefined ? token : "undefined"}`,
});

export const makeRequest = async (
  req: express.Request,
  opts: AxiosRequestConfig,
  res: express.Response
): Promise<any> => {
  await axios(opts)
    .then((response) => res.json(response.data.data))
    .catch((err) => {
      if (err.response) {
        console.debug("bff - url: %s status:%s", req.url, err.response.status);
        res.status(err.response.status || 500).send(err);
      }
      res.status(500);
      console.error(err);
    });
};
