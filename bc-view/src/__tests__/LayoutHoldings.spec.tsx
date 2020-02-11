import React from 'react';
import {render, waitForElement} from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';
import ViewHoldings from '../holdings';
import nock from 'nock';
import {runtimeConfig} from '../config';

nock(runtimeConfig().bcService)
  .get('/test/today')
  .replyWithFile(200, __dirname + '/contracts/test-holdings.json', {
    'Access-Control-Allow-Origin': '*',
    'Content-type': 'application/json'
  })
  .get('/zero/today')
  .replyWithFile(200, __dirname + '/contracts/zero-holdings.json', {
    'Access-Control-Allow-Origin': '*',
    'Content-type': 'application/json'
  })
  .log(console.log);

describe('<ViewHoldings />', () => {
  it('matches snapshot when holdings present', async () => {
    const TestHoldings = (): JSX.Element => {
      return ViewHoldings('test');
    };
    const {getByText, container} = render(<TestHoldings />);
    await waitForElement(() => getByText('USD'));
    expect(nock.isDone());
    expect(container).toMatchSnapshot();
  });

  it('matches snapshot for zero holdings', async () => {
    const ZeroHoldings = (): JSX.Element => {
      return ViewHoldings('zero');
    };
    const {getByText, container} = render(<ZeroHoldings />);
    await waitForElement(() => getByText('Value In'));
    expect(nock.isDone());
    expect(container).toMatchSnapshot();
  });
});
