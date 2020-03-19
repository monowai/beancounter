import { Asset } from "../types/beancounter";
import { AxiosError } from "axios";
import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/razzle";
import { _axios, getBearerToken } from "../common/axiosUtils";
import logger from "../common/ConfigLogging";

export function useAsset(assetId: string): [Asset | undefined, AxiosError | undefined] {
  const [asset, setAsset] = useState<Asset>();
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    _axios
      .get<Asset>(`/bff/assets/${assetId}`, {
        headers: getBearerToken(keycloak.token)
      })
      .then(result => {
        setAsset(result.data);
      })
      .catch(err => {
        if (err.response) {
          logger.error("trns error [%s]: [%s]", err.response.status, err.response.data.message);
        }
        setError(err);
      });
  }, [keycloak, assetId]);
  return [asset, error];
}
