import { SystemUser } from "../types/beancounter";
import { useEffect, useState } from "react";
import logger from "../common/ConfigLogging";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useKeycloak } from "@react-keycloak/razzle";
import { AxiosError } from "axios";

export const UNKNOWN: SystemUser = { email: undefined, active: false };

export function useSystemUser(): [SystemUser, AxiosError<any> | undefined] {
  const [keycloak] = useKeycloak();
  const [systemUser, setSystemUser] = useState<SystemUser>(UNKNOWN);
  const [error, setError] = useState<AxiosError>();
  useEffect(() => {
    if (keycloak.authenticated) {
      logger.debug(">>get SystemUser");
      _axios
        .post<any>(
          "/bff/register",
          {},
          {
            headers: getBearerToken(keycloak)
          }
        )
        .then(result => {
          logger.debug("<<fetched SystemUser");
          setSystemUser(result.data);
        })
        .catch(err => {
          setSystemUser({
            active: false,
            email: undefined
          });
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  }, [keycloak]);
  return [systemUser, error];
}
