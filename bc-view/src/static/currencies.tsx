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
      .get<any>("/bff/currencies", {
        headers: getBearerToken(keycloak)
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

export function currencyOptions(currencies: Currency[]): ReactNode {
  return currencies.map((currency: Currency) => (
    <option key={currency.code} value={currency.code}>
      {currency.code}
    </option>
  ));
}

export const USD = { code: "USD" } as Currency;
