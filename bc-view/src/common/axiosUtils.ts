import axios, { AxiosInstance } from "axios";
import { URL } from "url";
import express from "express";

export const _axios: AxiosInstance = axios.create()

export const svcUrl = (req: express.Request, endpoint: string): URL => {
  return new URL(req.originalUrl.replace("/bff/", "/api/"), endpoint);
};
