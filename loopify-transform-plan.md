# loopify transform — implementation plan (post-IR-study, 2026-06-08)

The scaffold is done (`ir/opt/loopify`, `-Oloopify`, default-off, verified no-op). This plans the
transform BODY, after reading how jank actually lowers loop/recur. **Key finding: the cleanest
implementation is NOT where the scaffold sits.**

## How jank lowers loop/recur (from `ir/processor.cpp` + `analyze/expr/*`)
- The loop machinery is **analyze-driven**: `analyze::expr::let` with `loop_kind::loop_with_recur` sets up
  the loop block + per-binding shadows; `analyze::expr::recur` emits `branch_set`(shadows := new args) +
  `jump(loop_recur_target, /*loop*/ true)` (the back-edge); `named_recursion`/`recursion_reference` handle
  cross-arity self-reference. The IR `loop`/`branch_set`/`branch_get`/`jump(loop=true)` instructions are
  the *output* of this, not where the decision is made.
- A plain self-recursive call `(defn f [n] … (f (dec n)))` is analyzed as a **`call` to the var f** — NOT
  a recur. So it lowers to an ordinary call instruction (native-stack-consuming). That's the gap loopify
  closes; that's why `(s 800)` segfaults.

## Consequence: split the transform by layer
**Tail case (high value, ~zero new codegen) → ANALYZE layer.**
Detect a function whose self-calls all sit in tail position, then rewrite the AST:
```
(defn f [a b] BODY[… (f x y) …tail])   ⇒   (defn f [a b] (loop [a a b b] BODY[… (recur x y) …]))
```
This reuses the existing `loop`(loop_with_recur)+`recur` lowering entirely — no new IR, no new codegen.
It is the jank-native form of crane's "all-tail ⇒ while + shadow vars" (`loopify.ml:811`).
Implement as an `analyze/pass/*` (sibling of `optimize.cpp`), gated by `opts.loopify`. Steps:
  1. tail-position analysis over the analyze AST (`if`/`do`/`let` propagate tail to their last form;
     `call` to the enclosing var in tail position = a loopify target).
  2. guard (spec L3): if ANY self-call is non-tail, decline (leave for the non-tail path) — partial identity.
  3. rewrite: wrap body in a `let`/`loop` over the params (loop_kind = loop_with_recur), replace each
     tail self-`call` with a `recur` expr carrying the call's args.
  4. mutual recursion (crane `register_fundef`): defer (single-function self-recursion first).

**Non-tail case (harder) → keep at IR-opt layer (the current scaffold), OR a CPS/trampoline transform.**
Needs crane's explicit `_Enter/_Call/_Combine` frame stack — a real second project. The GC dividend
(spec L4) makes it *safe* in jank (no dangling-shared_ptr hazard), so it can be more complete than crane,
but it is strictly more work than the tail case. Frames as GC-traced `object_ref` in a `native_vector`.

## Revised placement (honest correction)
- The current `ir/opt/loopify.{hpp,cpp}` + `-Oloopify` flag + processor gate = the **non-tail** home and
  the user-facing switch. Keep it.
- Add an **`analyze/pass/loopify_tail.cpp`** for the tail case (the 90% win), gated by the same flag.
- The operational model (`model/loopify_model.clj`) already validates the *non-tail* 2-phase-stack
  algorithm; the tail case is even simpler (accumulator loop) and is what the analyze rewrite produces.

## Why this is the right call (vs writing code now)
Implementing the tail rewrite touches the analyze AST API (expr construction, local_frame, loop_kind) and
needs a rebuild per iteration (~15 min). It is a focused, multi-step task best done deliberately, not
drip-fed through rebuild-gated loop ticks. This plan de-risks it: the algorithm and the correct layer are
now pinned. Next concrete step: read `analyze/expr/{let,recur}.hpp` + `analyze/pass/optimize.cpp` to learn
the AST-construction API, then implement steps 1–3 of the tail case with the existing red test as the gate.
```
RED today : -Oloopify run skip-deep-non-tail-recursion.jank  → still segfaults (transform is identity)
GREEN goal: same → prints the value, no crash, suite still green with flag on AND off
```

## Tractability assessment (read the analyze-pass infra, 2026-06-08)
- `analyze/pass/optimize.cpp` (the entry point) is where a loopify pass would hook; it's tiny and the AST
  is "modified in place."
- BUT `analyze/pass/walk.hpp` is **visitor-only**: `postwalk`/`prewalk` take `void(expression_ref)` — they
  walk for side-effects, they don't return a rewritten tree. So the tail rewrite must **mutate exprs in
  place** and **construct new `let(loop_with_recur)` + `recur` nodes by hand** (with `local_frame`, binding
  setup, position propagation, and a custom tail-position walk that tracks position — which `postwalk`
  does not).
- ⇒ This is a **deep, multi-day compiler feature**, not a loop-tick task. A wrong version *silently
  miscompiles* (worse than the current loud segfault). It needs deliberate work by someone fluent in
  jank's analyze internals (ideally coordinated with the maintainers), with the red test as the gate.

## Recommendation
**Do not implement the transform via autonomous rebuild-gated loop ticks.** The scaffold is complete and
verified; the transform is cleanly scoped here. Resume it as a focused, dedicated effort (or an upstream
contribution) — `analyze/pass/loopify_tail.cpp`, steps 1–3 above, gated by the existing red test.
