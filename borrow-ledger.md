# What else jank-crane can borrow — extended transfer ledger

Beyond loopify. Each: source → target, the concrete artifact, payoff, transfer mechanism.
Rule (from both convergence roots): **passes/algorithms cross freely; runtime headers need the
rc↔gc re-parameterization** (crane assumes `rc.h`+native `T`; jank assumes Boehm GC + boxed `object_ref`).

## crane → jank  (jank borrows)
Ranked by payoff × how grounded in a real jank need.

1. **bigint mapping discipline** — crane `theories/Mapping/{NatGMP,ZGMP,NInt,ZInt}.v` give explicit,
   documented `nat`/`Z` → {GMP arbitrary-precision | native int64} mappings with stated overflow
   semantics. **Ties directly to open jank issue #792 ("bigint is slightly different from clj").**
   jank uses boost-multiprecision; borrowing crane's *mapping discipline* (one canonical numeric
   semantics, explicitly chosen) is a model for closing #792. Transfer: design/semantics. HIGH, live.
2. **escape-analysis → auto-transient** — crane proves uniqueness (PR#22) to fire `set &&` /
   `use_count()==1` in-place. Ported into jank `analyze/pass/optimize`, idiomatic `assoc`/`conj`-in-loop
   auto-lowers to `immer::to_transient`. HIGH perf; target (transients) already exists. (Pass.)
3. **variant-visitor match compilation** — crane `minicpp.mli` `Smatch` = `std::visit` /
   `holds_alternative` over `std::variant`. For jank's *typed* `cpp_*` fragment, static tagged-union
   dispatch avoids box-to-`object_ref` + behavior-bitset on known types. MED-HIGH perf. (Pass.)
4. **typed-core + mlutil reductions** — crane optimizes on MiniML (`mlutil.ml`: β-iota, DCE, inline)
   *before* C++ concerns. A MiniML-like typed core over jank's static fragment is where these run.
   **Fits open jank issue #98 ("Optimization epic").** MED (architectural). (Pass.)
5. **decl topo-sort + name pre-resolution** — crane `structure_analysis.ml` topo-sorts mutually-
   recursive enum/struct decls and `name_resolution.ml` pre-resolves names *before* printing C++.
   jank also emits textual C++ (`gen_cpp`); borrowing this fixes forward-reference ordering bugs in
   generated code. MED, correctness. (Pass.)
6. **concept/template generation** — crane `gen_typeclass_cpp` maps Coq typeclasses → C++20 concepts.
   jank interop could emit concept-constrained templates for native generics (better errors,
   monomorphization). MED, relevant to jank's `cpp/` interop. (Pass.)
7. **ITree / STM verified runtime** — crane `theories/cpp/{crane_itree.h, mini_stm.h}` (effect monad +
   versioned-TVar STM). A proven effect/coroutine + concurrency vocabulary. LOWER transfer: runtime
   headers, must be re-parameterized to let Boehm own the nodes (drop `shared_ptr`). (Header.)

## jank → crane  (crane borrows)
1. **immer (RRB/HAMT)** replacing `persistent_array.h` COW — O(log n) node-sharing vs O(1)-fast/O(n)-slow;
   crane's own header *names immer as the drop-in*. The headline convergence seam. (Header, under a
   non-GC immer policy so it composes with `rc.h`.)
2. **CppInterOp JIT** (`clang::Interpreter`) — crane is a batch extractor (rocq compile → clang++ →
   link). Borrowing jank's CppInterOp JIT gives crane an **interactive extract-and-run loop**: Coq →
   MiniCpp → JIT → run immediately. A genuinely new workflow crane lacks. (Architecture.)
3. **nREPL server** — jank ships a bencode nREPL (in jank). A model for an *interactive* Rocq-extraction
   REPL / editor integration crane has no analog for. (Architecture.)

## methodology (both directions)
- **The operational-model + property-diff stance** (what `model/loopify_model.clj` is): jank can borrow
  crane's *correctness discipline* without adopting Coq — a reference operational semantics + a
  property-based differential check against codegen. Cheap verification where full proofs are too dear.

## Picking next
Highest-leverage *new* candidate = **#1 bigint mapping (closes a live issue, #792)**; highest-leverage
*structural* = **#2 escape→transient**; most *novel* (reverse) = crane gaining a **CppInterOp JIT REPL**.
