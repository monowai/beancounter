import axios, { AxiosRequestConfig } from "axios";
import { runtimeConfig } from "./config";
import logger from "./common/ConfigLogging";
import express from "express";
import { URL } from "url";

const svcPosition = (req: express.Request): URL => {
  return new URL(req.originalUrl.replace("/bff/", "/api/"), runtimeConfig().bcPositions);
};

const svcData = (req: express.Request): URL => {
  return new URL(req.originalUrl.replace("/bff/", "/api/"), runtimeConfig().bcData);
};

const makeRequest = async (
  req: express.Request,
  opts: AxiosRequestConfig,
  res: express.Response
): Promise<any> => {
  logger.debug("%s --> %s", req.url, svcPosition(req));
  await axios(opts)
    .then(response => res.json(response.data.data))
    .catch(err => {
      logger.error("api - %s", err);
      res.status(err.response.status || 500).send(err);
    });
};

export const holdings = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcPosition(req).toString(),
    headers: req.headers,
    method: "GET"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};

export const register = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcData(req).toString(),
    body: req.body,
    headers: req.headers,
    method: "POST"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};
