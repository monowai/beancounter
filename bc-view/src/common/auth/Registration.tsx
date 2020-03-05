import React, { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import logger from "../ConfigLogging";
import { getBearerToken } from "../../keycloak/utils";
import { AxiosError } from "axios";
import { SystemUser } from "../../types/beancounter";
import handleError from "../errors/UserError";
import { _axios } from "../axiosUtils";

const Registration = (): JSX.Element => {
  const [loading, setLoading] = useState<boolean>(true);
  const [systemUser, setSystemUser] = useState<SystemUser>();
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();

  useEffect(() => {
    const register = async (config: { headers: { Authorization: string } }): Promise<void> => {
      setLoading(true);
      logger.debug(">>do registration %s", config);
      await _axios
        .post<SystemUser>("/bff/register", {}, config)
        .then(result => {
          setSystemUser(result.data);
          logger.debug("<<fetched registered %s", result);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };
    register({
      headers: getBearerToken(keycloak)
    }).finally(() => setLoading(false));
  }, [keycloak]);

  if (loading) {
    return <div>Loading...</div>;
  }
  if (error) {
    return handleError(error, true);
  }
  if (systemUser) {
    return <div>Registered - {systemUser?.email}</div>;
  }
  return <div>Registering...</div>;
};

export default Registration;
