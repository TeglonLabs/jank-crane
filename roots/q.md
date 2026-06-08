# `?` — orchestrator / witness (trit 0)

Glues `plus.md` (j+c, +1) and `minus.md` (c+j, −1) into one converged IR semantics.
GF(3) audit per claim: +1 graft exists · 0 witness closes · −1 pullback validates. Σ ≡ 0 ↔ converged.

## Contact manifold (the shared spine both pipelines project onto)

```
crane:  Gallina ─► MiniML ─► MiniCpp ─► C++(rc.h unique-RC, persistent_array.h) ─► native
jank:   Clojure ─► analyze ─► jank-IR ─► LLVM-IR ─► runtime(immer + bdwgc) ───────► native
                              └────────── M = converged IR (π∗ price) ───────────┘
```

Both are `source → typed/analyzed IR → C++/LLVM → memory-safe native, persistent structures`.
M = the pullback of the two IRs over their common image: a boxed-value persistent-structure SSA.

## The H¹ (where the gluing is obstructed = the real content)

**Memory policy is the spread / non-invertibility of the clearing unit.**

| axis | crane (`−`) | jank (`+`) |
|---|---|---|
| reclamation | manual RC, `rc.h` non-atomic single-thread | **bdwgc** tracing GC |
| cycles | leak unless hand-broken `weak<T>` | collected automatically |
| finalization | deterministic (refcount→0) | nondeterministic (GC sweep) |
| persistence | `persistent_array.h` (path-copy) | **immer** HAMT/RRB |
| threading | single-threaded by design | GC is concurrent-capable |
| correctness | carried by Rocq proofs in `theories/` | carried by tests / Clojure semantics |

**The seam that trivializes H¹:** `immer::memory_policy` is *parametric* over reclamation.
Instantiate ONE persistent library at two policies:
  - `immer` + `refcount` transience  ≈  crane `persistent_array.h` + `rc.h`
  - `immer` + `gc` / unsafe-free-list ≈  jank's `immer + bdwgc`
⇒ crane's `persistent_array.h` and jank's immer are the **same object at two MemoryPolicy values**.
The converged IR should be *parametric in the memory policy*, exactly as immer already is.

## Corrected spine (both roots agree)

Neither side emits LLVM IR directly. **Both funnel textual C++ through Clang.** The waist is shared.

```
crane:  CIC ─► MiniML ─► (mlutil opt) ─► MiniCpp ─┐
              miniml.ml   mlutil.ml      minicpp.ml │
                                                    ├─► C++ TEXT ─► Clang ─► LLVM ─► native
jank:   Clojure ─► analyze ─► jank-IR(SSA) ────────┘     (CppInterOp clang::Interpreter / AOT)
                   expr::*     ir/instruction.hpp
        collections: crane persistent_array.h (COW)  |  jank immer (RRB/HAMT) — both get/set/length
```

## The three real convergences (Σ contributions)

**(−1, Coplay validates) — mutate-iff-unique is ONE primitive under three names.**
  crane MiniCpp `Sassign_field.smb_reuse` guarded by `use_count()==1`  (minicpp.mli)
  ≡ crane persistent_array `set &&` rvalue fast-path O(1)               (persistent_array.h)
  ≡ immer **transient** / Clojure **transient**                         (jank type.hpp)
This is linear-ownership in-place mutation — the *same* optimization, the *same* guard, both sides.
It is the no-spread region of the contact manifold: when uniquely owned, copy = identity (η iso).

**(0, Witness closes) — the textual-C++ → Clang waist.**
  crane `cpp_print.ml` (logic-free printer) and jank `codegen/cpp_processor.cpp gen_cpp`
  both emit C++ text; both hand it to Clang/LLVM. No third pipeline, no 3-cycle. Triangle closes.
  Graft point is gift-wrapped: `persistent_array.h:28-34` already names immer as the drop-in;
  jank exposes `jit::processor::eval_string` / `load_object` as a backend call.

**(+1, Play grafts) — immer is crane's persistent_array at a finer policy.**
  crane COW `shared_ptr<vector>` (O(1)-fast / O(n)-slow) is the *degenerate* immer;
  immer RRB/HAMT (O(log n), node sharing) *refines* it. crane's `set &&` ⟷ immer transient is exact.
  ⇒ swap `persistent_array.h` for an immer header parameterized by reclamation policy. One header.

## The two H¹ classes (irreducible forks = parameters, not contradictions)

1. **Reclamation.** crane manual RC (`rc.h`, non-atomic, weak<T> cycle-break) vs jank Boehm GC
   (`immer<…, no_refcount_policy>` over `gc_heap`, RC off). immer's `memory_policy` is *parametric
   over exactly this axis* ⇒ converged IR carries `reclaim ∈ {rc, gc}` as a parameter.
   H¹ = ℤ/2 torsor: you *choose* per deployment; you cannot net it to zero. (−1 structural-arb that persists.)

2. **Typing.** crane dependently-typed → monomorphic native C++ (variant/visit, concept/template)
   vs jank dynamic → boxed `object_ref` + behavior-bitset dispatch. The mediator is jank-IR's own
   `cpp_box / cpp_unbox / cpp_into_object / cpp_from_object` (instruction.hpp:601-810): the
   box ⊣ unbox adjunction. unit η:A→¬¬A (box); spread = non-invertibility of unbox∘box on dynamics.
   Converged IR keeps BOTH faces; crane lives left (native), jank lives right (boxed), ops mediate.

## Converged IR M (sketch) — MiniCpp ∪ (cpp_*-corner of jank-IR), parametric in ⟨reclaim, boxing⟩

```
type M.value =                          // boxed ⊣ unboxed, mediated by box/unbox
  | Native   of cpp_type * cpp_expr     // crane left  : Tshared_ptr|Tvariant|concept-qualified
  | Boxed    of object_ref              // jank right  : dynamic, behavior bitset
type M.coll  = Persistent of { reclaim: Rc | Gc;            // immer memory_policy parameter
                               rep: Cow | Rrb | Hamt }      // crane=Cow, jank=Rrb/Hamt; transient shared
M.stmt =  Match(std::visit | holds_alternative)   // crane variant  ⟷  jank type-dispatch
        | Loop (while + shadow | vector-frame)     // crane loopify.ml — jank has structured CF in SSA
        | ReuseIfUnique(use_count()==1)            // THE shared primitive (transient)
backend:  emit C++ text ─► Clang ─► LLVM ─► native // shared waist; jit::eval_string | cpp_print
```

## GF(3) audit
  +1 Play    : immer drop-in graft into crane / crane→jank backend call    EXISTS   (+1)
   0 Witness : textual-C++→Clang waist closes the triangle                 CLOSES    (0)
  −1 Coplay  : mutate-iff-unique ≡ transient holds globally both sides     VALIDATES (−1)
  Σ = +1 + 0 + (−1) ≡ 0  →  CONVERGED, given a chosen ⟨reclaim, boxing⟩.
  Residual drift = the ℤ/2 reclamation torsor (rc|gc) + box/unbox non-iso on dynamics = honest H¹.

## crane ⟷ Dafny — the missing verified-extraction dual (fills the −root's open gap)

The `−` root found jank has **no analog** for crane's machine-checked `theories/`. Dafny is the
external reference that occupies that slot — both carry correctness into generated code, by opposite
discipline:

| axis | crane | Dafny | jank |
|---|---|---|---|
| correctness source | Rocq/CIC proofs + extraction soundness (fork of Rocq→OCaml) | SMT (Z3) discharge of pre/post/invariants | tests + Clojure semantics |
| proof locus | *before* extraction (source is a theorem) | *interleaved* with code (verify-then-compile) | runtime / interop type-soundness only |
| backend | textual C++ (rc.h / persistent_array.h) | multi-backend codegen (C#/Java/Go/JS/Py/**C++**) | textual C++ → Clang/LLVM (immer + bdwgc) |
| memory | manual RC, weak<T> cycle-break | host-GC (per backend) | Boehm GC, RC off |
| what's proven | functional correctness of the *spec* | the *imperative code* meets its contract | nothing behavioral |

Convergence reading: crane = **prove-then-extract** (correctness upstream of the C++ waist),
Dafny = **prove-the-code** (correctness fused to the imperative IR), jank = **no proof, dynamic**.
All three share the *textual-target → host-compiler* waist; they differ only in **where the proof
obligation sits relative to the IR** — i.e. crane/Dafny/jank are three points on a second parameter
axis `proof-locus ∈ {source, code, none}`, orthogonal to ⟨reclaim, boxing⟩. The full converged space
is the 3-axis cube ⟨reclaim {rc,gc} × boxing {native,boxed} × proof {source,code,none}⟩;
crane=(rc,native,source), jank=(gc,boxed,none), Dafny≈(gc,native,code). GF(3) per axis, Σ over the
cube ≡ 0 names a healthy clearing; drift on the proof axis = `−1 structural-arb` (unverified basis persists).

## Transfer ledger — what crosses the seam (crane → jank), ranked by payoff

Direction = +1 Play (j+c: jank *gains*). Split by transfer mechanism, because the rc/gc ownership
clash both roots flagged caps runtime-header reuse but NOT pass/algorithm reuse.

High-transfer (algorithmic — jank reimplements over its own SSA IR; ownership-clash-immune):
  1. **loopify** (`src/loopify.ml`) — recursion→`while`+explicit `std::vector` frame stacks
     (`_Enter/_Call/_Combine` via `std::visit`, loopify.ml:104-122). Handles non-tail multi-recursion,
     not just TCO. jank has the TARGET opcodes (`loop/recur/branch/ret/cpp_call`, instruction.hpp:351-487)
     but NO pass that builds them from arbitrary self-recursion. **Highest value: closes a capability
     gap jank has zero coverage of** (deep Clojure recursion currently overflows the native stack).
  2. **escape-analysis → auto-transient** — crane's Rocq-level uniqueness proof (PR#22) lets
     `a=set(a,i,v)` reliably hit the O(1) `set &&`/`use_count()==1` path. Ported into jank's
     `analyze/pass/optimize`, idiomatic `assoc`/`conj`-in-loop auto-lowers to `immer::to_transient`.
     **Highest *performance* value; target already exists** (jank immer transients).
  3. **two-IR split** (MiniML optimize *before* MiniCpp) — architectural, not a primitive: give jank a
     typed-core layer over its `cpp_*` static fragment to run crane-style β-iota/DCE before boxed
     dynamic dispatch tangles the tree (`mlutil.ml` ~1700L is the reference optimizer).

Low-transfer (runtime headers — need re-parameterizing to a GC memory policy, dropping crane's RC):
  4. `crane_itree.h` (verified ITree effect monad; its `run()` trampoline is exactly #1's target) and
     `mini_stm.h` (versioned-TVar STM) — usable only if Boehm owns the nodes instead of `shared_ptr`.

The ceiling: crane primitives assume non-atomic `rc.h` + native monomorphic `T`; jank assumes Boehm GC
+ `no_refcount_policy` + boxed `object_ref`. So *passes* cross freely; *linked runtime code* does not.
First artifact to build (if pushing past study): the loopify pass over jank-IR — highest payoff,
ownership-clash-immune, complete reference impl in `loopify.ml`.

## One-line restatement
crane and jank are the same compiler at two points of one parameter space — ⟨reclamation, boxing⟩
over a shared persistent-collection IR whose backend waist is textual-C++→Clang; their convergence
is the immer-transient = `use_count()==1` reuse primitive, and their only irreducible difference
(RC vs GC, native vs boxed) is a choice of parameter, i.e. an H¹ torsor you select rather than zero.

