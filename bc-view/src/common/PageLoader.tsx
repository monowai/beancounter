import React from "react";

export default function PageLoader(props: { message: string; show: boolean }): JSX.Element {
  if (!props.show) {
    return <div />;
  }
  return (
    <div className="pageloader is-active is-success">
      <span className="title">{props.message}</span>
    </div>
  );
}
