import { useEffect } from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { initConfig } from "../common/kcConfig";
import { ErrorPage } from "../errors/ErrorPage";
import logger from "../common/configLogging";

export function useLogin(): undefined | boolean {
  const { keycloak } = useKeycloak();
  useEffect(() => {
    if (!keycloak?.authenticated) {
      keycloak
        ?.init(initConfig)
        .then(function (authenticated) {
          logger.debug(authenticated);
          return authenticated;
        })
        .catch((err) => {
          return ErrorPage("Auth issue", err.message);
        });
    }
  }, [keycloak, keycloak?.authenticated]);
  return keycloak?.authenticated;
}
