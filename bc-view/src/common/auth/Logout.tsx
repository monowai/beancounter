import { useKeycloak } from "@react-keycloak/razzle";
import logger from "../ConfigLogging";
import { LoginRedirect } from "./Login";

const Logout = (): JSX.Element => {
  const [keycloak] = useKeycloak();
  keycloak.logout({ redirectUri: window.location.origin }).then(() => logger.debug("logged out"));
  return LoginRedirect();
};

export default Logout;
