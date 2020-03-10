import express from "express";
import { makeRequest, svcUrl } from "../common/axiosUtils";
import { runtimeConfig } from "../config";
import { AxiosRequestConfig } from "axios";

export const getPositions = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, runtimeConfig().bcPositions).toString(),
    headers: req.headers,
    method: "GET"
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};
