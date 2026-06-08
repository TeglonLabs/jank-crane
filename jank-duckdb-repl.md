# jank + DuckDB REPL — feasibility proven (world.toml `jank_duckdb_repl`)

A live clearing REPL: jank drives an exact (HUGEINT) DuckDB ledger via cpp-interop, audited per-position.
Both halves are proven on the real jank binary; only the glue (install `libduckdb`) remains.

## Proven (on `nix .#jank-release`)
1. **cpp-interop works** — `model/`-style demo on the binary:
   ```clojure
   (cpp/raw "namespace demo { long add1(long x){ return x + 1; } }")
   (cpp/demo.add1 (cpp/long 41))   ;=> 42
   ```
   jank JIT-compiles and calls arbitrary C++ at runtime (CppInterOp). Syntax: `(cpp/raw "…")` injects;
   `(cpp/ns.fn (cpp/T v))` calls; `(cpp/cast cpp/std.string "…")` converts.
2. **DuckDB exact ledger works** — `model/clearing_ledger.{clj,sql}`: `net` as `HUGEINT` (128-bit exact);
   conservation `SUM(net)=0` per epoch; ewig time-travel via window functions; the SUFFICIENT per-position
   audit `MAX(ABS(net)) > 2^31-1` (catches what global Σ misses; F2-safe by column type).

## The bridge (remaining glue)
- `flox install duckdb` provides `libduckdb` + `duckdb.h` (flox has the package; only the CLI is on PATH now).
- Then from jank:
  ```clojure
  (cpp/raw "#include <duckdb.h>")
  ;; duckdb_open / duckdb_connect / duckdb_query / duckdb_value_int64 via cpp/ …
  ```
  pointing jank at the flox duckdb: `jank --include-dir <duckdb/include> --library-dir <duckdb/lib> …`.
- Wrap in a small jank ns `clearing.ledger` exposing `open!`, `append-epoch!`, `audit`, `time-travel` —
  the clearing round (transient batch → net) writes each epoch; audits run in SQL.

## The whole live loop (ties the threads)
```
−/?/+ Emacs tiles (steel-geiser-emacs.md)         ; interaction surface
   └─ jank nREPL  ──cpp-interop──▶  DuckDB (HUGEINT)   ; exact, queryable, append-only ledger
        clearing round = transient batch → net → append-epoch! → SQL audit (Σ + per-position)
```
This closes the ewig-clearing engine onto a real persistent store, driven live from a Lisp REPL, with
exact arithmetic enforced at the column type — and per-position auditing that a naive Σ check would miss.

## ★ WORKING end-to-end (2026-06-08)
jank drives DuckDB live. `model/duckjank.jank` (one `(cpp/raw "#include <duckdb.h> …")` wrapper) →
opens in-memory DuckDB, builds a `HUGEINT` ledger, runs the per-position audit, returns to jank:
```
jank -> DuckDB MAX(ABS(net)) = 9000000000
exact (== 9000000000, exceeds i32)?  true
```
**The working recipe** (the non-obvious part — the JIT needs the lib's symbols *loaded*, not just on -L):
```
DYLD_INSERT_LIBRARIES=$FLOX/lib/libduckdb.dylib \
  jank --include-dir $FLOX/include run model/duckjank.jank
#   $FLOX = /Users/dietrich/worlds/.flox/run/aarch64-darwin.worlds.dev
```
- `flox install duckdb` provides `$FLOX/include/duckdb.h` + `$FLOX/lib/libduckdb.dylib` (done).
- `--include-dir` lets `#include <duckdb.h>` resolve at JIT-compile.
- `DYLD_INSERT_LIBRARIES` force-loads the dylib so the ORC JIT resolves `duckdb_open` etc.
  (`-L`/`--library-dir` and `#pragma cling load` did NOT suffice — symbols-not-found; DYLD_INSERT does.)

## Status — DONE
Both halves AND the bridge work on the real binary. Next (optional polish): wrap as a jank ns
`clearing.ledger` (`open!`/`append-epoch!`/`audit`/`time-travel`) and drive it from the nREPL / the −/?/+
tiles for a live, exact, per-position-audited clearing REPL.
