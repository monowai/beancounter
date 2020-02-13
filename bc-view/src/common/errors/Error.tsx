import React from "react";
import "./Error.scss";
import { RouteComponentProps } from "react-router-dom";
import { serverEnv } from "../utils";

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

export default function Error(props: RouteComponentProps): JSX.Element | null {
  const debug = serverEnv("NODE_ENV", "development") !== "production";
  // const { t } = useTranslation();
  const { location = { state: props } } = props;
  const errorMessage = debug ? JSON.stringify(location, null, 2) : "";

  return (
    <div className="centered">
      <img src="/assets/error-global-icon.svg" />
      <h1>{"error-global-oops"}</h1>
      <p>{"error-global"}</p>
      <button
        className="bc-button active rounded"
        onClick={(e: React.MouseEvent) => {
          e.preventDefault();
          props.history.goBack();
        }}
      >
        {"error-tryagain"}
      </button>
      <DevMessage debug={debug} errorMessage={errorMessage} />
    </div>
  );
}
