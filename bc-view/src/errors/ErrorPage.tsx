import React from "react";
import "./Error.scss";
import { serverEnv } from "../common/utils";
import { useHistory } from "react-router";
import { DevMessage } from "../types/app";
import { useKeycloak } from "@react-keycloak/ssr";
import { getBearerToken } from "../common/axiosUtils";
import { translate } from "../common/i18nUtils";

function ErrorDetail(devMessage: DevMessage): JSX.Element | null {
  return devMessage.debug ? (
    <div className="rockstar">
      <h1 className="mono">Details Dire Dév!</h1>
      <h2>Token - {devMessage.token}</h2>
      <pre>
        <span>Your request failed with the following response:</span>
        {devMessage.errorMessage}
      </pre>
    </div>
  ) : null;
}

export function ErrorPage(stack: string | undefined, message: string): JSX.Element {
  const debug = serverEnv("NODE_ENV", "development") !== "production";
  const history = useHistory();
  const { keycloak } = useKeycloak();
  const errorMessage = debug ? JSON.stringify({ message, stack }, null, 2) : message;

  function handleClick() {
    return (e: React.MouseEvent) => {
      e.preventDefault();
      history.goBack();
    };
  }

  function label(key: string): string {
    return translate(key);
  }

  return (
    <div className="centered">
      <img src="/assets/error-global-icon.svg" alt={"Error!"} />
      <h1>{label("error-global-oops")}</h1>
      <p>{label("error-global")}</p>
      <button className="bc-button active rounded" onClick={handleClick()}>
        {"error-tryagain"}
      </button>
      <ErrorDetail
        debug={debug}
        errorMessage={errorMessage}
        token={JSON.stringify(getBearerToken(keycloak?.token))}
      />
    </div>
  );
}
