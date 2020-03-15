import { _axios, getBearerToken } from "../common/axiosUtils";
import { Portfolio } from "../types/beancounter";
import logger from "../common/ConfigLogging";
import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { useKeycloak } from "@react-keycloak/razzle";
import { USD } from "../static/currencies";

export function usePortfolios(): [Portfolio[], AxiosError | undefined] {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    _axios
      .get<Portfolio[]>("/bff/portfolios", {
        headers: getBearerToken(keycloak)
      })
      .then(result => {
        logger.debug("<<retrieved Portfolio");
        setPortfolios(result.data);
      })
      .catch(err => {
        if (err.response) {
          logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
        }
        setError(err);
      });
  }, [keycloak]);
  return [portfolios, error];
}

export function usePortfolio(id: string): [Portfolio | undefined, AxiosError | undefined] {
  const [portfolio, setPortfolio] = useState<Portfolio>({
    id: id,
    code: "",
    name: "",
    currency: USD,
    base: USD
  });
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    if (id !== "new") {
      _axios
        .get<Portfolio>(`/bff/portfolios/${id}`, {
          headers: getBearerToken(keycloak)
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
    }
  }, [keycloak, id]);

  return [portfolio, error];
}
