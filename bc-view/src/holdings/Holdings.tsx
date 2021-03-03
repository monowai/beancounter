import React, { useState } from "react";
import { SubTotal } from "./SubTotal";
import { calculate } from "./calculate";
import { GroupBy, groupOptions } from "../types/groupBy";
import { GroupOption, Holdings, ValuationOption } from "../types/beancounter";
import Total from "./Total";
import StatsHeader, { StatsRow } from "./Stats";
import Switch from "react-switch";
import Select, { ValueType } from "react-select";
import { valuationOptions, ValueIn } from "../types/valueBy";
import { useHoldings } from "./hooks";
import { Rows } from "./Rows";
import { Header } from "./Header";
import { isDone } from "../types/typeUtils";
import PageLoader from "../common/PageLoader";
import { TrnDropZone } from "../portfolio/DropZone";
import { ShowError } from "../errors/ShowError";

export default function ViewHoldings(code: string): JSX.Element {
  const holdingResults = useHoldings(code);
  const [valueIn, setValueIn] = useState<ValuationOption>({
    value: ValueIn.PORTFOLIO,
    label: "Portfolio",
  });
  const [hideEmpty, setHideEmpty] = useState<boolean>(true);
  const [groupBy, setGroupBy] = useState<GroupOption>({
    value: GroupBy.MARKET_CURRENCY,
    label: "Currency",
  });

  // Render where we are in the initialization process
  if (isDone(holdingResults)) {
    if (holdingResults.error) {
      return <ShowError error={holdingResults.error} />;
    }
    if (!holdingResults.data.positions) {
      return (
        <div data-testid="dropzone">
          <label>This portfolio has no transactions. Please drop your CSV file to upload</label>
          <TrnDropZone portfolio={holdingResults.data.portfolio} purgeTrn={false} />
        </div>
      );
    }
    const holdings = calculate(
      holdingResults.data,
      hideEmpty,
      valueIn.value,
      groupBy.value
    ) as Holdings;
    return (
      <div className="page-box">
        <div className="filter-columns">
          <div className="filter-label">Value In</div>
          <div className="filter-column">
            <Select
              options={valuationOptions()}
              defaultValue={valueIn}
              isSearchable={false}
              isClearable={false}
              onChange={(newValue: ValueType<ValuationOption, false>) => {
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
              onChange={(newValue: ValueType<GroupOption, false>) => {
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
            <StatsHeader portfolio={holdingResults.data.portfolio} />
            <StatsRow
              portfolio={holdingResults.data.portfolio}
              moneyValues={holdings.totals}
              valueIn={valueIn.value}
            />
          </table>
        </div>
        <div className={"all-getData"}>
          <table className={"table is-striped is-hoverable"}>
            {Object.keys(holdings.holdingGroups)
              .sort()
              .map((groupKey) => {
                return (
                  <React.Fragment key={groupKey}>
                    <Header groupKey={groupKey} />
                    <Rows
                      portfolio={holdingResults.data.portfolio}
                      holdingGroup={holdings.holdingGroups[groupKey]}
                      valueIn={valueIn.value}
                    />
                    <SubTotal
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
  return <PageLoader message={"Crunching data..."} show={true} />;
}
