import { useKeycloak } from "@react-keycloak/web";
import logger from "../ConfigLogging";
import { resetToken } from "../axiosUtils";
import { LoginRedirect } from "./Login";

const Logout = (): JSX.Element => {
  const { keycloak } = useKeycloak();
  resetToken();
  if (keycloak) {
    keycloak.logout({ redirectUri: window.location.origin }).then(() => logger.debug("logged out"));
  }
  return LoginRedirect();
};

export default Logout;
