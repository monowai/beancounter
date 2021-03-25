import React, { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { AxiosError } from "axios";
import { SystemUser } from "../types/beancounter";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { ShowError } from "../errors/ShowError";

const Registration = (): JSX.Element => {
  const [systemUser, setSystemUser] = useState<SystemUser>();
  const [error, setError] = useState<AxiosError>();
  const { keycloak, initialized } = useKeycloak();

  useEffect(() => {
    const register = async (): Promise<void> => {
      console.debug("Registering %s", keycloak?.token);
      await _axios
        .post<SystemUser>(
          "/bff/register",
          {},
          {
            headers: getBearerToken(keycloak?.token),
          }
        )
        .then((result) => {
          setSystemUser(result.data);
          console.debug("<<fetched registered %s", result);
        })
        .catch((err) => {
          setError(err);
          if (err.response) {
            console.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };
    register().finally(() => console.log("Registered"));
  }, [initialized, keycloak?.token]);

  if (error) {
    return <ShowError error={error} />;
  }
  if (systemUser) {
    return <div>Registered - {systemUser?.email}</div>;
  }
  return <div>Registering...</div>;
};

export default Registration;
