-- TWR rules changed: PRIVATE-market assets are excluded unless opted in, and BALANCE
-- contributions are external flows. Every cached snapshot was computed under the old
-- rules, so drop them all; svc-position rebuilds each portfolio's series on next request.
DELETE FROM performance_snapshot;
