# Counterfactuals to "opam vs dune" (grounded in building crane)

## The naive framing — and its refutation
"opam vs dune" treats them as rivals. **Counterfactual: it's a category error.** They are different
layers:
- **opam** = cross-project **package manager** — fetches/solves deps (OCaml 4.14, Rocq 9.0.0), manages
  *switches* (global-ish environments).
- **dune** = within-project **build system** — compiles this project (crane's plugin + theories via
  `(using coq 0.8)` in `dune-project`), incremental.
crane's README uses **both**: `opam install . --deps-only` (opam fetches) → `dune build` (dune compiles).
Neither substitutes for the other in the canonical flow. So at face value there is no "vs."

## …but here are the counterfactuals where the dichotomy becomes REAL
1. **dune is encroaching on opam.** dune 3.x added package management — `dune pkg lock`, lock files,
   dependency fetching. crane's `(lang dune 3.13)` is new enough to use it. As dune absorbs the
   package-manager role, "opam vs dune" turns from category-error into an actual rivalry.
2. **nix is the true counterfactual to opam** (the package-management layer) — and it's the one that bites
   here. We built **jank** with `nix build .#jank-release` and **zero opam** (the flake provided LLVM 22 +
   clang). crane has *no* flake but the same path is viable: **nixpkgs has `rocq-core_9_0`** (crane's exact
   Rocq 9.0.0) **+ `dune` + ocaml**. ⇒ `flox install rocq-core_9_0 dune ocaml; dune build -p rocq-crane`
   builds crane with **opam bypassed entirely**. dune stays the builder; nix replaces opam.
3. **The opam cost, made concrete (the local counterfactual):** `dune` isn't even installed here, and the
   existing opam switch is **`oxgame-ox` (OCaml 5.2.0+ox)** — wrong for crane (wants 4.14 + Rocq 9).
   The opam path needs a *fresh switch* (heavy, stateful, global, conflicting with oxgame-ox). The nix path
   is hermetic, switch-free, reproducible — no global state touched.
4. **Vendoring** is a third counterfactual to opam: deps in-tree, dune builds, no package manager at all
   (monorepo style). Orthogonal to nix; same "no opam" conclusion by a different route.

## Verdict
"opam vs dune" dissolves — they're layers, not rivals — but the **real** counterfactual is **opam vs nix**
for the *package* layer, with **dune the builder either way**. For building crane specifically, **nix wins**:
`rocq-core_9_0` exists in nixpkgs, it's reproducible, it matches how we built jank, and it sidesteps the
`oxgame-ox` switch conflict. The deeper future counterfactual: `dune pkg` may make dune itself the package
manager, collapsing the two layers into one tool.

## Consequence for the verified corner
Build crane the **nix/flox** way, not opam:
```
flox install rocq-core_9_0 dune ocaml      # nix provides deps (counterfactual to opam install --deps-only)
cd vendor/crane && dune build -p rocq-crane # dune builds the Rocq plugin + theories
```
This makes the verified corner (Rocq conservation lemma → crane-extract) buildable here without the opam
switch mess — gated only on the (env-changing) flox install, your call.
