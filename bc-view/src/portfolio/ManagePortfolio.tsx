import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import logger from "../common/ConfigLogging";
import { Currency, Portfolio, PortfolioInput } from "../types/beancounter";
import { _axios, getBearerToken, setToken } from "../common/axiosUtils";
import { AxiosError } from "axios";
import { useKeycloak } from "@react-keycloak/web";
import handleError from "../common/errors/UserError";
import { currencyOptions } from "../static/currencies";

export function ManagePortfolio(code: string): React.ReactElement {
  const { register, handleSubmit, errors } = useForm<PortfolioInput>();
  const [portfolio, setPortfolio] = useState<Portfolio>();
  const [currencies, setCurrencies] = useState<Currency[]>();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();

  const savePortfolio = handleSubmit(({ code, name, base, currency }) => {
    const updatePortfolio = async (
      id: string,
      portfolioInput: PortfolioInput,
      config: {
        headers: { Authorization: string };
      }
    ): Promise<void> => {
      setLoading(true);
      logger.debug(">>patch portfolio %s", id);
      await _axios
        .patch<Portfolio>(`/bff/portfolios/${id}`, portfolioInput, config)
        .then(result => {
          logger.debug("<<patched Portfolio");
          setPortfolio(result.data);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };

    if (portfolio) {
      updatePortfolio(
        portfolio.id,
        { code, name, currency, base },
        {
          headers: getBearerToken()
        }
      ).finally(() => setLoading(false));
    }
  });

  useEffect(() => {
    const fetchCurrencies = async (config: {
      headers: { Authorization: string };
    }): Promise<void> => {
      setLoading(true);
      logger.debug(">>fetch getCurrencies");
      await _axios.get<Currency[]>("/bff/currencies", config).then(result => {
        logger.debug("<<fetched Currencies");
        setCurrencies(result.data);
      });
    };

    const fetchPortfolio = async (config: {
      headers: { Authorization: string };
    }): Promise<void> => {
      setLoading(true);
      logger.debug(">>fetch getData");
      await _axios
        .get<Portfolio>(`/bff/portfolios/code/${code}`, config)
        .then(result => {
          logger.debug("<<fetched getData");
          setPortfolio(result.data);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };
    setToken(keycloak);
    fetchCurrencies({
      headers: getBearerToken()
    }).finally(() => logger.debug("Currencies Loaded"));
    fetchPortfolio({
      headers: getBearerToken()
    }).finally(() => setLoading(false));
  }, [code, keycloak]);
  if (loading || !currencies) {
    return <div id="root">Loading...</div>;
  }
  if (error) {
    return handleError(error, true);
  }
  if (errors) {
    console.log(errors);
  }
  if (portfolio && currencies) {
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
                    <button className="button is-link is-light">Cancel</button>
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
