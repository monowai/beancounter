import { LoginRedirect } from "../auth/Login";
import { ErrorPage } from "./ErrorPage";
import { AxiosError } from "axios";
import logger from "../configLogging";
import React from "react";
import { useKeycloak } from "@react-keycloak/razzle";

export function ShowError(props: { error: AxiosError }): JSX.Element {
  const { keycloak, initialized } = useKeycloak();
  if (props.error.code === "401" || props.error.response?.status === 401) {
    logger.debug("Login Redirect...");
    keycloak?.clearToken();
    keycloak?.logout();
    return <LoginRedirect />;
  }
  return ErrorPage(props.error.stack, props.error.message);
}
