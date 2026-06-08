# jank-crane — a convergence study, made runnable

Two compilers, read as one. **jank** (Clojure-on-LLVM, immer, GC) and **bloomberg/crane** (Rocq→C++
verified extraction, `rc.h`, COW `persistent_array`) are the same `source → IR → C++-text → Clang →
native` pipeline at two points of a `⟨reclaim × boxing × proof⟩` parameter space. This repo studies that
convergence, then *cashes it out* in running code: an IR pass, a clearing engine, and three real bugs
found by building and exercising jank.

GF(3) spine: **+1 jank (Play)** · **−1 crane (Coplay)** · **0 witness (this hub)**, Σ ≡ 0.
`vendor/jank` and `vendor/crane` are submodules → the two TeglonLabs forks.

## Read in this order

**1 · The convergence (the map)**
- [`roots/q.md`](roots/q.md) — the witness synthesis: the shared spine, the converged-IR cube, the GF(3) audit.
- [`roots/plus.md`](roots/plus.md) (jank-first) · [`roots/minus.md`](roots/minus.md) (crane-first) · [`roots/crane-vs-dafny.md`](roots/crane-vs-dafny.md).
- [`borrow-ledger.md`](borrow-ledger.md) — what each side can borrow (bigint, transients, immer, CppInterOp-REPL…).

**2 · loopify (the IR pass)** — crane's `loopify.ml` (non-tail recursion → iteration) ported to jank-IR.
- [`loopify-spec.md`](loopify-spec.md) — spec + non-cascade proof (5 lemmas; default-off, closed opcode set).
- [`model/loopify_model.clj`](model/loopify_model.clj) — **runnable** operational model: property-diff `interp == loopify`
  over 300 reproducible cases + observed red/green. `bb model/loopify_model.clj` → ACCEPT.
- Scaffold lives in `vendor/jank` (branch `loopify-pass`): `ir/opt/loopify.{hpp,cpp}`, default-off behind
  `--loopify`. **Compiles into real jank** (built via the flake). Transform body still a skeleton.

**3 · Rigor (Tweag + simonw, made load-bearing)**
- [`model/loopify.ncl`](model/loopify.ncl) — typed config with a contract that *rejects* bad input ([`model/bad.ncl`](model/bad.ncl) → exit 1).
- [`.github/workflows/loopify-model.yml`](.github/workflows/loopify-model.yml) — hermetic Nix CI runs all of it on every push.
- [`simonw-workflow.md`](simonw-workflow.md) — the 13 patterns applied + self-graded (red must be *observed*).
- [`ASSESSMENT.md`](ASSESSMENT.md) — each practice → artifact → reproduce → pre-rebutted objection.

**4 · Real-jank findings (built it, ran it, found bugs)** — see [`jank-findings.md`](jank-findings.md), drafts in [`upstream-issue-draft.md`](upstream-issue-draft.md).
- **F2 ★** — `small_integer` (i32) arithmetic silently wraps, no promotion: `(* 1000000 1000000)` → `-727379968`.
  Counterfactual-audited (jank's own `promoting_mul` proves intent) ⇒ a real, unreported, severe bug. **File it.**
- **F1** — non-tail recursion SIGSEGVs ~500–800 deep. Counterfactual *holds*: expected native behavior, **not a bug**;
  loopify reframed as an enhancement (#98).
- **F3** — lexer chokes on valid UTF-8 in a comment (`e2 80 94`). Real, minor.
- Verdict after the −1 pass: **one strong bug (F2) + one minor (F3) + one enhancement (loopify)**, not "three bugs".

**5 · ewig-clearing (the application)** — transients as the fast-clearing primitive ([`ewig-clearing.md`](ewig-clearing.md)).
- [`model/clearing_model.clj`](model/clearing_model.clj) — **runnable**: transient-batch netting, WCC clearing groups,
  Σ-net≡0 conservation over 500 systems, multi-epoch ledger. `bb` → ACCEPT.
- [`model/clearing_real.jank`](model/clearing_real.jank) — **validated on the real jank binary**: bigint settlements correct.
- ★ Insight: **Σ=0 is necessary but not sufficient** — symmetric i32 wrap keeps the books balanced (sum=0) while
  corrupting a per-position settlement by $4.3B. H⁰ clean / H¹ corrupt ⇒ audit per-position, not just the sum.
  ⇒ F2 is the gating dependency; bigint/Ratio is the mitigation (works on jank today).

**6 · The live loop (interaction surface)**
- [`steel-geiser-emacs.md`](steel-geiser-emacs.md) — the −/?/+ REPL tiles (jank nREPL / Steel geiser / Gay.jl witness), Emacs-resident.
- [`gay-3stream-color.md`](gay-3stream-color.md) — drand(continue) ⊕ photon(fork) ⊕ open: the 3-stream color game.

## Reproduce
```
bb model/loopify_model.clj      # IR pass semantics: property-diff + red/green → ACCEPT
bb model/clearing_model.clj     # clearing conservation over 500 systems → ACCEPT
nickel export --format json model/loopify.ncl   # typed config; model/bad.ncl fails the contract
nix build .#jank-release        # (in vendor/jank) builds jank incl. the loopify scaffold
```

## Open threads (`world.toml [comparisons]`)
- `crane_dafny` — verification/backend approaches (drafted in roots/crane-vs-dafny.md).
- `coqgym_place` — Princeton CoqGym as a proof-search/data layer upstream of crane.
- `jank_duckdb_repl` — jank REPL outer loop over DuckDB primitives/IR.

## Status, honest
Done & green: the convergence map, the two operational models, the rigor stack, the real-jank build + bug
hunt (counterfactual-audited), the ewig-clearing validation. Pending: the loopify transform body (skeleton);
the `--loopify` CLI registration; the immer→crane seam; filing F2 (user-gated, `bmorphism`).
