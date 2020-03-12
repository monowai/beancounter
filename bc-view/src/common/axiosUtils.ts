import axios, { AxiosInstance, AxiosRequestConfig } from "axios";
import { URL } from "url";
import express from "express";
import { KeycloakInstance } from "keycloak-js";
import logger from "./ConfigLogging";

export const _axios: AxiosInstance = axios.create();

export const svcUrl = (req: express.Request, endpoint: string): URL => {
  return new URL(req.originalUrl.replace("/bff/", "/api/"), endpoint);
};
export const getBearerToken = (): { Authorization: string } => ({
  Authorization: `Bearer ${
    !localStorage.getItem("token") ? "undefined" : localStorage.getItem("token")
  }`
});

export function isLoggedOut(): boolean {
  return localStorage.getItem("token") === undefined;
}

export function isLoggedIn(): boolean {
  return !isLoggedOut();
}

export const setToken = (keycloak: KeycloakInstance<"native">): void => {
  if (keycloak && keycloak.token) {
    localStorage.setItem("token", keycloak.token);
  }
};

export const resetToken = (): void => {
  if (typeof window !== undefined) {
    console.debug("token reset");
    localStorage.removeItem("token");
  } else {
    console.debug("no window so no token to remove");
  }
};

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
