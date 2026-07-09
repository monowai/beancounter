-- Sync auto-settled cash-leg status to the parent trade.
--
-- Auto-settle previously hardcoded the WITHDRAWAL+DEPOSIT legs to SETTLED
-- regardless of the parent's status, and the patch/edit path could flip a
-- parent to PROPOSED while leaving its legs SETTLED (orphaned). Result: an
-- unsettled trade with a settled cash impact.
--
-- Legs now mirror the parent status. Backfill existing rows: for every BC-AUTO
-- leg whose parent (matched by batch = parent.caller_id) has a different status,
-- set the leg's status to the parent's.
UPDATE trn AS leg
SET status = parent.status
FROM trn AS parent
WHERE leg.provider = 'BC-AUTO'
  AND parent.provider <> 'BC-AUTO'
  AND leg.batch = parent.caller_id
  AND leg.status <> parent.status;
