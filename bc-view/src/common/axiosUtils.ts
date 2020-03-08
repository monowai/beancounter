import axios, { AxiosInstance } from "axios";
import { URL } from "url";
import express from "express";
import { KeycloakInstance } from "keycloak-js";

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
    localStorage.removeItem("token");
  }
};
