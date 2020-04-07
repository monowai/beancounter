import { BcResult, Transaction } from "../types/beancounter";
import { AxiosError } from "axios";
import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/razzle";
import { _axios, getBearerToken } from "../common/axiosUtils";
import logger from "../common/configLogging";

export function useAssetTransactions(
  portfolioId: string,
  assetId: string
): BcResult<Transaction[]> {
  const [transactions, setTransactions] = useState<Transaction[]>();
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    console.debug("Hook-AssetTrn");
    _axios
      .get<Transaction[]>(`/bff/trns/${portfolioId}/asset/${assetId}`, {
        headers: getBearerToken(keycloak.token)
      })
      .then(result => {
        setTransactions(result.data);
      })
      .catch(err => {
        if (err.response) {
          logger.error("asset trns [%s]: [%s]", err.response.status, err.response.data.message);
        }
        setError(err);
      });
  }, [keycloak, assetId, portfolioId]);
  return { data: transactions, error };
}

export function useTransaction(portfolioId: string, trnId: string): BcResult<Transaction> {
  const [transaction, setTransaction] = useState<Transaction>();
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    console.log("Hook-CallerRef");
    _axios
      .get<Transaction>(`/bff/trns/${portfolioId}/${trnId}`, {
        headers: getBearerToken(keycloak.token)
      })
      .then(result => {
        setTransaction(result.data[0]);
      })
      .catch(err => {
        if (err.response) {
          logger.error("trns Id [%s]: [%s]", err.response.status, err.response.data.message);
        }
        setError(err);
      });
  }, [keycloak, portfolioId, trnId]);
  return { data: transaction, error };
}
