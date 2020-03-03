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
import { axiosBff } from "../common/utils";
import { AxiosError } from "axios";
import { useKeycloak } from "@react-keycloak/web";
import logger from "../common/ConfigLogging";
import { getBearerToken } from "../keycloak/utils";
import { LoginRedirect } from "../common/auth/Login";

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
  const [data, setData] = useState<HoldingContract>();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();
  useEffect(() => {
    const fetchHoldings = async (config: { headers: { Authorization: string } }): Promise<void> => {
      setLoading(true);
      logger.debug(">>fetch %s %s", code, JSON.stringify(config));
      await axiosBff()
        .get<HoldingContract>(`/bff/${code}/today`, config)
        .then(result => {
          logger.debug("<<fetch %s", code);
          setData(result.data);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("bff error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };
    fetchHoldings({
      headers: getBearerToken(keycloak)
    }).finally(() => setLoading(false));
  }, [keycloak, code]);
  // Render where we are in the initialization process
  if (loading) {
    return <div id="root">Loading...</div>;
  }
  if (error) {
    if (error.response) {
      const { data: errData, status } = error.response;
      if (status === 401) {
        return <LoginRedirect />;
      }
      logger.error("Error: %s", errData.message);
      return <div>{errData.message}</div>;
    }
    return <div>Unknown error</div>;
  }
  if (data) {
    const holdings = calculate(data, hideEmpty, valueIn.value, groupBy.value) as Holdings;
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
            <StatsHeader portfolio={data.portfolio} />
            <StatsRow
              portfolio={data.portfolio}
              moneyValues={holdings.totals}
              valueIn={valueIn.value}
            />
          </table>
        </div>
        <div className={"all-holdings"}>
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
