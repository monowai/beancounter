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

export function ManagePortfolio(portfolioId: string): React.ReactElement {
  const [keycloak] = useKeycloak();
  const { register, handleSubmit, errors } = useForm<PortfolioInput>();
  const [pfId, setPortfolioId] = useState<string>(portfolioId);
  const [portfolio, pfError] = usePortfolio(pfId);
  const currencies = useCurrencies();
  const [error, setError] = useState<AxiosError>();
  const history = useHistory();

  const title = (): JSX.Element => {
    return (
      <section className="page-box is-centered page-title">
        {pfId === "new" ? "Create" : "Edit"} Portfolio
      </section>
    );
  };
  const handleCancel = (): void => {
    return history.goBack();
  };

  const savePortfolio = handleSubmit((portfolioInput: PortfolioInput) => {
    if (portfolio) {
      if (portfolioId === "new") {
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
            history.push(`/portfolios`);
          })
          .catch(err => {
            setError(err);
            if (err.response) {
              logger.error(
                "axios error [%s]: [%s]",
                err.response.status,
                err.response.data.message
              );
            }
          });
      } else {
        _axios
          .patch<Portfolio>(`/bff/portfolios/${portfolio.id}`, portfolioInput, {
            headers: getBearerToken(keycloak.token)
          })
          .then(result => {
            logger.debug("<<patched Portfolio");
            setPortfolioId(result.data.id);
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
    } // portfolio
  });
  if (pfError) {
    return ErrorPage(pfError.stack, pfError.message);
  }
  if (error) {
    return ErrorPage(error.stack, error.message);
  }

  if (!portfolio) {
    return <div id="root">Loading...</div>;
  }
  if (errors) {
    console.log(errors);
  }
  if (portfolio) {
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
                  <label className="label">Portfolio Currency</label>
                  <div className="control">
                    <select
                      placeholder={"Select currency"}
                      className={"select is-3"}
                      name={"currency"}
                      defaultValue={portfolio.currency.code}
                      ref={register({ required: true })}
                    >
                      {currencyOptions(currencies)}
                    </select>
                  </div>
                </div>
                <div className="field">
                  <label className="label">Common Currency</label>
                  <div className="control">
                    <select
                      placeholder={"Select currency"}
                      className={"select is-3"}
                      name={"base"}
                      defaultValue={portfolio.base.code}
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
  return <div id="root">Portfolio not found!</div>;
}
