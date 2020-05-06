import { LoginRedirect } from "../auth/Login";
import ErrorPage from "./ErrorPage";
import { AxiosError } from "axios";
import logger from "../configLogging";

export function checkError(error: AxiosError): JSX.Element {
  if (error.code === "401" || error.response?.status === 401) {
    logger.debug("Login Redirect...");
    return LoginRedirect();
  }
  return ErrorPage(error.stack, error.message);
}
