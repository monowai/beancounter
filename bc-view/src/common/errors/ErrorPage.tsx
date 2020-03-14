import React from "react";
import "./Error.scss";
import { serverEnv } from "../utils";
import { useHistory } from "react-router";
import { AxiosError } from "axios";

const DevMessage = ({
  debug,
  errorMessage
}: {
  debug: boolean;
  errorMessage: string;
}): JSX.Element | null =>
  debug ? (
    <div className="rockstar">
      <h1 className="mono">Only for you Rock-Star Devs!</h1>
      <p>In case you&apos;re wondering, the Back button just works&trade;... :p</p>
      <pre>
        <span>Your request failed with the following response:</span>
        {errorMessage}
      </pre>
    </div>
  ) : null;

export default function ErrorPage(message: AxiosError<any>): JSX.Element {
  const debug = serverEnv("NODE_ENV", "development") !== "production";
  const history = useHistory();
  const errorMessage = debug ? JSON.stringify(message.stack, null, 2) : message.message;

  function handleClick() {
    return (e: React.MouseEvent) => {
      e.preventDefault();
      history.goBack();
    };
  }

  function label(key: string): string {
    return key;
  }
  return (
    <div className="centered">
      <img src="/assets/error-global-icon.svg" alt={"Error!"} />
      <h1>{label("error-global-oops")}</h1>
      <p>{label("error-global")}</p>
      <button className="bc-button active rounded" onClick={handleClick()}>
        {"error-tryagain"}
      </button>
      <DevMessage debug={debug} errorMessage={errorMessage} />
    </div>
  );
}
