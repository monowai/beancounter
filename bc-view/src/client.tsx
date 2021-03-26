import React, { Suspense } from "react";
import { hydrate } from "react-dom";
import { BrowserRouter, Route, Switch } from "react-router-dom";
import { useSSR } from "react-i18next";

import App from "./App";
import { keycloakConfig } from "./common/kcConfig";
import { Cookies, SSRKeycloakProvider } from "@react-keycloak/ssr";
import { Resource } from "i18next";
import "./i18nConfig";

declare global {
  interface WindowI18n extends Window {
    initialI18nStore: Resource;
    initialLanguage: string;
  }
}

const cookiePersistor = new Cookies();
const BaseApp = (): JSX.Element => {
  useSSR((window as WindowI18n).initialI18nStore, (window as WindowI18n).initialLanguage);

  return (
    <SSRKeycloakProvider keycloakConfig={keycloakConfig} persistor={cookiePersistor}>
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
