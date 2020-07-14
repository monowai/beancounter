import React from "react";
import "./Error.scss";
import { serverEnv } from "../utils";
import { useHistory } from "react-router";
import { translate } from "../i18nConfig";

function DevMessage(debug: boolean, errorMessage: string): JSX.Element | null {
  return debug ? (
    <div className="rockstar">
      <h1 className="mono">Details Dire Dév!</h1>
      <pre>
        <span>Your request failed with the following response:</span>
        {errorMessage}
      </pre>
    </div>
  ) : null;
}

export default function ErrorPage(stack: string | undefined, message: string): JSX.Element {
  const debug = serverEnv("NODE_ENV", "development") !== "production";
  const history = useHistory();
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
      {DevMessage(debug, errorMessage)}
    </div>
  );
}
