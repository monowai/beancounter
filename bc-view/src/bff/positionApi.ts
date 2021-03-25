import express from "express";
import { makeRequest } from "../common/axiosUtils";
import { bcConfig } from "../common/config";
import { AxiosRequestConfig } from "axios";
import { svcUrl } from "../server/utils";

export const getPositions = async (req: express.Request, res: express.Response): Promise<any> => {
  const opts = {
    url: svcUrl(req, bcConfig.bcPositions).toString(),
    headers: req.headers,
    method: "GET",
  } as AxiosRequestConfig;
  await makeRequest(req, opts, res);
};
