# What's doable — capability audit (2026-06-08, verified by running)

Tiers by actual reachability. Everything in tier 1 was just executed and passed.

## Tier 1 — DOABLE NOW (verified green this audit)
| capability | command | result |
|---|---|---|
| loopify semantics (property-diff + red/green) | `bb model/loopify_model.clj` | ACCEPT |
| clearing conservation (500 systems) | `bb model/clearing_model.clj` | ACCEPT |
| ℂ² Reeb=Hopf (contact geometry) | `bb model/hopf_c2.clj` | ACCEPT |
| DuckDB HUGEINT ledger audits | `bb model/clearing_ledger.clj` | OK |
| **live jank→DuckDB clearing engine** | `DYLD_INSERT_LIBRARIES=…/libduckdb.dylib jank --include-dir …/include run model/clearing_repl.jank` | Σ(net)=0 ✓ |
| jank cpp-interop → DuckDB | `… run model/duckjank.jank` | 9e9 ✓ |
| typed config + **load-bearing** contract | `nickel export model/{loopify,bad}.ncl` | good=exit0, **bad=exit1** ✓ |
| jank binary (fork + loopify scaffold) | `jank -Oloopify run …` | runs; `-Oloopify` switchable, verified no-op |
| CoqGym language / SCIP query | `gh api …/languages` | answered (`coqgym-scip.md`) |

### Reproducibility note (caught by a re-run sweep, 2026-06-08)
The jank-dependent rows need the built binary. The earlier `nix build … --no-link` builds left **no GC
root** and were garbage-collected (binary vanished; bb/Nickel/DuckDB rows unaffected). Fix: build with a
GC root so it persists —
```
cd vendor/jank && nix build .#jank-release -L --out-link /Users/dietrich/worlds/jc-converge/.jank-result
# then: JANK=.jank-result/bin/jank ; jank artifacts (clearing_repl, transient, duckjank, -Oloopify) re-run.
```

## Tier 2 — DOABLE, ONE DECISION (yours)
- **File F2 (and F1/F3) upstream.** `gh` authed as `bmorphism`, scopes `repo`+`workflow` → can open issues
  on `jank-lang/jank`. Drafts ready in `upstream-issue-draft.md`. Just needs your go.

## Tier 3 — DOABLE, LARGE EFFORT (toolchain / deliberate work)
- **jank rebuild** — works (~15 min, `nix build .#jank-release`); used repeatedly this session.
- **The verified corner** (Rocq lemma → crane-extract a toy `net`): `opam` is installed; crane's build
  files (`dune-project`, `rocq-crane.opam`) are present ⇒ buildable, but needs `opam install` of Rocq 9 +
  OCaml 4.14 + the plugin (heavy, multi-step, like the jank build). This makes the "verified" corner real.
- **The loopify transform body** — scoped in `loopify-transform-plan.md` to the analyze layer; a deliberate
  multi-day compiler feature (in-place AST construction), rebuild-gated. Not a loop-tick task.

## Tier 4 — NOT doable here (honest)
- **fastmath inside jank** — fastmath is a JVM library; jank is not the JVM. Usable in babashka/JVM Clojure,
  or its ℂ²/quaternion ideas reimplemented in jank (cpp-interop) — but not the JVM jar directly.
- **CoqGym itself running** — legacy Coq 8.9 + SerAPI + 2019 Python, not set up; the modern realization of
  its role is an LLM/search prover over **Rocq** feeding crane (the +1-prove leg).

## One line
Everything modeled and the whole live clearing engine run **now**; filing F2 is one yes; the verified
corner and the loopify transform are buildable but deliberate; only the JVM-bound fastmath jar and the
legacy CoqGym stack are out of reach here.
