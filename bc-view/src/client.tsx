import React, { Suspense } from "react";
import { hydrate } from "react-dom";
import { BrowserRouter, Route, Switch } from "react-router-dom";
import { useSSR } from "react-i18next";

import App from "./App";
import { initConfig, keycloakConfig } from "./common/kcConfig";
import { ClientPersistors, SSRKeycloakProvider } from "@react-keycloak/razzle";

declare global {
  interface WindowI18n extends Window {
    initialI18nStore: any;
    initialLanguage: any;
  }
}

const BaseApp = (): JSX.Element => {
  useSSR((window as WindowI18n).initialI18nStore, (window as WindowI18n).initialLanguage);

  return (
    // @ts-ignore
    <SSRKeycloakProvider
      keycloakConfig={keycloakConfig}
      // @ts-ignore
      persistor={ClientPersistors.Cookies}
      initConfig={initConfig}
    >
      <Suspense fallback={<div>Loading ...</div>}>
        <BrowserRouter>
          <Switch>
            <Route path="*" component={App} />
          </Switch>
        </BrowserRouter>
      </Suspense>
      // @ts-ignore
    </SSRKeycloakProvider>
  );
};
hydrate(<BaseApp />, document.getElementById("root"));

if (module.hot) {
  module.hot.accept();
}
