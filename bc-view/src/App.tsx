import React from "react";
import "./common/i18nConfig";
import { KeycloakProvider } from "@react-keycloak/web";
import { keycloak, keycloakProviderInitConfig } from "./keycloak/keycloak";
import { Switch } from "react-router";
import Routes from "./routes";
import { renderRoutes } from "react-router-config";

const App = (): JSX.Element => {
  return (
    <KeycloakProvider keycloak={keycloak} initConfig={keycloakProviderInitConfig}>
      <div>
        <div className={"columns"}>
          <div className={"column is-centered"}>Welcome to Bean Counter</div>
        </div>
        <Switch>{renderRoutes(Routes)}</Switch>
      </div>
    </KeycloakProvider>
  );
};

export default App;
