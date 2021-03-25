import { useEffect } from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { initConfig } from "../common/kcConfig";
import { ErrorPage } from "../errors/ErrorPage";

export function useLogin(): undefined | boolean {
  const { keycloak } = useKeycloak();
  useEffect(() => {
    if (!keycloak?.authenticated) {
      keycloak
        ?.init(initConfig)
        .then(function (authenticated) {
          console.debug(authenticated);
          return authenticated;
        })
        .catch((err) => {
          return ErrorPage("Auth issue", err.message);
        });
    }
  }, [keycloak, keycloak?.authenticated]);
  return keycloak?.authenticated;
}
