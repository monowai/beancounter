import axios, { AxiosRequestConfig } from "axios";
import { runtimeConfig } from "./config";
import logger from "./ConfigLogging";
import express from "express";
import { URL } from "url";

const svcPosition = (req: express.Request): URL => {
  return new URL(req.originalUrl.replace("/bff/", "/api/"), runtimeConfig().bcPositions);
};

export const handleGetApi = async (
  req: express.Request,
  opts: AxiosRequestConfig,
  res: express.Response
): Promise<any> => {
  try {
    logger.debug("%s --> %s", req.url, svcPosition(req));
    const response = await axios(opts);
    res.json(response.data.data);
  } catch (err) {
    logger.error("api - %s", err);
    res.status(500).send(err);
  }
};

export const holdings = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcPosition(req).toString(),
    body: req.body,
    method: "GET"
  } as AxiosRequestConfig;
  await handleGetApi(req, opts, res);
};
