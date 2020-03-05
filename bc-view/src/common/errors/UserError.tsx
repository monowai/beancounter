import { AxiosError } from "axios";
import { LoginRedirect } from "../auth/Login";
import logger from "../ConfigLogging";
import React from "react";

export default function handleError(error: AxiosError<any>, withRedirect: boolean): JSX.Element {
  const { response } = error;
  if (response) {
    const { data: errData, status } = response;
    if (status === 401 && withRedirect) {
      return <LoginRedirect />;
    }
    logger.error("Error: %s", errData.message);
    return <div>{errData.message}</div>;
  }
  return <div>Unknown error</div>;
}
