import axios, { AxiosRequestConfig } from "axios";
import { runtimeConfig } from "./config";
import logger from "./common/ConfigLogging";
import express from "express";
import { svcUrl } from "./common/axiosUtils";

const makeRequest = async (
  req: express.Request,
  opts: AxiosRequestConfig,
  res: express.Response
): Promise<any> => {
  logger.debug("%s", req.url);
  await axios(opts)
    .then(response => res.json(response.data.data))
    .catch(err => {
      logger.error("api - %s", err.response.data);
      res.status(err.response.status || 500).send(err);
    });
};

export const apiHoldings = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcPositions).toString(),
    headers: req.headers,
    method: "GET"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};

export const apiRegister = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcData).toString(),
    headers: req.headers,
    data: {},
    method: "POST"
  } as AxiosRequestConfig;
  //logger.debug("calling %s %s", opts.url, req.headers);
  await makeRequest(req, opts, res);
};

export const apiPortfolios = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcData).toString(),
    headers: req.headers,
    method: "GET"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};
