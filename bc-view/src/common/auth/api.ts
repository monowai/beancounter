import { SystemUser } from "../../types/beancounter";
import { axiosBff } from "../utils";
import logger from "../ConfigLogging";

export const registerUser = (config: {
  headers: { Authorization: string };
}): Promise<SystemUser> => {
  logger.debug(">>apiRegister");
  return axiosBff()
    .post<SystemUser>(`/bff/register`, config)
    .then(result => {
      logger.debug("<<apiRegister %s", result.data.email);
      return result.data;
    });
};
