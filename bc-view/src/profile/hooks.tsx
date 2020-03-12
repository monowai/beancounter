import { SystemUser } from "../types/beancounter";
import { useEffect, useState } from "react";
import logger from "../common/ConfigLogging";
import { _axios, getBearerToken } from "../common/axiosUtils";

export const UNKNOWN: SystemUser = { email: "loading", active: false };

export function useSystemUser(): SystemUser {
  const [systemUser, setSystemUser] = useState<SystemUser>(UNKNOWN);
  useEffect(() => {
    logger.debug(">>get SystemUser");
    _axios
      .post<any>(
        "/bff/register",
        {},
        {
          headers: getBearerToken()
        }
      )
      .then(result => {
        logger.debug("<<fetched SystemUser");
        setSystemUser(result.data);
      })
      .catch(err => {
        setSystemUser({
          active: false,
          email: "loading"
        });
        if (err.response) {
          logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
        }
      });
  }, []);
  return systemUser;
}
