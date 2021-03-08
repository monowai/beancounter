import React from "react";
import { Redirect, useLocation } from "react-router";
import { useKeycloak } from "@react-keycloak/ssr";
import { initConfig } from "../common/kcConfig";
import { Portfolios } from "../portfolio/Portfolios";
import { ErrorPage } from "../errors/ErrorPage";

const Login = (): JSX.Element => {
  const { keycloak } = useKeycloak();
  if (keycloak && !keycloak.authenticated) {
    keycloak
      .init(initConfig)
      .then(function (authenticated) {
        if (authenticated) {
          return <Portfolios />;
        }
        return <div>Auth Failed...</div>;
      })
      .catch((err) => {
        return ErrorPage("Auth issue", err.message);
      });
  }
  return <div>Initialising Auth...</div>;
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
