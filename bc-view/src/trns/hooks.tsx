import { Transaction, TrnId } from "../types/beancounter";
import { AxiosError } from "axios";
import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/razzle";
import { _axios, getBearerToken } from "../common/axiosUtils";
import logger from "../common/ConfigLogging";

export function useAssetTransactions(
  portfolioId: string,
  assetId: string
): [Transaction[], AxiosError | undefined] {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    _axios
      .get<Transaction[]>(`/bff/trns/${portfolioId}/${assetId}`, {
        headers: getBearerToken(keycloak.token)
      })
      .then(result => {
        setTransactions(result.data);
      })
      .catch(err => {
        if (err.response) {
          logger.error("trns error [%s]: [%s]", err.response.status, err.response.data.message);
        }
        setError(err);
      });
  }, [keycloak, assetId, portfolioId]);
  return [transactions, error];
}

export function getKey(trnId: TrnId): string {
  return trnId.provider + "." + trnId.batch + "." + trnId.id;
}
