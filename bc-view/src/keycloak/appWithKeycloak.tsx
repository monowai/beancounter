import { getKeycloakInstance } from "./keycloak";

const appWithKeycloak = () => (App: any) => {
  const keycloak = getKeycloakInstance();
};

export default appWithKeycloak;
