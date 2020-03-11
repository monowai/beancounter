import { _axios, getBearerToken, setToken } from "../common/axiosUtils";
import { Portfolio } from "../types/beancounter";
import logger from "../common/ConfigLogging";
import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { useKeycloak } from "@react-keycloak/web";

export function usePortfolios(): [Portfolio[], AxiosError | undefined] {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    _axios
      .get<Portfolio[]>("/bff/portfolios", {
        headers: getBearerToken()
      })
      .then(result => {
        logger.debug("<<got Portfolios");
        setPortfolios(result.data);
      })
      .catch(err => {
        if (err.response) {
          logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
        }
        setError(err);
      });
  }, []);
  setToken(keycloak);
  return [portfolios, error];
}

export function usePortfolio(id: string): [Portfolio | undefined, AxiosError<any> | undefined] {
  const [portfolio, setPortfolio] = useState<Portfolio>();
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    _axios
      .get<Portfolio>(`/bff/portfolios/${id}`, {
        headers: getBearerToken()
      })
      .then(result => {
        logger.debug("<<got Portfolio");
        setPortfolio(result.data);
      })
      .catch(err => {
        setError(err);
        if (err.response) {
          logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
        }
      });
  }, [id]);
  setToken(keycloak);
  return [portfolio, error];
}
