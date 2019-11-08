import React from "react";
import App from "./App";
import { Route, Switch } from "react-router-dom";

const SSR = (): JSX.Element => (
  <Switch>
    <Route path="*" component={App} />
  </Switch>
);

export default SSR;
