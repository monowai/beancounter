import React, { Suspense } from "react";
import { hydrate } from "react-dom";
import { BrowserRouter, Route, Switch } from "react-router-dom";
import { useSSR } from "react-i18next";

import App from "./App";

const BaseApp = (): JSX.Element => {
  useSSR(window.initialI18nStore, window.initialLanguage);

  return (
    <Suspense fallback={<div>Loading ...</div>}>
      <BrowserRouter>
        <Switch>
          <Route path="*" component={App} />
        </Switch>
      </BrowserRouter>
    </Suspense>
  );
};

hydrate(<BaseApp />, document.getElementById("root"));

if (module.hot) {
  module.hot.accept();
}
