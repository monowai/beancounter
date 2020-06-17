import { _axios, getBearerToken } from "../common/axiosUtils";
import { Portfolio } from "../types/beancounter";
import logger from "../common/configLogging";
import { useEffect, useState } from "react";
import { AxiosError } from "axios";
import { useKeycloak } from "@react-keycloak/razzle";
import { USD } from "../static/hooks";
import { BcResult } from "../types/app";

export function usePortfolios(): BcResult<Portfolio[]> {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    _axios
      .get<Portfolio[]>("/bff/portfolios", {
        headers: getBearerToken(keycloak.token),
      })
      .then((result) => {
        logger.debug("<<retrieved Portfolio");
        setPortfolios(result.data);
      })
      .catch((err) => {
        if (err.response) {
          logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          if (err.response.status != 403) {
            setError(err);
          }
        } else {
          setError(err);
        }
      });
  }, [keycloak]);
  return { data: portfolios, error };
}

export function usePortfolio(id: string): BcResult<Portfolio> {
  const [portfolio, setPortfolio] = useState<Portfolio>({
    id: id,
    code: "",
    name: "",
    currency: USD,
    base: USD,
  });
  const [keycloak] = useKeycloak();
  const [error, setError] = useState<AxiosError>();
  useEffect(() => {
    if (id !== "new") {
      _axios
        .get<Portfolio>(`/bff/portfolios/${id}`, {
          headers: getBearerToken(keycloak.token),
        })
        .then((result) => {
          logger.debug("<<got Portfolio");
          setPortfolio(result.data);
          setError(undefined);
        })
        .catch((err) => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  }, [id, keycloak.token]);

  return { data: portfolio, error };
}
