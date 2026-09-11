-- Periodic snapshots of external macro indicators (currently Kalshi Fed-decision market
-- probabilities) so trend deltas can be computed without re-querying the upstream provider's own
-- history, which the public Kalshi API doesn't expose. One row per (series, metric) sample taken
-- by MacroRefreshSchedule. Mirrors news_article's style (V19) — id via KeyGenUtils, no audit
-- columns beyond observed_at.
--
-- series:  provider-qualified identifier, e.g. "KALSHI:KXFEDDECISION-26SEP".
-- metric:  the observed label within that series, e.g. a Kalshi outcome's yes_sub_title. Sized
--          VARCHAR(255), not VARCHAR(64), so a longer free-text Kalshi label never overflows —
--          RateExpectationsService also defensively truncates to this same length before persist.
-- value:   the sampled value (a 0..1 probability for Kalshi outcomes).
--
-- The index covers (series, metric, observed_at) to match the only query that uses it —
-- findFirstBySeriesAndMetricAndObservedAtLessThanEqualOrderByObservedAtDesc.

CREATE TABLE IF NOT EXISTS macro_observation (
    id           VARCHAR(36)    PRIMARY KEY,
    series       VARCHAR(64)    NOT NULL,
    metric       VARCHAR(255)   NOT NULL,
    value        NUMERIC(12, 6) NOT NULL,
    observed_at  TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_macro_observation_series_metric_observed
    ON macro_observation (series, metric, observed_at);
