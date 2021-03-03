import { _axios, getBearerToken } from "../common/axiosUtils";
import { HoldingContract } from "../types/beancounter";
import logger from "../common/configLogging";
import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { AxiosError } from "axios";
import { BcResult } from "../types/app";

export function useHoldings(code: string): BcResult<HoldingContract> {
  const [holdingResults, setHoldings] = useState<HoldingContract>();
  const [error, setError] = useState<AxiosError>();
  const { keycloak, initialized } = useKeycloak();
  useEffect(() => {
    if (initialized) {
      _axios
        .get<HoldingContract>(`/bff/${code}/today`, {
          headers: getBearerToken(keycloak?.token),
        })
        .then((result) => {
          logger.debug("<<fetch %s", code);
          setHoldings(result.data);
        })
        .catch((err) => {
          setError(err);
          if (err.response) {
            logger.error("axios errory [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  }, [code, initialized]);
  return { data: holdingResults, error };
}
