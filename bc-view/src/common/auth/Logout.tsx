import React from "react";
import { Redirect, useLocation, withRouter } from "react-router";
import { useKeycloak } from "@react-keycloak/nextjs";
import logger from "../ConfigLogging";

const Logout = (): JSX.Element => {
  const location = useLocation();
  const { keycloak } = useKeycloak();
  if (keycloak) {
    keycloak.logout({ redirectUri: window.location.origin }).then(() => logger.debug("logged out"));
  }
  return (
    <Redirect
      to={{
        pathname: "/",
        state: { from: location }
      }}
    />
  );
};

export default withRouter(Logout);
