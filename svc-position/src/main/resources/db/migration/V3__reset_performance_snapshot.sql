-- Accumulation rules changed: a BALANCE now replaces the quantity earlier trns built up, and
-- splits compound. Cached snapshots were replayed under the old rules, so drop them all;
-- svc-position rebuilds each portfolio's series on next request.
DELETE FROM performance_snapshot;
