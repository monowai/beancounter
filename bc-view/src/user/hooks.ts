import { SystemUser } from "../types/beancounter";
import { useEffect, useState } from "react";
import logger from "../common/configLogging";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useKeycloak } from "@react-keycloak/razzle";
import { AxiosError } from "axios";
import { BcResult } from "../types/app";

export const UNKNOWN: SystemUser = { email: undefined, active: false };

export function useSystemUser(): BcResult<SystemUser> {
  const [keycloak, initialized] = useKeycloak();
  const [systemUser, setSystemUser] = useState<SystemUser>(UNKNOWN);
  const [error, setError] = useState<AxiosError>();
  useEffect(() => {
    //if (initialized && keycloak.token) {
    logger.debug(">>get SystemUser");
    _axios
      .get<SystemUser>("/bff/me", {
        headers: getBearerToken(keycloak.token),
      })
      .then((result) => {
        logger.debug("<<fetched SystemUser");
        setSystemUser(result.data);
      })
      .catch((err) => {
        if (err.response.status != 401) {
          _axios
            .post<SystemUser>(
              "/bff/register",
              {},
              {
                headers: getBearerToken(keycloak.token),
              }
            )
            .then((result) => {
              logger.debug("<<fetched SystemUser");
              setSystemUser(result.data);
            })
            .catch((err) => {
              setError(err);
            });
        }
      });
  }, [keycloak, initialized]);
  return { data: systemUser, error };
}
