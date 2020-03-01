import { SystemUser } from "../../types/beancounter";
import { axiosBff } from "../utils";
import logger from "../ConfigLogging";

export const registerUser = (config: {
  headers: { Authorization: string };
}): Promise<SystemUser> => {
  logger.debug(">>register");
  return axiosBff()
    .post<SystemUser>(`/bff/register`, config)
    .then(result => {
      logger.debug("<<register %s", result.data.email);
      return result.data;
    });
};
