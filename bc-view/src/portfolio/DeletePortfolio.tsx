import React, { useState } from "react";
import logger from "../common/ConfigLogging";
import { _axios, getBearerToken } from "../common/axiosUtils";
import handleError from "../common/errors/UserError";
import { usePortfolio } from "./hooks";
import { AxiosError } from "axios";
import { useHistory } from "react-router";
import { useCurrencies } from "../static/currencies";

export function DeletePortfolio(portfolioId: string): React.ReactElement {
  const [pfId] = useState<string>(portfolioId);
  const [portfolio, pfError] = usePortfolio(pfId);
  const currencies = useCurrencies();
  const [error, setError] = useState<AxiosError>();
  const history = useHistory();
  function deletePf(): void {
    if (portfolio) {
      _axios
        .delete<void>(`/bff/portfolios/${portfolio.id}`, {
          headers: getBearerToken()
        })
        .then(result => {
          logger.debug("<<deleted Portfolio");
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  }
  if (pfError) {
    return handleError(pfError, true);
  }
  if (error) {
    return handleError(error, true);
  }

  if (!portfolio) {
    return <div id="root">Loading...</div>;
  }
  if (portfolio) {
    return (
      <section className="page-box hero is-primary is-fullheight">
        <div className="hero-body">
          <div className="container">
            <div className="columns is-centered">
              <form onSubmit={deletePf} className="column is-5-tablet is-4-desktop is-3-widescreen">
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
                    <button
                      className="button is-link is-warning"
                      onClick={() => {
                        deletePf();
                        history.push("/portfolios");
                      }}
                    >
                      Delete
                    </button>
                  </div>
                  <div className="control">
                    <button
                      className="button is-link is-light"
                      onClick={() => {
                        history.goBack();
                      }}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </form>
            </div>
          </div>
        </div>
      </section>
    );
  }
  return <div id="root">Portfolio not found!</div>;
}
