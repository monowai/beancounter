import React, { useState } from "react";
import { useForm } from "react-hook-form";
import logger from "../common/ConfigLogging";
import { Portfolio, PortfolioInput } from "../types/beancounter";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { currencyOptions, useCurrencies } from "../static/currencies";
import { usePortfolio } from "./hooks";
import { AxiosError } from "axios";
import { useHistory } from "react-router";
import { useKeycloak } from "@react-keycloak/razzle";
import ErrorPage from "../common/errors/ErrorPage";
import { isDone } from "../types/typeUtils";

export function PortfolioEdit(portfolioId: string): React.ReactElement {
  const [keycloak] = useKeycloak();
  const { register, handleSubmit, errors } = useForm<PortfolioInput>();
  const [pfId, setPortfolioId] = useState<string>(portfolioId);
  const portfolioResult = usePortfolio(pfId);
  const currencyResult = useCurrencies();
  const [error, setError] = useState<AxiosError>();
  const history = useHistory();
  const [submitted, setSubmitted] = useState(false);

  const title = (): JSX.Element => {
    return (
      <section className="page-box is-centered page-title">
        {pfId === "new" ? "Create" : "Edit"} Portfolio
      </section>
    );
  };
  const handleCancel = (): void => {
    history.push("/portfolios");
  };

  const savePortfolio = handleSubmit((portfolioInput: PortfolioInput) => {
    if (pfId === "new") {
      _axios
        .post<Portfolio[]>(
          "/bff/portfolios",
          { data: [portfolioInput] },
          {
            headers: getBearerToken(keycloak.token)
          }
        )
        .then(result => {
          logger.debug("<<post Portfolio");
          setPortfolioId(result.data[0].id);
          setSubmitted(true);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    } else {
      _axios
        .patch<Portfolio>(`/bff/portfolios/${pfId}`, portfolioInput, {
          headers: getBearerToken(keycloak.token)
        })
        .then(result => {
          logger.debug("<<patched Portfolio");
          setPortfolioId(result.data.id);
          setSubmitted(true);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error(
              "patchPortfolio [%s]: [%s]",
              err.response.status,
              err.response.data.message
            );
          }
        });
      // New Portfolio
    } // portfolio.id
  });

  if (submitted) {
    history.push("/portfolios");
  }
  if (error) {
    return ErrorPage(error.stack, error.message);
  }
  if (errors) {
    console.log(errors);
  }
  if (isDone(portfolioResult) && isDone(currencyResult)) {
    if (portfolioResult.error) {
      return ErrorPage(portfolioResult.error.stack, portfolioResult.error.message);
    }

    const currencies = currencyResult.data;
    const portfolio = portfolioResult.data;
    return (
      <div>
        {title()}
        <section className="is-primary is-fullheight">
          <div className="container">
            <div className="columns is-centered">
              <form
                onSubmit={savePortfolio}
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
                    name="code"
                    defaultValue={portfolio.code}
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
                  <label className="label">Portfolio Reporting Currency</label>
                  <div className="control">
                    <select
                      placeholder={"Select currency"}
                      className={"select is-3"}
                      name={"currency"}
                      defaultValue={portfolio.currency.code}
                      // onChange={e => {
                      //   const value = e.target.value;
                      //   const currency = get(currencies, value);
                      //   // if (currency) {
                      //   //   setValue("currency", currency[0].code);
                      //   //   portfolio.currency = currency[0];
                      //   // }
                      // }}
                      ref={register({ required: true })}
                    >
                      {currencyOptions(currencies, portfolio.currency.code)}
                    </select>
                  </div>
                </div>
                <div className="field">
                  <label className="label">Cross Portfolio Base Currency</label>
                  <div className="control">
                    <select
                      placeholder={"Select currency"}
                      className={"select is-3"}
                      name={"base"}
                      defaultValue={portfolio.base.code}
                    >
                      {currencyOptions(currencies, portfolio.base.code)}
                    </select>
                  </div>
                </div>
                <div className="field is-grouped">
                  <div className="control">
                    <button className="button is-link">Submit</button>
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