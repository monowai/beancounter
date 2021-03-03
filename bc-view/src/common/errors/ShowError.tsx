import { ErrorPage } from "./ErrorPage";
import { AxiosError } from "axios";
import logger from "../configLogging";
import React from "react";
import { LoginRedirect } from "../auth/Login";

export function ShowError(props: { error: AxiosError }): JSX.Element {
  if (props.error.code === "401" || props.error.response?.status === 401) {
    logger.debug("Login Redirect...");
    return <LoginRedirect />;
  }
  return ErrorPage(props.error.stack, props.error.message);
}
