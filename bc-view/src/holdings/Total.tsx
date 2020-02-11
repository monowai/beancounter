import {Holdings} from '../types/beancounter';
import React from 'react';
import {FormatMoneyValue} from '../common/MoneyUtils';
import {ValueIn} from '../types/valueBy';

export default function Total(props: {holdings: Holdings; valueIn: ValueIn}): JSX.Element {
  // Transform the contract into the view the user requested
  const holdings = props.holdings;
  const valueIn = props.valueIn;
  return (
    <tbody className={'totals-row'} key={holdings.portfolio.code + 'totals'}>
      <tr key={valueIn}>
        <td colSpan={4} align={'right'}>
          Totals in {valueIn} currency
        </td>
        <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={'costValue'} />
        <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={'marketValue'} />
        <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={'dividends'} />
        <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={'realisedGain'} />
        <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={'unrealisedGain'} />
        <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={'totalGain'} />
      </tr>
    </tbody>
  );
}
