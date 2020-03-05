import { SystemUser } from "../../types/beancounter";
import logger from "../ConfigLogging";
import { _axios } from "../axiosUtils";

export const registerUser = (config: {
  headers: { Authorization: string };
}): Promise<SystemUser> => {
  logger.debug(">>apiRegister");
  return _axios.post<SystemUser>(`/bff/register`, config).then(result => {
    logger.debug("<<apiRegister %s", result.data.email);
    return result.data;
  });
};
