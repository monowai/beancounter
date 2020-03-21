import { Currency } from "../types/beancounter";
import React, { ReactNode, useEffect, useState } from "react";
import logger from "../common/ConfigLogging";
import { _axios, getBearerToken } from "../common/axiosUtils";
import { useKeycloak } from "@react-keycloak/razzle";

export function useCurrencies(): Currency[] {
  const [currencies, setCurrencies] = useState<Currency[]>([]);
  const [keycloak] = useKeycloak();
  useEffect(() => {
    logger.debug(">>fetch getCurrencies");
    _axios
      .get<Currency[]>("/bff/currencies", {
        headers: getBearerToken(keycloak.token)
      })
      .then(result => {
        logger.debug("<<fetched Currencies");
        setCurrencies(result.data);
      })
      .catch(err => {
        console.error("Unable to get currencies {}", err);
      });
  }, [keycloak]);
  return currencies;
}

export function get(currencies: Currency[], value: string): Currency[] | undefined {
  return currencies.filter(currency => currency.code === value);
}
export function currencyOptions(currencies: Currency[], selectedValue: string): ReactNode {
  return currencies.map((currency: Currency) => (
    <option key={currency.code} value={currency.code} selected={currency.code === selectedValue}>
      {currency.code}
    </option>
  ));
}

export const USD = { code: "USD" } as Currency;
