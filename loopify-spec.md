# loopify → jank-IR : precision spec + non-cascade proof  (living doc, /loop)

Goal: specify the loopify pass for jank to semgrep/tree-sitter precision and show its semantics
cannot cascade into the rest of the jank codebase. Cite concrete crane + jank code.

Status: ITER 1 — skeleton grounded in real code. Open items flagged `TODO(iterN)`.

---

## 0. The two reference points (verbatim code anchors)

**crane** `c/crane/src/loopify.{ml,mli}` — pure `cpp_decl → cpp_decl`, runs *after translation,
before pretty-printing* (loopify.mli:4-9). Structure:
- `classify check body` (loopify.ml:806) → `if List.for_all (fun cs -> cs.cs_is_tail) calls` (811):
  ALL-TAIL ⇒ shadow-variable `while`; else ⇒ frame-based stack (`transform_nontail`).
- frame ADT ids (105-122): `_Enter` / `_Call` / `_Combine` / `_stack` / `_frame`, dispatched by
  `std::visit(Overloaded{...}, frame)` (mli/ml docstring 55-56).
- `has_recursive_branch_dependency` (690-762) + guard note (695-699): an unsafe non-tail shape where
  "popping the continuation frame before pushing `_Enter` can leave a **dangling raw pointer from a
  shared_ptr that was std::moved**" ⇒ **left UNTRANSFORMED (still recursive)**.
- `register_fundef` / `mutual_fn_table` (250-265): mutual-recursion group inlining.

**jank** `j/jank/compiler+runtime/include/cpp/jank/ir/instruction.hpp`:
- `instruction_kind` enum (35-83): incl. `named_recursion`(53) `recursion_reference`(54)
  `branch_set`(57) `branch_get`(58) `branch`(59) `loop`(60) `jump`(395, `loop` bool flag 407).
- `named_recursion` (351-369): `fn, fn_base_name, args, needs_dynamic_call` — the `recur` opcode.
- `loop` (462-487): terminator with `loop_block, merge_block, shadow,
  binding_shadows : native_vector<{name,value,type}>` — the mutable per-iteration bindings.
- `branch` (439-459): if/then/else terminator with pizlo upsilon/phi `shadow` for the merge value;
  `branch_set`/`branch_get` (414-436) are the υ/φ pair.
codegen `j/jank/.../codegen/cpp_processor.cpp`:
- `gen(named_recursion_ref)` :880 · `gen(loop_ref)` :1011 (`enter_block(loop_block)` :1039) ·
  `gen(recursion_reference_ref)` :870 · back-edge `if(inst->loop)` :948 · entry `gen_cpp(module)` :1917.
runtime policy `j/jank/.../runtime/detail/type.hpp:25-29`: `memory_policy = immer::memory_policy<
  heap_policy<gc_heap>, no_refcount_policy, …>` — **Boehm GC owns lifetime, RC off**.

---

## 1. What jank already has vs. what loopify adds

| crane loopify case | jank status | gap |
|---|---|---|
| all-tail ⇒ `while` + shadow vars | **ALREADY EXISTS**: `loop`+`binding_shadows`+`jump{loop=true}` (instruction.hpp:462-487, codegen :1011) — but only when the *programmer* wrote `(loop…(recur…))` | **auto-detect** plain self-recursive `defn` and lower it into this existing form |
| non-tail ⇒ explicit frame stack | **MISSING**: jank has no `_Enter/_Call/_Combine` frame construct | new lowering, but onto existing object model (frames as `object_ref`) |
| mutual fixpoint inlining | n/a | `TODO(iter3)` — scope decision |

⇒ The pass is **`ir::pass::loopify : ir::module → ir::module`** with two subcases:
- **(A) tail**: rewrite self-call-in-tail-position into `loop` + `named_recursion` +
  `binding_shadows` + `jump{loop=true}`. **Reuses jank's existing, tested codegen — zero new codegen.**
- **(B) non-tail**: build a frame stack as `native_vector<frame>` / `immer::vector` of `object_ref`;
  GC traces it. New lowering, but no new runtime type and no rc.

---

## 2. Non-cascade proof skeleton (the load-bearing section)

**Claim:** inserting `ir::pass::loopify` cannot change the observable behavior of any jank program
that does not get loopified, and preserves the behavior of those that do.

**L1 — Single insertion seam, bounded blast radius.**
Pipeline: `read::parse → analyze::processor → ir::processor (builds ir::module) → [passes] →
codegen::cpp_processor::gen_cpp (:1917) → jit::processor::eval`.
loopify is a pure `module → module` rewrite inserted between `ir::processor` and `gen_cpp` —
exactly crane's "after translation, before pretty-printing" slot. It reads/writes only `ir::module`;
it touches no `analyze::expr`, no reader, no runtime, no codegen internals.
`TODO(iter2)`: cite the exact registration call site in `ir/processor.cpp` / `analyze/pass/*`.

**L2 — Closed opcode set (the semgrep/tree-sitter-enforceable invariant).**
loopify may CONSUME only `{call, dynamic_call, named_recursion, recursion_reference, branch,
branch_set, branch_get, jump, ret}` and may PRODUCE only the PRE-EXISTING subset
`{loop, named_recursion, branch, branch_set, branch_get, jump(loop=true), binding_shadows}`.
It must NEVER construct a new `instruction_kind`, and must NEVER read or write the interop family
`{cpp_call, cpp_member_call, cpp_constructor_call, cpp_new, cpp_box, cpp_unbox, …}` (instruction.hpp:601-810)
nor `{var_def, var_deref, closure, type_erase}`.
⇒ Because every produced node already has tested codegen (cpp_processor.cpp §§870-1168) and tested
runtime, codegen/runtime behavior is unchanged *by construction*. This invariant is **statically
checkable** — see §3 (a semgrep rule on the pass source + a tree-sitter query over emitted IR).

**L3 — Conservative guard ⇒ partial identity (core lemma).**
Mirror crane `classify`(806) + `has_recursive_branch_dependency`(690). If a body is not provably
loopify-safe, **return it unchanged** (still recursive). Therefore
  `loopify(f) ∈ { f, f' }` with `f' ≈_obs f`.
The pass can never make a working function wrong; worst case it declines to optimize. Non-cascade ⟸
the fallback is literal identity.

**L4 — The GC dividend (jank is strictly SAFER than crane here).**
crane's guard (690-699) exists for ONE reason: under `rc.h`/`shared_ptr`, popping a continuation
frame before pushing `_Enter` can dangle a raw pointer into a `std::move`'d shared_ptr. Under jank's
`gc_heap + no_refcount_policy` (type.hpp:25-29) frames are `object_ref` traced by Boehm GC ⇒ the
moved-from / premature-free hazard **cannot occur**. The exact unsafe-shape class that forces crane to
bail is SAFE in jank. (The rc/gc H¹ fork pays a safety dividend precisely at loopify.)
⇒ jank's (B) non-tail case can be MORE complete than crane's, not less.

**L5 — Equivalence obligations (what must be discharged, not assumed).**
For a loopified `f' = loopify(f)`, prove `∀ args. f(args) ≈_obs f'(args)`:
  (a) **eval order**: jank is strict; frame stack must preserve left-to-right arg evaluation + effect
      order (crane sequences via `_Enter`→`_Combine`). `TODO(iter2)`: spell the ordering for (B).
  (b) **value identity**: jank `identical?`/`=` on the result. Frames hold the SAME `object_ref`s (GC,
      no copy) ⇒ identity preserved for free (vs crane COW which must reason about it).
  (c) **stack semantics**: programs that depended on `StackOverflowError` (none — Clojure has no such
      contract; jank overflows the native stack non-recoverably) ⇒ no behavior is lost, only gained.
Discharge plan: §2-L2 static guarantee + a **differential test** (run `f` vs `f'` over a corpus,
assert equal results AND equal effect traces). Lands in `compiler+runtime/test/` against the
`clojure-test-suite` submodule. `TODO(iter2)`: name the exact test dir + a property harness.

---

## 3. semgrep / tree-sitter precision  `TODO(iter2)` — to be written next
- semgrep rule (over the C++ pass source) asserting L2: the pass file constructs only allowed
  `inst::*` types; flags any `make_box<inst::cpp_*>` / new instruction_kind. Draft the YAML.
- tree-sitter query (over emitted IR dump / or over the C++) verifying no `cpp_*` opcode appears
  inside a loopified function's body that wasn't there pre-pass (delta = ∅ on the interop set).
- the `classify`/guard predicate transliterated to a jank-IR walk: tail-position detection over
  `branch`/`jump`/`ret` terminators (cite instruction.hpp terminator flags: branch:449, loop:478,
  jump:403).

## 4. Open items
- `TODO(iter2)`: exact pass registration site; ordering proof for (B); semgrep YAML + ts query; test dir.
- `TODO(iter3)`: mutual recursion (crane `register_fundef`) — in scope or deferred?
- `TODO(iter3)`: interaction with jank's existing `analyze/pass/optimize` (ordering vs other passes).
