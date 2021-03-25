import React, { useState } from "react";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { usePortfolio } from "./hooks";
import { AxiosError } from "axios";
import { useHistory } from "react-router";
import { useKeycloak } from "@react-keycloak/ssr";
import { ErrorPage } from "../errors/ErrorPage";
import { isDone } from "../types/typeUtils";
import { ShowError } from "../errors/ShowError";

export function DeletePortfolio(portfolioId: string): JSX.Element {
  const [pfId] = useState<string>(portfolioId);
  const [error, setError] = useState<AxiosError>();
  const history = useHistory();
  const { keycloak } = useKeycloak();
  const portfolioResult = usePortfolio(pfId);
  const [submitted, setSubmitted] = useState(false);
  // const [deleting, setDeleting] = useState(false);

  const handleCancel = (): void => {
    history.push("/portfolios");
  };

  function deletePf(): void {
    if (portfolioResult.data.id && !submitted && keycloak) {
      setSubmitted(true);
      console.debug("delete PF %s", portfolioResult.data.id);
      _axios
        .delete<void>(`/bff/portfolios/${portfolioResult.data.id}`, {
          headers: getBearerToken(keycloak.token),
        })
        .then(() => {
          console.debug("Delete success");
        })
        .catch((err) => {
          setError(err);
          if (err.response) {
            console.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  }

  if (submitted) {
    setSubmitted(false);
    history.push("/portfolios");
  }

  if (error) {
    return <ShowError error={error} />;
  }
  if (!submitted && isDone(portfolioResult)) {
    if (portfolioResult.error) {
      return ErrorPage(portfolioResult.error.stack, portfolioResult.error.message);
    }
    const portfolio = portfolioResult.data;
    return (
      <div>
        <section className="page-box is-centered has-background-danger">Delete Portfolio</section>
        <section className="page-box is-primary is-fullheight">
          <div className="container">
            <div className="columns is-centered">
              <form
                onSubmit={deletePf}
                onAbort={handleCancel}
                className="column is-5-tablet is-4-desktop is-3-widescreen"
              >
                <label className="label ">Code</label>
                <div className="control ">
                  <input
                    type="text"
                    className={"input"}
                    autoFocus={true}
                    placeholder="code"
                    readOnly={true}
                    name="code"
                    defaultValue={portfolio?.code}
                  />
                </div>
                <div className="field">
                  <label className="label">Name</label>
                  <div className="control">
                    <input
                      className="input is-3"
                      type="text"
                      readOnly={true}
                      placeholder="name"
                      defaultValue={portfolio.name}
                      name="name"
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Reporting Currency</label>
                  <div className="control">
                    <input
                      className={"select is-3"}
                      name={"currency"}
                      readOnly={true}
                      value={portfolio?.currency.code}
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Reference Currency</label>
                  <div className="control">
                    <input
                      className={"select is-3"}
                      name={"currency"}
                      readOnly={true}
                      value={portfolio?.base.code}
                    />
                  </div>
                </div>
                <div className="field is-grouped">
                  <div className="control">
                    <button className="button is-link is-danger">Delete</button>
                  </div>
                  <div className="control">
                    <button className="button is-link is-light" onClick={handleCancel}>
                      Cancel
                    </button>
                  </div>
                </div>
              </form>
            </div>
          </div>
        </section>
      </div>
    );
  }
  return <div id="root">Loading...</div>;
}
