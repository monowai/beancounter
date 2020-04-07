import { SystemUser } from "../../types/beancounter";
import logger from "../configLogging";
import { _axios } from "../axiosUtils";

export const registerUser = (config: {
  headers: { Authorization: string };
}): Promise<SystemUser> => {
  logger.debug(">>postData");
  return _axios.post<SystemUser>(`/bff/register`, config).then((result) => {
    logger.debug("<<postData %s", result.data.email);
    return result.data;
  });
};
