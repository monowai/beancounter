import React, { useState } from "react";
import { useForm } from "react-hook-form";
import logger from "../common/ConfigLogging";
import { Portfolio, PortfolioInput } from "../types/beancounter";
import { _axios, getBearerToken } from "../common/axiosUtils";
import handleError from "../common/errors/UserError";
import { currencyOptions, useCurrencies } from "../static/currencies";
import { usePortfolio } from "./hooks";
import { AxiosError } from "axios";
import { useHistory } from "react-router";

export function ManagePortfolio(portfolioId: string): React.ReactElement {
  const { register, handleSubmit, errors } = useForm<PortfolioInput>();
  const [pfId, setPortfolioId] = useState<string>(portfolioId);
  const [portfolio, pfError] = usePortfolio(pfId);
  const currencies = useCurrencies();
  const [error, setError] = useState<AxiosError>();
  const history = useHistory();

  const savePortfolio = handleSubmit((portfolioInput: PortfolioInput) => {
    if (portfolio) {
      _axios
        .patch<Portfolio>(`/bff/portfolios/${portfolio.id}`, portfolioInput, {
          headers: getBearerToken()
        })
        .then(result => {
          logger.debug("<<patched Portfolio");
          setPortfolioId(result.data.id);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  });
  if (pfError) {
    return handleError(pfError, true);
  }
  if (error) {
    return handleError(error, true);
  }

  if (!portfolio) {
    return <div id="root">Loading...</div>;
  }
  if (errors) {
    console.log(errors);
  }
  if (portfolio) {
    return (
      <section className="page-box hero is-primary is-fullheight">
        <div className="hero-body">
          <div className="container">
            <div className="columns is-centered">
              <form
                onSubmit={savePortfolio}
                className="column is-5-tablet is-4-desktop is-3-widescreen"
              >
                <label className="label ">Code</label>
                <div className="control ">
                  <input
                    type="text"
                    className={"input"}
                    autoFocus={true}
                    placeholder="code"
                    name="code"
                    defaultValue={portfolio?.code}
                    ref={register({ required: true, maxLength: 10 })}
                  />
                </div>
                <div className="field">
                  <label className="label">Name</label>
                  <div className="control">
                    <input
                      className="input is-3"
                      type="text"
                      placeholder="name"
                      defaultValue={portfolio.name}
                      name="name"
                      ref={register({ required: true, maxLength: 100 })}
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Reporting Currency</label>
                  <div className="control">
                    <select
                      placeholder={"Select currency"}
                      className={"select is-3"}
                      defaultValue={portfolio.currency.code}
                      name={"currency"}
                      ref={register({ required: true })}
                    >
                      {currencyOptions(currencies)}
                    </select>
                  </div>
                </div>
                <div className="field">
                  <label className="label">Reference Currency</label>
                  <div className="control">
                    <select
                      placeholder={"Select currency"}
                      className={"select is-3"}
                      defaultValue={portfolio.base.code}
                      name={"base"}
                      ref={register({ required: true })}
                    >
                      {currencyOptions(currencies)}
                    </select>
                  </div>
                </div>
                <div className="field is-grouped">
                  <div className="control">
                    <button className="button is-link">Submit</button>
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
