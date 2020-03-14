import React, { Suspense } from "react";
import { hydrate } from "react-dom";
import { BrowserRouter, Route, Switch } from "react-router-dom";
import { useSSR } from "react-i18next";

import App from "./App";
import { ClientPersistors, SSRKeycloakProvider } from "@react-keycloak/razzle";
import { keycloakConfig } from "./common/kcConfig";

declare global {
  interface WindowI18n extends Window {
    initialI18nStore: any;
    initialLanguage: any;
  }
}

const BaseApp = (): JSX.Element => {
  useSSR((window as WindowI18n).initialI18nStore, (window as WindowI18n).initialLanguage);

  return (
    <SSRKeycloakProvider keycloakConfig={keycloakConfig} persistor={ClientPersistors.Cookies}>
      <Suspense fallback={<div>Loading ...</div>}>
        <BrowserRouter>
          <Switch>
            <Route path="*" component={App} />
          </Switch>
        </BrowserRouter>
      </Suspense>
    </SSRKeycloakProvider>
  );
};

hydrate(<BaseApp />, document.getElementById("root"));

if (module.hot) {
  module.hot.accept();
}
