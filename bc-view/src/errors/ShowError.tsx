import { ErrorPage } from "./ErrorPage";
import { AxiosError } from "axios";
import logger from "../common/configLogging";
import React from "react";
import Home from "../Home";

export function ShowError(props: { error: AxiosError }): JSX.Element {
  if (props.error.code === "401" || props.error.response?.status === 401) {
    logger.debug("Login Redirect...");
    return <Home />;
  }
  return ErrorPage(props.error.stack, props.error.message);
}
