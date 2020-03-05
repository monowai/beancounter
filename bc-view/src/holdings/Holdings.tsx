import { HoldingFooter, HoldingHeader, HoldingRows } from "./Group";
import React, { useEffect, useState } from "react";
import "../App.css";
import { calculate } from "./calculate";
import { GroupBy, groupOptions } from "../types/groupBy";
import { GroupOption, HoldingContract, Holdings, ValuationOption } from "../types/beancounter";
import Total from "./Total";
import StatsHeader, { StatsRow } from "../portfolio/Stats";
import Switch from "react-switch";
import Select, { ValueType } from "react-select";
import { valuationOptions, ValueIn } from "../types/valueBy";
import { AxiosError } from "axios";
import logger from "../common/ConfigLogging";
import { useKeycloak } from "@react-keycloak/web";
import handleError from "../common/errors/UserError";
import { _axios, getBearerToken, setToken } from "../common/axiosUtils";

export default function ViewHoldings(code: string): React.ReactElement {
  const [valueIn, setValueIn] = useState<ValuationOption>({
    value: ValueIn.PORTFOLIO,
    label: "Portfolio"
  });
  const [hideEmpty, setHideEmpty] = useState<boolean>(true);
  const [groupBy, setGroupBy] = useState<GroupOption>({
    value: GroupBy.MARKET_CURRENCY,
    label: "Currency"
  });
  const [holdingContract, setHoldingContract] = useState<HoldingContract>();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    const fetchHoldings = async (config: { headers: { Authorization: string } }): Promise<void> => {
      setLoading(true);
      logger.debug(">>fetch %s", code);
      await _axios
        .get<HoldingContract>(`/bff/${code}/today`, config)
        .then(result => {
          logger.debug("<<fetch %s", code);
          setHoldingContract(result.data);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };
    setToken(keycloak);
    fetchHoldings({
      headers: getBearerToken()
    }).finally(() => setLoading(false));
  }, [keycloak, code]);
  // Render where we are in the initialization process
  if (loading) {
    return <div id="root">Loading...</div>;
  }
  if (error) {
    return handleError(error, true);
  }
  if (holdingContract) {
    const holdings = calculate(
      holdingContract,
      hideEmpty,
      valueIn.value,
      groupBy.value
    ) as Holdings;
    return (
      <div className="page-box">
        <div className="filter-columns">
          <div className="filter-label" />
          <div className="filter-label" />
          <div className="filter-label">Value In</div>
          <div className="filter-column">
            <Select
              options={valuationOptions()}
              defaultValue={valueIn}
              isSearchable={false}
              isClearable={false}
              onChange={(newValue: ValueType<ValuationOption>) => {
                if (newValue) {
                  setValueIn(newValue as ValuationOption);
                }
              }}
            />
          </div>
          <div className="filter-label">Group By</div>
          <div className="filter-column">
            <Select
              options={groupOptions()}
              defaultValue={groupBy}
              isSearchable={false}
              isClearable={false}
              onChange={(newValue: ValueType<GroupOption>) => {
                if (newValue) {
                  setGroupBy(newValue as GroupOption);
                }
              }}
            />
          </div>
          <div className="filter-label">Open Only</div>
          <div className="filter-column">
            <Switch className="react-switch" onChange={setHideEmpty} checked={hideEmpty} required />
          </div>
        </div>
        <div className={"stats-container"}>
          <table>
            <StatsHeader portfolio={holdingContract.portfolio} />
            <StatsRow
              portfolio={holdingContract.portfolio}
              moneyValues={holdings.totals}
              valueIn={valueIn.value}
            />
          </table>
        </div>
        <div className={"all-apiHoldings"}>
          <table className={"table is-striped is-hoverable"}>
            {Object.keys(holdings.holdingGroups)
              .sort()
              .map(groupKey => {
                return (
                  <React.Fragment key={groupKey}>
                    <HoldingHeader groupKey={groupKey} />
                    <HoldingRows
                      holdingGroup={holdings.holdingGroups[groupKey]}
                      valueIn={valueIn.value}
                    />
                    <HoldingFooter
                      holdingGroup={holdings.holdingGroups[groupKey]}
                      valueIn={valueIn.value}
                    />
                  </React.Fragment>
                );
              })}
            <Total holdings={holdings} valueIn={valueIn.value} />
          </table>
        </div>
      </div>
    );
  }
  return <div id="root">No Holdings to display</div>;
}
