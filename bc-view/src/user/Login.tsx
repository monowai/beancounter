import React from "react";
import { Redirect, useLocation } from "react-router";
import { useKeycloak } from "@react-keycloak/ssr";
import logger from "../common/configLogging";
import { initConfig } from "../common/kcConfig";
import { Portfolios } from "../portfolio/Portfolios";

const Login = (): JSX.Element => {
  const { keycloak } = useKeycloak();
  keycloak?.init(initConfig).then(function (authenticated) {
    if (authenticated) {
      logger.debug("Logged in: auth= " + authenticated);
      return <Portfolios />;
    }
    return <div>Auth Failed...</div>;
  });
  return <div>Auth not initialised...</div>;
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
