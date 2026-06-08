-- ewig-clearing ledger in DuckDB. net stored as HUGEINT (128-bit exact) = the SQL-level
-- F2-safe / exact-arithmetic discipline. The ewig snapshot history becomes a queryable,
-- conservation-audited audit trail (the world.toml jank_duckdb_repl seed, realized).

CREATE TABLE ledger AS
  SELECT * FROM read_csv('/tmp/clearing_ledger.csv', header = true,
                         columns = {'epoch':'INTEGER','party':'INTEGER','net':'HUGEINT'});

.print == 1. conservation audit (necessary): SUM(net) per epoch must be 0 ==
SELECT epoch, SUM(net) AS sigma, (SUM(net) = 0) AS conserved
FROM ledger GROUP BY epoch ORDER BY epoch;

.print == 2. per-position running balance across epochs (ewig time-travel as SQL) ==
SELECT party, epoch, net,
       SUM(net) OVER (PARTITION BY party ORDER BY epoch) AS cumulative
FROM ledger ORDER BY party, epoch;

.print == 3. final net positions after all epochs (who is owed / owes) ==
SELECT party, SUM(net) AS final_position
FROM ledger GROUP BY party HAVING SUM(net) <> 0 ORDER BY final_position DESC;

.print == 4. SUFFICIENT audit: max abs single position (would overflow i32 if > 2.1e9) ==
SELECT MAX(ABS(net)) AS max_abs_net, (MAX(ABS(net)) > 2147483647) AS exceeds_i32
FROM ledger;
