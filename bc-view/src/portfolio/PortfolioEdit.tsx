import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { Portfolio, PortfolioInput } from "../types/beancounter";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useCurrencies } from "../static/hooks";
import { usePortfolio } from "./hooks";
import { AxiosError } from "axios";
import { useHistory } from "react-router";
import { useKeycloak } from "@react-keycloak/ssr";
import { ErrorPage } from "../errors/ErrorPage";
import { isDone } from "../types/typeUtils";
import { currencyOptions } from "../static/IsoHelper";
import { TrnDropZone } from "./DropZone";
import { ShowError } from "../errors/ShowError";

export function PortfolioEdit(portfolioId: string): React.ReactElement {
  const { keycloak } = useKeycloak();
  const { register, handleSubmit, errors } = useForm<PortfolioInput>();
  const [pfId, setPortfolioId] = useState<string>(portfolioId);
  const portfolioResult = usePortfolio(pfId);
  const [purgeTrn, setPurgeTrn] = useState(false);
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
    history.goBack();
  };

  const savePortfolio = handleSubmit((portfolioInput: PortfolioInput) => {
    if (keycloak && keycloak.token) {
      if (pfId === "new") {
        _axios
          .post<Portfolio[]>(
            "/bff/portfolios",
            { data: [portfolioInput] },
            {
              headers: getBearerToken(keycloak.token),
            }
          )
          .then((result) => {
            console.debug("<<post Portfolio");
            setPortfolioId(result.data[0].id);
            setSubmitted(true);
          })
          .catch((err) => {
            setError(err);
            if (err.response) {
              console.error(
                "axios error [%s]: [%s]",
                err.response.status,
                err.response.data.message
              );
            }
          });
      } else {
        _axios
          .patch<Portfolio>(`/bff/portfolios/${pfId}`, portfolioInput, {
            headers: getBearerToken(keycloak.token),
          })
          .then((result) => {
            console.debug("<<patched Portfolio");
            setPortfolioId(result.data.id);
            setSubmitted(true);
          })
          .catch((err) => {
            setError(err);
            if (err.response) {
              console.error(
                "patchPortfolio [%s]: [%s]",
                err.response.status,
                err.response.data.message
              );
            }
          });
      }
    }
  });

  if (submitted) {
    history.goBack();
  }
  if (portfolioResult.error) {
    return <ShowError error={portfolioResult.error} />;
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
                      ref={register({ required: true })}
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
                <div className="field">
                  <label className="checkbox">
                    <input
                      type="checkbox"
                      checked={purgeTrn}
                      onChange={() => setPurgeTrn(!purgeTrn)}
                    />
                    Delete existing transactions
                  </label>
                </div>

                <div className="field">
                  <TrnDropZone portfolio={portfolio} purgeTrn={purgeTrn} />
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
