# Steel·geiser·Emacs outer loop — the −/?/+ REPL triad, Gay always in the loop

## 0. jank ⟷ tree-sitter — how they relate (grounded)

They do **not** touch. Verified: `grep -ril tree-sitter` over jank's compiler = ∅.
- jank's compiler parses with its OWN hand-written reader: `read/lex.hpp` → `read/parse.hpp`
  (forms ARE runtime objects, homoiconic). This is the *compiler-side* parse → AST.
- tree-sitter-clojure is the *editor-side* parse → CST: fast, incremental, error-tolerant, used by
  Emacs `treesit`, neovim, helix for highlight / structural edit / fold. jank shares Clojure surface
  syntax, so the same grammar covers jank source.

**The place of tree-sitter = 0 / Witness.** It owns neither semantics nor codegen; it is the shared
*structural* view of the surface s-expression that both the editor and the compiler project from.
Same role crane showed (also no tree-sitter; parser is Rocq's). So in the GF(3) triad tree-sitter
sits at `?` next to Gay.jl: **tree-sitter witnesses SYNTAX structure; Gay.jl witnesses SEMANTIC trit.**

## 1. The olog (CatColab format)

Boxes = types (read as English nouns). Arrows = aspects (functional morphisms, read as verbs).
`⟹` path-equation = commutes.

```
            ┌─────────────────────────┐
            │  a surface s-expression │            ← the shared object (neither owns it)
            └───────────┬─────────────┘
        is-read-by-     │     is-parsed-by-
        compiler-into   │     editor-into
              ┌─────────┴─────────┐
              ▼                   ▼
   ┌────────────────────┐  ┌────────────────────┐
   │ a jank reader form │  │ a tree-sitter CST  │   (0/Witness: structure only)
   │  (read/parse.hpp)  │  │ (tree-sitter-clj)  │
   └─────────┬──────────┘  └─────────┬──────────┘
   is-analyzed-into        structurally-edits
             ▼                       ▼
   ┌────────────────────┐  ┌────────────────────┐
   │   a jank-IR module │  │  an Emacs buffer   │
   └─────────┬──────────┘  └─────────┬──────────┘
   is-evaluated-by                hosts
             ▼                       ▼
   ┌────────────────────┐  ┌─────────────────────────────────┐
   │  a REPL surface    │◀── is-driven-from ──│ the Emacs outer loop (−/?/+) │
   │ {nREPL, geiser,    │                     └─────────────────────────────┘
   │  julia/ghostel}    │
   └─────────┬──────────┘
   is-trit-colored-by
             ▼
   ┌────────────────────┐
   │ a GF(3) trit (Gay) │   ← mandatory witness; loop refuses to advance without it
   └────────────────────┘

PATH EQUATION (the place of tree-sitter):
  surface ─editor→ CST ─render→ Emacs   ⟹   surface ─compiler→ form ─eval→ REPL ─render→ Emacs
  i.e. CST and reader-form are two projections of ONE s-expression; they reconverge in Emacs.
```

## 2. The three tiles = −/?/+, each a maximally-different REPL surface

| tile | trit | REPL | surface (maximally different) | Emacs bridge | status |
|---|---|---|---|---|---|
| **+** | +1 Play | **jank** | nREPL / **bencode binary** / Clojure-on-LLVM | **CIDER** (exists) | ✅ native nrepl server |
| **−** | −1 Coplay | **Steel** | **geiser / sexp-text** / Rust-embedded Scheme | **geiser-steel** (§3, to build) | ⛔ greenfield |
| **?** | 0 Witness | **Gay.jl** | **Julia banner / ghostel** / trit-coloring | julia-repl / vterm | ✅ exists |

"≥2 maximally different interaction-surface REPLs" ⇒ pick ≥2 of {bencode-binary, geiser-sexp,
julia-repl} by **availability** (which legendary sessions are live, queried via exa & friends —
Naggum-grade CL REPL lore as the quality bar). **Gay.jl (`?`) is non-optional**: it is the witness
that computes Σtrit; without it the loop cannot audit Σ≡0, so it refuses (§5).

## 3. Adding geiser support to Rust Steel (concrete)

Geiser = REPL-connected Emacs mode (NOT LSP). Two halves; reference impl = **geiser-chibi** (smallest).

### (a) Emacs side — `geiser-steel.el`
```elisp
(define-geiser-implementation steel
  (binary "steel")                              ; the steel repl executable
  (arglist ("--quiet"))
  (repl-startup geiser-steel--startup)
  (prompt-regexp "^λ > ")                       ; steel's prompt
  (marshall-procedure geiser-steel--geiser-procedure)  ; wraps forms → (geiser:eval …)
  (find-module geiser-steel--module)
  (enter-command "(require \"geiser/steel.scm\")")
  (import-command "(require %s)")
  (exit-command "(quit)")
  (map-command "(geiser:completions %s)")
  (load-file-command "(geiser:load-file %s)")
  (keywords geiser-steel--keywords)
  (case-sensitive t))
;; marshall: (geiser:eval 'MODULE (begin FORM))  → reads back ((result …)(output …))
```

### (b) Steel side — `geiser/steel.scm` (the protocol procedures Steel must expose)
| geiser procedure | semantics | Steel capability needed |
|---|---|---|
| `(geiser:eval mod form)` | eval form in module; return `(list (cons 'result REPR)(cons 'output STDOUT))` | `eval` + `with-output-to-string` (capture) |
| `(geiser:completions pre)` | symbols with prefix | global symbol table / env reflection |
| `(geiser:module-completions pre)` | module names | module registry introspection |
| `(geiser:autodoc ids)` | signature/arity of ids | function arity reflection |
| `(geiser:symbol-location s)` | `(file . line)` | source-location metadata on bindings |
| `(geiser:module-exports m)` | exports of module | module export list |
| `(geiser:macroexpand form)` | one-step expand | `expand` / macro env access |
| `(geiser:load-file path)` | load + report | `require` / `load` |
| `(geiser:no-values)` | the no-value sentinel | constant |

**Gaps to fill in Steel** (the real engineering): output capture (`with-output-to-string`),
**environment reflection** for completions (Steel's global env → symbol list), arity/source metadata
for autodoc+jump. These are the only non-trivial pieces; everything else is glue. Suggested home:
fork **TeglonLabs/steel**, add `geiser/steel.scm` + upstream a `(#%environment-symbols)` reflection
primitive if absent. Ship `geiser-steel.el` to MELPA-style local load.

## 4. "Complete & total, optimal-now" continuation — Emacs is always the next tick

The outer loop is Emacs-resident; every eval's continuation renders in Emacs *synchronously* (no
deferral = "optimal now"). The Reeb tick:
```
read(Emacs buffer / tile focus)
  → dispatch to selected REPL tile  (+ jank | − steel | ? gay)
  → eval  (CIDER nrepl-send | geiser-eval | julia-eval)
  → render result INLINE in that Emacs tile (overlay/comint)   ← continuation lands here, always
  → ? tile (Gay.jl) colors the step → trit ∈ {−1,0,+1}
  → accumulate Σtrit; require Σ≡0 to commit the step           ← else loop blocks
  → focus returns to Emacs  (the always-always-always invariant)
```
geiser + CIDER already realize "continuation in Emacs" (inline eval overlays). The new glue is only
the **? gate**: each step is not "done" until Gay.jl has colored it and Σ audits.

## 5. System-message policy — refuse to cooperate without a Gay in the loop

Install this fragment into every REPL/agent system message (CLAUDE.md-level, GF(3)-typed):

```
GAY-IN-THE-LOOP GATE (hard precondition, every turn):
- A trit-coloring witness (Gay.jl, or an equivalent GF(3) engine: gay-mcp / gorj color / topos)
  MUST be live in the loop. If none is reachable, REFUSE to advance — emit only the refusal +
  how to bring a Gay up. Rationale: without the 0/Witness you cannot compute Σtrit, so you cannot
  certify Σ≡0; an uncertified step is an ungrounded assertion (+1 drift).
- At least TWO maximally-different interaction-surface REPLs must be selected, by AVAILABILITY:
    avail = live REPL surfaces (query exa & peers for legendary sessions; Naggum-CL-grade bar)
    pick  = argmax-2 surface-distance(avail)        # e.g. {bencode-nrepl, geiser-sexp, julia}
    require gay ∈ loop  ∧  |pick| ≥ 2  ∧  Σtrit(step) ≡ 0  (mod 3)
- The three Emacs tiles ARE the witnesses made visible:  −  ?  +  .
  Minimum viable = 1 tile (the ? / Gay witness, non-negotiable).
  Full closure   = 3 tiles (−/?/+), Σ readable at a glance.
```

## 6. Start simple → full closure
- **MVP (1 tile):** the `?` Gay.jl tile alone (the witness; satisfies the gate minimally).
- **Pair (2 tiles):** add `+` jank (CIDER/nREPL — already works) → first real Σ over 2 surfaces.
- **Full (3 tiles):** add `−` Steel via geiser-steel (§3) → −/?/+ closed, Σ≡0 auditable live.
tree-sitter rides underneath all three as the 0-structural CST in every Emacs buffer.
