import React, { useState } from "react";
import { Redirect, useLocation, withRouter } from "react-router";
import { useKeycloak } from "@react-keycloak/nextjs";
import logger from "../ConfigLogging";
import { registerUser } from "./api";
import { getBearerToken } from "../../keycloak/utils";

const Login = (): JSX.Element => {
  const location = useLocation();
  const [loggingIn, setLoggingIn] = useState<boolean>(false);
  const { keycloak, initialized } = useKeycloak();
  if (keycloak) {
    // Object destructuring
    const { authenticated } = keycloak;
    if (authenticated) {
      return (
        <Redirect
          to={{
            pathname: "/",
            state: { from: location }
          }}
        />
      );
    } else if (initialized && !loggingIn) {
      setLoggingIn(true);
      logger.debug("About to login");
      keycloak
        .login()
        .then(() => {
          logger.debug("Logged in");
          if (keycloak.authenticated) {
            registerUser({
              headers: getBearerToken(keycloak)
            }).then(() => {
              logger.info("Registered user");
            });
          }
        })
        .finally(() => setLoggingIn(false));
    }

    return <div>KeyCloak is intializing...</div>;
  }
  return <div>KeyCloak is not initialized...</div>;
};

export const LoginRedirect = (): JSX.Element => {
  const location = useLocation();
  return (
    <Redirect
      to={{
        pathname: "/login",
        state: { from: location }
      }}
    />
  );
};
export default withRouter(Login);
