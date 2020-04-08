import { SystemUser } from "../types/beancounter";
import { useEffect, useState } from "react";
import logger from "../common/configLogging";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useKeycloak } from "@react-keycloak/razzle";

export const UNKNOWN: SystemUser = { email: undefined, active: false };

export function useSystemUser(): SystemUser {
  const [keycloak, initialized] = useKeycloak();
  const [systemUser, setSystemUser] = useState<SystemUser>(UNKNOWN);
  useEffect(() => {
    if (initialized && keycloak.token) {
      logger.debug(">>get SystemUser");
      _axios
        .post<any>(
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
          if (err.response) {
            logger.error("User error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  }, [keycloak, initialized]);
  return systemUser;
}
