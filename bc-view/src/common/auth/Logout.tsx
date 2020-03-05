import React from "react";
import { Redirect, useLocation } from "react-router";
import { useKeycloak } from "@react-keycloak/web";
import logger from "../ConfigLogging";

const Logout = (): JSX.Element => {
  const location = useLocation();
  const { keycloak } = useKeycloak();
  if (keycloak) {
    keycloak.logout({ redirectUri: window.location.origin }).then(() => logger.debug("logged out"));
  }
  localStorage.removeItem("token");

  return (
    <Redirect
      to={{
        pathname: "/",
        state: { from: location }
      }}
    />
  );
};

export default Logout;
