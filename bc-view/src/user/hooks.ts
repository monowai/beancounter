import { SystemUser } from "../types/beancounter";
import { useEffect, useState } from "react";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useKeycloak } from "@react-keycloak/ssr";
import { AxiosError } from "axios";
import { BcResult } from "../types/app";

export const UNKNOWN: SystemUser = { email: undefined, active: false };

export function useSystemUser(): BcResult<SystemUser> {
  const { keycloak } = useKeycloak();
  const [systemUser, setSystemUser] = useState<SystemUser>(UNKNOWN);
  const [error, setError] = useState<AxiosError>();
  useEffect(() => {
    if (keycloak?.token) {
      console.debug(">>get SystemUser");
      _axios
        .get<SystemUser>("/bff/me", {
          headers: getBearerToken(keycloak?.token),
        })
        .then((result) => {
          console.debug("<<fetched SystemUser");
          setSystemUser(result.data);
        })
        .catch((err) => {
          console.error(err.message);
          if (err.response.status != 401) {
            console.info("fetch user");
            _axios
              .post<SystemUser>(
                "/bff/register",
                {},
                {
                  headers: getBearerToken(keycloak?.token),
                }
              )
              .then((result) => {
                console.debug("<<fetched SystemUser " + result.data.email);
                setSystemUser(result.data);
              })
              .catch((err) => {
                setError(err);
              });
          }
        });
    }
  }, [keycloak?.token]);
  return { data: systemUser, error };
}
