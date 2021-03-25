import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { Transaction, TrnInput } from "../types/beancounter";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useCurrencies } from "../static/hooks";
import { AxiosError } from "axios";
import { useHistory } from "react-router";
import { useKeycloak } from "@react-keycloak/ssr";
import { ErrorPage } from "../errors/ErrorPage";
import { useTransaction } from "./hooks";
import { isDone } from "../types/typeUtils";
import { currencyOptions } from "../static/IsoHelper";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { ShowError } from "../errors/ShowError";
import { translate } from "../common/i18nUtils";

export function TransactionEdit(portfolioId: string, trnId: string): React.ReactElement {
  const { keycloak } = useKeycloak();

  const schema = yup.object().shape({
    trnType: yup.string(),
    quantity: yup.number().required(),
    // fees: yup.number(),
    // tax: yup.number(),
    // price: yup.number(),
    // tradeDate: yup.date(),
  });
  const { register, handleSubmit } = useForm<TrnInput>({
    resolver: yupResolver(schema),
  });
  const trnResult = useTransaction(portfolioId, trnId);
  const currencyResult = useCurrencies();
  const [stateError, setError] = useState<AxiosError>();
  const history = useHistory();
  const [submitted, setSubmitted] = useState(false);

  const title = (): JSX.Element => {
    return (
      <section className="page-box is-centered page-title">
        {trnId === "new" ? "Create" : "Edit"} Transaction
      </section>
    );
  };

  const handleCancel = (): void => {
    history.goBack();
  };

  const deleteTransaction = handleSubmit(() => {
    if (confirm(translate("delete.trn"))) {
      _axios
        .delete<Transaction>(`/bff/trns/${trnId}`, {
          headers: getBearerToken(keycloak?.token),
        })
        .then(() => {
          console.debug("<<delete Trn");
          setSubmitted(true);
        })
        .catch((err) => {
          setError(err);
          if (err.response) {
            console.error("deleteTrn [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  });

  const saveTransaction = handleSubmit((trnInput: TrnInput) => {
    if (trnId === "new") {
      _axios
        .post<Transaction>(
          "/bff/trns",
          { data: [trnInput] },
          {
            headers: getBearerToken(keycloak?.token),
          }
        )
        .then(() => {
          console.debug("<<post Trn");
          setSubmitted(true);
        })
        .catch((err) => {
          setError(err);
          if (err.response) {
            console.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    } else {
      _axios
        .patch<Transaction>(`/bff/trns/${trnId}`, trnInput, {
          headers: getBearerToken(keycloak?.token),
        })
        .then(() => {
          console.debug("<<patch Trn");
          setSubmitted(true);
        })
        .catch((err) => {
          setError(err);
          if (err.response) {
            console.error("patchedTrn [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
      // New Portfolio
    } // portfolioId.callerId
  });

  if (submitted) {
    history.goBack();
  }

  if (stateError) {
    return <ShowError error={stateError} />;
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
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Quantity</label>
                  <div className="control">
                    <input
                      className="input"
                      type="number"
                      placeholder="quantity"
                      defaultValue={trnResult.data.quantity}
                      name="quantity"
                      ref={register()}
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
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Fees</label>
                  <div className="control">
                    <input
                      className="input"
                      type="number"
                      placeholder="Fees and charges"
                      defaultValue={trnResult.data.fees}
                      name="fees"
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Tax</label>
                  <div className="control">
                    <input
                      className="input"
                      type="number"
                      placeholder="Tax Paid"
                      defaultValue={trnResult.data.tax}
                      name="tax"
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Trade PF</label>
                  <div className="control">
                    <input
                      className="input"
                      type="number"
                      placeholder="Trade to PF"
                      defaultValue={trnResult.data.tradePortfolioRate}
                      name="tradePortfolioRate"
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Trade Base</label>
                  <div className="control">
                    <input
                      className="input"
                      type="number"
                      placeholder="Trade to Base"
                      defaultValue={trnResult.data.tradeBaseRate}
                      name="tradeBaseRate"
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
                    >
                      {currencyOptions(currencies, trnResult.data.tradeCurrency.code)}
                    </select>
                  </div>
                </div>
                <div className="field is-grouped">
                  <div className="control">
                    <button className="button is-link">Save</button>
                  </div>
                  <div className="control">
                    <button className="button is-link is-light" onClick={handleCancel}>
                      Cancel
                    </button>
                  </div>
                  <div className="control">
                    <button className="button is-link is-danger" onClick={deleteTransaction}>
                      Delete
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
