import { Asset } from "../types/beancounter";
import { AxiosError } from "axios";
import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { BcResult } from "../types/app";

export function useAsset(assetId: string): BcResult<Asset> {
  const [asset, setAsset] = useState<Asset>();
  const [error, setError] = useState<AxiosError>();
  const { keycloak, initialized } = useKeycloak();
  useEffect(() => {
    if (initialized) {
      _axios
        .get<Asset>(`/bff/assets/${assetId}`, {
          headers: getBearerToken(keycloak?.token),
        })
        .then((result) => {
          setAsset(result.data);
        })
        .catch((err) => {
          if (err.response) {
            console.error("Asset error [%s]: [%s]", err.response.status, err.response.data.message);
          }
          setError(err);
        });
    }
  }, [initialized, assetId, keycloak?.token]);
  return { data: asset, error };
}
