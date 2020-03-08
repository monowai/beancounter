import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import logger from "../common/ConfigLogging";
import { Portfolio } from "../types/beancounter";
import { _axios, getBearerToken, setToken } from "../common/axiosUtils";
import { AxiosError } from "axios";
import { useKeycloak } from "@react-keycloak/web";
import handleError from "../common/errors/UserError";

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
      <form onSubmit={handleSubmit(onSubmit)}>
        <input
          type="text"
          placeholder="code"
          defaultValue={portfolio?.code}
          name="Unique code"
          ref={register({ required: true, maxLength: 10 })}
        />
        <input
          className="input"
          type="text"
          placeholder="name"
          defaultValue={portfolio.name}
          name="Name"
          ref={register({ required: true, maxLength: 100 })}
        />
        <select placeholder={"Currency"} name="Currency" ref={register({ required: true })}>
          <option value="USD">USD</option>
          <option value="EUR">EUR</option>
          <option value="SGD">SGD</option>
          <option value="NZD">NZD</option>
        </select>

        <select placeholder={"Currency"} name="BaseCurrency" ref={register({ required: true })}>
          <option value="USD">USD</option>
          <option value="EUR">EUR</option>
          <option value="SGD">SGD</option>
          <option value="NZD">NZD</option>
        </select>

        <button className="button" type="submit" value="Submit input" />
      </form>
    );
  }
  return <div id="root">Portfolio not found!</div>;
}
