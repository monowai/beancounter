import axios, { AxiosInstance, AxiosRequestConfig } from "axios";
import { URL } from "url";
import express from "express";
import { KeycloakInstance } from "keycloak-js";
import logger from "./ConfigLogging";

export const _axios: AxiosInstance = axios.create();

export const svcUrl = (req: express.Request, endpoint: string): URL => {
  return new URL(req.originalUrl.replace("/bff/", "/api/"), endpoint);
};
export const getBearerToken = (keycloak: KeycloakInstance): { Authorization: string } => ({
  Authorization: `Bearer ${keycloak !== undefined ? keycloak.token : "undefined"}`
});

export const makeRequest = async (
  req: express.Request,
  opts: AxiosRequestConfig,
  res: express.Response
): Promise<any> => {
  logger.debug("%s %s", req.method, req.url);
  await axios(opts)
    .then(response => res.json(response.data.data))
    .catch(err => {
      logger.error("api - %s %s", err.response.status, err.response.data);
      res.status(err.response.status || 500).send(err);
    });
};
