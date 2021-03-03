import React from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import logger from "../common/configLogging";

const Logout = (): JSX.Element => {
  const { keycloak, initialized } = useKeycloak();
  if (initialized && keycloak != undefined) {
    logger.debug("How to logout");
    keycloak.token = undefined;
    keycloak.authenticated = false;
  }
  return <div>Logged Out</div>;
};

export default Logout;
