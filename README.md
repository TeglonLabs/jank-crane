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
  `-Oloopify`. **Compiles into real jank** and the flag is **verified switchable** (`-Oloopify` accepted,
  `-Obogus` rejected) and a **verified no-op** (deep recursion segfaults identically with/without it —
  spec-L3 identity holds on the binary). Transform body still a skeleton.

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
- **F4** — string `count`/index is UTF-8 **bytes**, not chars (`(count "café")`→5 vs Clojure 4). Same root as
  F3 (byte-oriented string model) ⇒ file together as one "Unicode/codepoint semantics" issue.
- Verdict after the −1 pass: **F2 (severe) + F3/F4 (Unicode, one issue) + loopify-as-enhancement**, not "four bugs".

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

**7 · Deepenings & audits (each grounded by running, or by real code)**
- [`crane-extraction-concrete.md`](crane-extraction-concrete.md) — ★ crane golden file: extraction is **"loopify everywhere"**
  (iterative `~dtor`/`clone`/`loopify`), forced by RC; the ⟨reclaim⟩ axis predicts F1. Unifies loopify + F1 + rc/gc.
- [`symbolic-c2.md`](symbolic-c2.md) — CoqGym-AST ↔ jank-IR (homoiconic IR = the Bitter Lesson the TF/JAX family re-derives as
  MLIR); fastmath; **ℂ² keystone** — `S³⊂ℂ²` contact manifold, Reeb=Hopf ([`model/hopf_c2.clj`](model/hopf_c2.clj) → ACCEPT).
- [`coqgym-place.md`](coqgym-place.md) + [`coqgym-scip.md`](coqgym-scip.md) — proof-acquisition layer; **95.8% SCIP-dark** (Coq/OCaml), self-indexed via SerAPI.
- [`opam-dune-counterfactual.md`](opam-dune-counterfactual.md) — opam-vs-dune is a false rivalry; the real one is **opam-vs-nix** (build crane via `rocq-core_9_0`).
- [`crane-users.md`](crane-users.md) — all users of crane (joom's verified games; CharlesCNorton; forks incl. ours + `plurigrid/u-crane`).
- [`DOABLE.md`](DOABLE.md) — capability audit, verified by running: doable-now / one-yes / large-effort / blocked.
- jank transients validated on the binary (`model/transient.jank`: `transient == persistent` = true).

## Reproduce
```
bb model/loopify_model.clj      # IR pass semantics: property-diff + red/green → ACCEPT
bb model/clearing_model.clj     # clearing conservation over 500 systems → ACCEPT
bb model/hopf_c2.clj            # C^2 / S^3 contact manifold, Reeb=Hopf → ACCEPT
bb model/clearing_model.clj     # clearing conservation over 500 systems → ACCEPT
nickel export --format json model/loopify.ncl   # typed config; model/bad.ncl fails the contract
nix build .#jank-release        # (in vendor/jank) builds jank incl. the loopify scaffold
```

## `world.toml [comparisons]` threads — all addressed
- `crane_dafny` — ✅ drafted ([`roots/crane-vs-dafny.md`](roots/crane-vs-dafny.md)): the ⟨reclaim×boxing×proof⟩ cube.
- `coqgym_place` — ✅ placed ([`coqgym-place.md`](coqgym-place.md)): proof-acquisition layer upstream of crane
  (+1 prove / 0 kernel-check / −1 extract); how the clearing conservation proof would be *found*.
- `jank_duckdb_repl` — ✅ **WORKING** ([`jank-duckdb-repl.md`](jank-duckdb-repl.md), [`model/clearing_repl.jank`](model/clearing_repl.jank)):
  jank → cpp-interop → exact `HUGEINT` DuckDB ledger, conservation + per-position audit in SQL, live on the binary.

## Status, honest
Done & green: the convergence map; three operational models (loopify/clearing/ℂ²); the rigor stack; the
real-jank build + bug hunt (counterfactual-audited, F1–F4); the **complete loopify scaffold** (`-Oloopify`
verified switchable + no-op on the binary); the **live jank→DuckDB clearing engine**; transients validated;
all three `world.toml` threads; crane's user base.
Pending — all bounded: the loopify transform *body* ([`loopify-transform-plan.md`](loopify-transform-plan.md), analyze-layer, a deliberate
effort); the verified corner (`flox install rocq-core_9_0` → `dune build`); filing F2/F3/F4 (user-gated, `bmorphism`).
