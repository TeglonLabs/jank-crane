# CoqGym's place — the proof-acquisition layer upstream of crane (world.toml `coqgym_place`)

## What CoqGym is
Yang & Deng, ICML 2019, *"Learning to Prove Theorems via Interacting with Proof Assistants."*
- A **dataset**: ~71K human-written proofs from **123 Coq projects** (real GitHub repos), with full
  proof-state + tactic ASTs, served via **SerAPI**.
- An **environment**: step a Coq proof interactively; a model (ASTactic) emits the next tactic as an AST.
- I.e. CoqGym is **ML-driven proof *search/acquisition*** over real Coq developments.

## The place: it answers "where do crane's proofs come from?"
crane extracts a **kernel-checked Gallina term → memory-safe C++**. It needs a *proof to extract*. CoqGym
(or a modern successor) is the layer that **produces** that proof. The verified-code pipeline, GF(3)-typed:

```
 +1 Play      CoqGym / proof search      generate a Gallina proof of a spec
  0 Witness   Coq/Rocq kernel            CHECK the term (the trusted core; reject if not certified)
 −1 Coplay    crane                       extract the checked term -> C++ (the validated, fast artifact)
              ───────────────────────────────────────────────────────────────────────────────────
 application  jank (immer/transients) + DuckDB(HUGEINT) ledger + nREPL    run it, fast & live
```
Σ ≡ 0: prove (+1) · kernel-check (0) · extract (−1). CoqGym deepens the cube's **proof = source** corner
(`roots/crane-vs-dafny.md`): not just "the source is a theorem," but *how you obtain that theorem* — search
over a corpus. Provenance sub-axis: hand-written | CoqGym-searched | LLM-generated — all kernel-checked,
all crane-extractable. (TeglonLabs siblings: **PutnamBench** = the *eval* of proof search; **narya** = a
proof assistant — the same +1-generate-verified-code leg.)

## Why it completes THIS session's stack (the clearing engine, verified end to end)
The clearing engine's core law is **conservation: `Σ net ≡ 0` within a closed clearing group** — exactly a
theorem. The full verified+fast+live realization:
1. **State** the netting + `Σ net ≡ 0` in Coq/Rocq (rung5's multilateral net, as Gallina).
2. **Prove** it — CoqGym-style search (today: an LLM prover over Rocq, the generate/verify discipline this
   very session practiced).
3. **Kernel-check** — the Coq kernel certifies the proof (0/witness; no trust in the prover).
4. **Extract** — crane lowers the verified `net : Book → Book` to memory-safe C++.
5. **Run** — jank wraps it with immer transients (`clearing_repl.jank`), exact `HUGEINT` DuckDB ledger,
   live nREPL + −/?/+ tiles, per-position audited.
⇒ a clearing engine whose conservation is **machine-proved**, **extracted**, **fast**, and **live** — the
union of every thread in this repo. CoqGym's place is step 2: it is *how the conservation proof is found*.

## Honest status
- CoqGym itself is legacy infra (Coq 8.9, SerAPI, 2019 Python) — not runnable here; this is the
  architectural placement, not a run. The *modern* realization of "step 2" is an LLM/search prover over
  **Rocq** feeding crane — which is the same +1-generate / 0-verify / −1-extract loop the session ran on
  jank, lifted to proofs. PutnamBench is the yardstick for that prover.
- Concrete next (if pursued): write the rung5 conservation lemma in Rocq; crane-extract a toy `net`;
  link it into `clearing_repl.jank`. That makes the "verified" corner real rather than placed.
