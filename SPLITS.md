# Stock Splits in svc-data

How splits are stored, adjusted, and surfaced to clients. Read before
adding a new market-data provider so the integration lands cleanly.

## Concepts

| Term            | Meaning                                                                                                  |
|-----------------|----------------------------------------------------------------------------------------------------------|
| **Ex-date**     | First trading day on the post-split share basis. Pre-ex prices are quoted on the old basis.              |
| **Split factor**| Ratio of new shares to old. A 4-for-1 split has factor `4`. A reverse 1-for-5 has factor `0.2`.          |
| **Raw price**   | Quote in the basis that was current on the trading day (so pre-ex rows are in old-basis dollars/shares). |
| **Adjusted**    | Quote rebased onto today's share basis (pre-ex closes divided by every event factor that came after).    |
| **Stamped row** | A `MarketData` row carrying `split != 1` — used by the chart and adjuster to mark an ex-date.            |

## Invariants

1. **Database stores raw OHLC.** Pre-ex `close`, `open`, `high`, `low` are
   on the old basis. The adjuster scales them on the way out — never
   re-write history into the DB.
2. **`MarketData.split` is the ex-date marker.** Exactly one row per
   split event carries the factor (the ex-date row). Sticky stamps on
   neighbouring rows are a provider bug; the adjuster collapses them
   but enrichers must not introduce them.
3. **`previousClose` on the ex-date row is rebased.** When the daily
   refresh persists a Global Quote on the ex-date,
   `enrichWithPreviousClose` divides the prior day's close by the
   factor so the row's `change` / `changePercent` reflect the
   post-split move. Backfilled rows don't go through this enricher and
   typically have `previousClose = 0`.
4. **`PriceService.getPriceHistory` is the only adjustment seam.**
   Every callsite that returns a chronological price series (chart,
   future analytics) must run the rows through `SplitAdjuster.adjust`.
   Single-row lookups (`getMarketData(date)`) leave the raw row alone
   because they have no series context.

## Data flow

```
Provider ─► PriceAdapter ─► (refresh path: enrichWithPreviousClose,
                              CorporateEventEnricher)
                          ─► MarketDataRepo.save (raw OHLC + split)
                          ◄─ MarketDataRepo.findPriceHistory
                          ─► SplitAdjuster.adjust(prices, events)
                          ─► PriceHistoryResponse to bc-view
```

`SplitAdjuster` accepts:
- The raw `List<PricePoint>` from the repo.
- An optional `List<SplitEvent>` from a provider's corporate-event
  service (Alpha Vantage today). This covers the case where a backfill
  endpoint did not carry split coefficients, so the DB has nothing but
  raw closes for the days leading up to a known ex-date.

It returns a list where:
- Pre-event OHLC and `previousClose` are divided by the cumulative
  factor of every event whose ex-date follows the row.
- The ex-date row keeps its `close` as-stored (already on the new
  basis) but its `previousClose` is divided by `canonicalSplit` **only
  if it sits at roughly raw pre-split magnitude** (≥ ½ × close ×
  canonicalSplit). Already-rebased values are left alone — see
  `resolvePreviousCloseFactor`.
- The `split` column is normalised: only the canonical ex-date keeps
  the factor; sticky neighbours go back to `1`.

## Adding a new provider

When wiring a new market-data provider, decide each of the four points
below and document the answer in the adapter's KDoc.

### 1. Where does the split coefficient come from?

| Provider channel                              | Carries split?                                |
|-----------------------------------------------|-----------------------------------------------|
| Daily quote / latest bar                      | Often **no** — must be enriched from events.  |
| Historical bars (raw)                         | Usually **no**.                               |
| Historical bars (adjusted endpoint)           | Often **yes** — but adjusts close already.    |
| Dedicated corporate-actions endpoint          | Yes, with ex-date and factor.                 |

Pick whichever channel gives a clean ex-date + factor pair. Capture
the factor on the ex-date row only; never copy it forward to
neighbouring days.

### 2. Match on the exact ex-date

`AlphaCorporateEventEnricher` once used a ±1 day fallback for both
splits and dividends. The fallback is fine for dividends (pay-date /
ex-date conventions vary by provider) but **lethal for splits**: it
created two rows with the same factor and the chart double-divided
history (this is what the VO 4:1 incident on 2026-04-21 surfaced).

Rule: split lookups must compare `priceDate.isEqual(...)`. Dividend
lookups may use the ±1-day fallback as long as they filter to
dividend-bearing rows first (so a same-day split row doesn't shadow a
neighbouring dividend).

### 3. Decide whether the historical close is raw or adjusted

If the provider returns historical closes that are **already
back-adjusted** for splits (e.g. Alpha's `TIME_SERIES_DAILY_ADJUSTED`
"5. adjusted close"), do **not** persist that value as `close`. Either
take the raw close (`"4. close"` field) or transform back, otherwise
`SplitAdjuster` will divide adjusted history a second time and prices
collapse to a tiny fraction.

### 4. Wire the event source into `PriceService`

`PriceService.collectSplitEvents(asset, from, to)` is the seam. Today
it pulls from `AlphaEventService` for Alpha-supported markets and
returns empty for others. When introducing a new provider:

1. Expose a method on the provider's event service that returns
   `List<SplitEvent>` (date + factor) inside an arbitrary date range.
2. Inject the new service into `PriceService` (alongside
   `alphaEventService`) and union the results.
3. Make the call **failure-tolerant**: a provider outage must not
   break price-history requests. The existing path catches and logs,
   then falls back to whatever the price rows themselves carry.
4. Cache aggressively — these endpoints are usually rate-limited and
   the data rarely changes. Alpha's service uses
   `@Cacheable("alpha.asset.event")`; mirror that pattern.

## Test checklist for a new provider

Add tests covering:

- Single ex-date inside the window — pre-event closes divided, ex-date
  row untouched.
- No event in window — adjuster is a no-op.
- Backfill-only data (`split = 1` on every row) plus an external
  `SplitEvent` — pre-event rows still divide.
- Two events in window — factors compound on the earliest rows.
- Sticky stamps across multiple consecutive rows — collapsed to one
  ex-date.
- `previousClose` shapes:
  - raw pre-split (≥ ½ × close × factor) — divided.
  - already rebased (close to `close`) — left alone.

`SplitAdjusterTest` and `AlphaCorporateEventEnricherTest` are the
working references.

## When NOT to adjust

- Single-row lookups (`getMarketData(asset, date)`) return the raw row.
  Callers (e.g. position valuation on a specific date) want the price
  as it was, not as it would be quoted today.
- Trade transactions are stored on the basis that was current at the
  time of trade — splitting an existing trade is a separate corporate
  action handled in `bc-event`, not in price storage.

## Glossary

- **`PricePoint`** — DTO returned by `getPriceHistory`. Slim variant of
  `MarketData` without the repeated asset reference.
- **`SplitAdjuster.SplitEvent(date, factor)`** — input contract for the
  adjuster's external-event channel.
- **`canonicalSplit`** — the factor recorded on the ex-date row after
  normalisation.

## Reference incident

- VO (Vanguard Mid-Cap ETF) 4-for-1 split on **2026-04-21**.
- Alpha enricher's ±1-day fallback also stamped `split=4` on
  2026-04-22, so the DB had two ex-date rows for one event.
- Pre-fix: chart divided pre-split history by 16 (= 4 × 4) and showed
  a `-72.87 %` change against a raw cliff drop.
- Fixes: enricher matches splits on exact date only (PR #800);
  `SplitAdjuster` collapses sticky stamps and surfaces a single
  canonical marker; chart renders the response verbatim (bc-view #619).
