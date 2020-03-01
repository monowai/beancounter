import { Keycloak } from "./keycloak";

export const getBearerToken = (
  keycloak: Keycloak.KeycloakInstance<"native">
): { Authorization: string } => ({
  Authorization: `Bearer ${!keycloak ? "undefined" : keycloak.token}`
});
