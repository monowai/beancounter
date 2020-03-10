import { Portfolio, PortfolioInput } from "../types/beancounter";
import logger from "../common/ConfigLogging";
import { _axios } from "../common/axiosUtils";

export const updatePortfolio = async (
  id: string,
  portfolioInput: PortfolioInput,
  config: {
    headers: { Authorization: string };
  }
): Promise<Portfolio | any> => {
  logger.debug(">>patch portfolio %s", id);
  await _axios.patch<Portfolio>(`/bff/portfolios/${id}`, portfolioInput, config).then(result => {
    logger.debug("<<patched Portfolio");
    return result.data;
  });
};
