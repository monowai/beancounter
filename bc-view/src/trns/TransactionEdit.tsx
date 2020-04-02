import React, { useState } from "react";
import { useForm } from "react-hook-form";
import logger from "../common/configLogging";
import { Transaction, TrnInput } from "../types/beancounter";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useCurrencies } from "../static/hooks";
import { AxiosError } from "axios";
import { useHistory } from "react-router";
import { useKeycloak } from "@react-keycloak/razzle";
import ErrorPage from "../common/errors/ErrorPage";
import { useTransaction } from "./hooks";
import { isDone } from "../types/typeUtils";
import { currencyOptions } from "../static/IsoHelper";

export function TransactionEdit(provider: string, batch: string, id: string): React.ReactElement {
  const [keycloak] = useKeycloak();
  const { register, handleSubmit } = useForm<TrnInput>();
  const trnResult = useTransaction({
    provider: provider,
    batch: batch,
    callerId: id
  });
  const currencyResult = useCurrencies();
  const [stateError, setError] = useState<AxiosError>();
  const history = useHistory();
  const [submitted, setSubmitted] = useState(false);

  const title = (): JSX.Element => {
    return (
      <section className="page-box is-centered page-title">
        {provider === "new" ? "Create" : "Edit"} Transaction
      </section>
    );
  };
  const handleCancel = (): void => {
    history.goBack();
  };

  const saveTransaction = handleSubmit((trnInput: TrnInput) => {
    if (provider === "new") {
      _axios
        .post<Transaction>(
          "/bff/trns",
          { data: [trnInput] },
          {
            headers: getBearerToken(keycloak.token)
          }
        )
        .then(() => {
          logger.debug("<<post Trn");
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
        .patch<Transaction>(`/bff/trns/${provider}/${batch}/${id}`, trnInput, {
          headers: getBearerToken(keycloak.token)
        })
        .then(() => {
          logger.debug("<<patch Trn");
          setSubmitted(true);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("patchedTrn [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
      // New Portfolio
    } // portfolio.callerId
  });

  if (submitted) {
    history.goBack();
  }

  if (stateError) {
    return ErrorPage(stateError.stack, stateError.message);
  }

  if (trnResult.error) {
    return ErrorPage(trnResult.error.stack, trnResult.error.message);
  }

  if (isDone(trnResult) && isDone(currencyResult)) {
    const currencies = currencyResult.data;
    return (
      <div>
        {title()}
        <section className="is-primary">
          <div className="container">
            <div className="columns is-centered is-3">
              <form
                onSubmit={saveTransaction}
                onAbort={handleCancel}
                className="column is-5-tablet is-4-desktop is-3-widescreen"
              >
                <label className="label ">Type</label>
                <div className="control ">
                  <input
                    type="label"
                    className={"text"}
                    placeholder="Type"
                    name="type"
                    defaultValue={trnResult.data.trnType}
                    ref={register({ required: true, maxLength: 10 })}
                  />
                </div>
                <div className="field">
                  <label className="label">Trade Date</label>
                  <div className="control">
                    <input
                      className="input is-4"
                      type="string"
                      placeholder="Date of trade"
                      defaultValue={trnResult.data.tradeDate}
                      name="tradeDate"
                      ref={register({ required: true, maxLength: 100 })}
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Price</label>
                  <div className="control">
                    <input
                      className="input"
                      type="number"
                      placeholder="price"
                      defaultValue={trnResult.data.price}
                      name="price"
                      ref={register({ required: true, maxLength: 10 })}
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Amount</label>
                  <div className="control">
                    <input
                      className="input"
                      type="number"
                      placeholder="Amount in Trade Currency"
                      defaultValue={trnResult.data.tradeAmount}
                      name="tradeAmount"
                      ref={register({ required: true, maxLength: 100 })}
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Trade Currency</label>
                  <div className="control">
                    <select
                      placeholder={"Select currency"}
                      className={"select"}
                      name={"tradeCurrency"}
                      defaultValue={trnResult.data.tradeCurrency.code}
                      ref={register({ required: true })}
                    >
                      {currencyOptions(currencies, trnResult.data.tradeCurrency.code)}
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
