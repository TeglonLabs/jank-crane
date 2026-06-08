# world://CoqGym — the SCIP breakdown of languages

Source: `gh api repos/princeton-vl/CoqGym/languages` (417★, 158.3 MB).

## Language breakdown (by bytes)
| language | % | role |
|---|---|---|
| Coq | 52.0% | the 123 `coq_projects` + proof corpus (the knowledge) |
| HTML | 33.6% | generated coqdoc/docs (artifact) |
| OCaml | 8.3% | `coq-serapi` + plugins (Coq's impl language) |
| C | 2.9% | bundled verified-C in projects |
| TeX | 1.1% · JS 0.9% · **Python 0.3%** (ASTactic/ML env) | |
| tail (<0.2% ea) | Makefile, Shell, Asm, C++, Haskell, Standard ML, Scheme, Emacs Lisp, Vyper, GAP, Verilog, … | the 123 real projects bundle diverse code |

## SCIP-indexability
SCIP indexers exist for: python, typescript/JS, java, clang (C/C++), rust, go, ruby, dotnet. No scip-coq,
no scip-ocaml.
- **SCIP-coverable ≈ 4.2%** (Python + JS + C + C++ + Ruby).
- **SCIP-dark ≈ 95.8%** — incl. the dominant **Coq (52%)** and **OCaml (8.3%)**.

## The point
SCIP models imperative/OO call-graphs; it has no notion of dependently-typed terms, tactics, or proof
states — so the *valuable* 52% Coq corpus + 8.3% OCaml engine is exactly what SCIP cannot reach.
But **SCIP-dark ≠ intelligence-dark**: CoqGym is **natively symbolic** — SerAPI serves Gallina as
S-expressions, so the corpus *is its own index*. The proof world needs no SCIP retrofit because its
representation is already homoiconic and transformable.

⇒ same thread as `symbolic-c2.md`: Gallina ASTs (CoqGym) ↔ jank-IR ↔ MLIR-as-rediscovered-homoiconicity.
**SCIP is the imperative world's bolt-on code intelligence; the proof world's index is the S-expression.**
