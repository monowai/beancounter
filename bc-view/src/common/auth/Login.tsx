import React, { useState } from "react";
import { Redirect, useLocation } from "react-router";
import { useKeycloak } from "@react-keycloak/razzle";
import logger from "../configLogging";

const Login = (): JSX.Element => {
  const location = useLocation();
  const [loggingIn, setLoggingIn] = useState<boolean>(false);
  const { keycloak, initialized } = useKeycloak();
  if (keycloak) {
    const { login, token } = keycloak;
    if (token) {
      setLoggingIn(false);
      let path;
      location.pathname === "/login" ? (path = "/") : (path = location.pathname);
      return (
        <Redirect
          to={{
            pathname: path,
            state: { from: location },
          }}
        />
      );
    } else if (initialized && !loggingIn) {
      setLoggingIn(true);
      login()
        .then(() => {
          logger.debug("Logging in");
        })
        .finally(() => setLoggingIn(false));
      //
    }

    return <div>KeyCloak is initializing...</div>;
  }
  return <div>KeyCloak is not initialized...</div>;
};

export const LoginRedirect = (): JSX.Element => {
  const location = useLocation();
  return (
    <Redirect
      to={{
        pathname: "/login",
        state: { from: location },
      }}
    />
  );
};
export default Login;
