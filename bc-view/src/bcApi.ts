import axios, { AxiosRequestConfig } from "axios";
import { runtimeConfig } from "./config";
import logger from "./ConfigLogging";
import express from "express";
import { URL } from "url";

const svcPosition = (req: any): URL => {
  return new URL(req.originalUrl, runtimeConfig().bcPositions);
};
//
// const server = axios.create({
//   baseURL: runtimeConfig().bcPositions
// });
// //https://kapeli.com/cheat_sheets/Axios.docset/Contents/Resources/Documents/index
// export const getHoldings = async (portfolioId: string | undefined): Promise<HoldingContract> => {
//   logger.debug('>>getHoldings %s%s', runtimeConfig().bcPositions, `/${portfolioId}/today`);
//   logger.debug(getUrl(`/bff/${portfolioId}/today`).toString());
//   const opts = {
//     url: getUrl(`/bff/${portfolioId}/today`).toString(),
//     method: 'GET'
//   } as AxiosRequestConfig;
//
//   const response = await axios(opts)
//   logger.debug('<<getHoldings  %s', portfolioId);
//   return response.data;
// };

export const handleGetApi = async (req: express.Request, res: express.Response): Promise<any> => {
  try {
    logger.debug("api url %s", svcPosition(req));

    const opts = {
      url: svcPosition(req).toString(),
      body: req.body,
      method: "GET"
    } as AxiosRequestConfig;

    const response = await axios(opts);
    res.json(response.data.data);
  } catch (e) {
    logger.error(e);
  }
};
