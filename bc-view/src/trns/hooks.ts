import { Transaction } from "../types/beancounter";
import { AxiosError } from "axios";
import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { BcResult } from "../types/app";

export function useAssetTransactions(
  portfolioId: string,
  assetId: string,
  filter: string
): BcResult<Transaction[]> {
  const [transactions, setTransactions] = useState<Transaction[]>();
  const [error, setError] = useState<AxiosError>();
  const { keycloak, initialized } = useKeycloak();
  useEffect(() => {
    if (initialized) {
      _axios
        .get<Transaction[]>(`/bff/trns/${portfolioId}/asset/${assetId}/${filter}`, {
          headers: getBearerToken(keycloak?.token),
        })
        .then((result) => {
          setTransactions(result.data);
        })
        .catch((err) => {
          if (err.response) {
            console.error("asset trns [%s]: [%s]", err.response.status, err.response.data.message);
          }
          setError(err);
        });
    }
  }, [assetId, portfolioId, filter, initialized, keycloak?.token]);
  return { data: transactions, error };
}

export function useTransaction(portfolioId: string, trnId: string): BcResult<Transaction> {
  const [transaction, setTransaction] = useState<Transaction>();
  const [error, setError] = useState<AxiosError>();
  const { keycloak, initialized } = useKeycloak();
  useEffect(() => {
    if (initialized) {
      _axios
        .get<Transaction>(`/bff/trns/${portfolioId}/${trnId}`, {
          headers: getBearerToken(keycloak?.token),
        })
        .then((result) => {
          setTransaction(result.data[0]);
        })
        .catch((err) => {
          if (err.response) {
            console.error("trns Id [%s]: [%s]", err.response.status, err.response.data.message);
          }
          setError(err);
        });
    }
  }, [initialized, keycloak?.token, portfolioId, trnId]);
  return { data: transaction, error };
}
