import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import logger from "../common/ConfigLogging";
import { Portfolio } from "../types/beancounter";
import { _axios, getBearerToken, setToken } from "../common/axiosUtils";
import { AxiosError } from "axios";
import { useKeycloak } from "@react-keycloak/web";
import handleError from "../common/errors/UserError";
import { SelectCurrency } from "../common/controls/SelectCurrency";

export function ManagePortfolio(code: string): React.ReactElement {
  const { register, handleSubmit, errors } = useForm();
  const onSubmit = (data: Record<string, Portfolio>) => console.log(data);
  const [portfolio, setPortfolio] = useState<Portfolio>();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();

  useEffect(() => {
    const fetchPortfolio = async (config: {
      headers: { Authorization: string };
    }): Promise<void> => {
      setLoading(true);
      logger.debug(">>fetch apiPortfolio");
      await _axios
        .get<Portfolio>(`/bff/portfolios/code/${code}`, config)
        .then(result => {
          logger.debug("<<fetched apiPortfolio");
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
    fetchPortfolio({
      headers: getBearerToken()
    }).finally(() => setLoading(false));
  }, [code, keycloak]);
  if (loading) {
    return <div id="root">Loading...</div>;
  }
  if (error) {
    return handleError(error, true);
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
                onSubmit={handleSubmit(onSubmit)}
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
                      name="Name"
                      ref={register({ required: true, maxLength: 100 })}
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Reporting Currency</label>
                  <div className="control">
                    <SelectCurrency
                      name={"refCcy"}
                      default={portfolio?.currency.code}
                      key={"currency"}
                    />
                  </div>
                </div>
                <div className="field">
                  <label className="label">Reference Currency</label>
                  <div className="control">
                    <SelectCurrency name={"baseCcy"} default={portfolio?.base.code} key={"base"} />
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
