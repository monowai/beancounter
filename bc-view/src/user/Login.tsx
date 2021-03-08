import React from "react";
import { Redirect } from "react-router";
import { useLogin } from "./loginHook";

export const Login = (): JSX.Element => {
  const loggedIn = useLogin();
  if (loggedIn) {
    return <Redirect to="/portfolios" />;
  }

  return <div>Logging in...</div>;
};
