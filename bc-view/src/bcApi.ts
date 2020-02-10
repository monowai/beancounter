import axios, { AxiosResponse } from "axios";
import { runtimeConfig } from "./config";
import { HoldingContract } from "./types/beancounter";
import logger from "./ConfigLogging";

const server = axios.create({
  baseURL: runtimeConfig().bcService
});
//https://kapeli.com/cheat_sheets/Axios.docset/Contents/Resources/Documents/index
export const getHoldings = async (portfolioId: string | undefined): Promise<HoldingContract> => {
  logger.debug('>>getHoldings %s%s', runtimeConfig().bcService, `/${portfolioId}/today`);
  const response = await server.request<AxiosResponse<HoldingContract>>({
    url: `/${portfolioId}/today`,
    method: 'get'
  });
  logger.debug('<<getHoldings  %s', portfolioId);
  return response.data.data;
};
