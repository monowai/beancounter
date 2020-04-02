import { _axios, getBearerToken } from "../common/axiosUtils";
import { BcResult, HoldingContract } from "../types/beancounter";
import logger from "../common/configLogging";
import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/razzle";
import { AxiosError } from "axios";

export function useHoldings(code: string): BcResult<HoldingContract> {
  const [holdingResults, setHoldings] = useState<HoldingContract>();
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    _axios
      .get<HoldingContract>(`/bff/${code}/today`, {
        headers: getBearerToken(keycloak.token)
      })
      .then(result => {
        logger.debug("<<fetch %s", code);
        setHoldings(result.data);
      })
      .catch(err => {
        setError(err);
        if (err.response) {
          logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
        }
      });
  }, [code, keycloak.token]);
  return { data: holdingResults, error };
}
