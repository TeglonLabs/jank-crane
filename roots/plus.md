# `+` root — j+c map (jank-first): how bloomberg/crane fits into jank's scaffold

trit **+1 (Play / Generate)** · direction **j+c** · read-only study, neither repo modified.

Convergence thesis (given):
- **crane**: Gallina → MiniML → MiniCpp → C++ (`unique_ptr`, `rc.h`, `persistent_array.h`) → native (Rocq plugin, OCaml, verified extraction, **textual C++ printer**, AOT)
- **jank**:  Clojure → reader → analyze → **jank-IR** → **textual C++** → CppInterOp/Clang → LLVM ORC JIT → native (Clojure-on-LLVM, C++)

Key finding up front: **jank does *not* emit LLVM IR directly.** Its own SSA IR (`jank::ir`) is lowered to **textual C++** by `codegen::cpp_processor` and handed to a `clang::Interpreter` (CppInterOp) which produces LLVM IR and JITs it. So *both* compilers share a textual-C++ waist — that is the load-bearing fact for the graft.

---

## 1. jank's actual compilation pipeline (real paths)

| Stage | What it does | Data structure(s) | File(s) |
|---|---|---|---|
| **Lex** | Clojure text → token stream | `lex::token` | `include/cpp/jank/read/lex.hpp`, src in `read/` |
| **Parse (read)** | tokens → runtime objects (forms are *already* runtime values: `object_ref`, lists/vectors/maps) | `runtime::object_ref` (homoiconic) | `include/cpp/jank/read/parse.hpp`, `src/cpp/jank/read/parse.cpp` |
| **Analyze** | forms → typed semantic AST (`expr::*`), local frames, C++ interop resolution | `analyze::expression_ref` = `jtl::ref<expression>`; ~40 `expr::*` node types | `include/cpp/jank/analyze/processor.hpp`, `src/cpp/jank/analyze/processor.cpp`, `analyze/expr/*`, `analyze/cpp_util.cpp` |
| **Analyze passes** | optimize, strip-source-meta, force-boxed, walk | same AST | `analyze/pass/{optimize,strip_source_meta,walk}.cpp`, `analyze/step/force_boxed.cpp` |
| **IR lowering** | semantic AST → **jank-IR** (SSA: functions → blocks → instructions, pizlo upsilon/phi for branches) | `ir::module` / `ir::function` / `ir::block` / `ir::instruction` | `include/cpp/jank/ir/{instruction,processor,builder,visit,print}.hpp`, `src/cpp/jank/ir/{processor,builder,instruction,print}.cpp` |
| **Codegen** | jank-IR → **textual C++** (string buffers via `util::format_to`, then `clang_format`) | `codegen::generated_cpp { declaration, expression }` | `include/cpp/jank/codegen/{cpp_processor,api}.hpp`, `src/cpp/jank/codegen/cpp_processor.cpp` (entry `gen_cpp(ir::module)`, `codegen/api.cpp`) |
| **JIT** | textual C++ → LLVM IR → native, in-process | CppInterOp `clang::Interpreter` + LLVM ORC `LLJIT` | `include/cpp/jank/jit/processor.hpp`, `src/cpp/jank/jit/processor.cpp` |
| **Eval driver** | ties analyze→IR→codegen→JIT for `eval` | — | `include/cpp/jank/evaluate.hpp`, `src/cpp/jank/evaluate.cpp` |

### The exact codegen → JIT seam (verified, `src/cpp/jank/jit/processor.cpp`)
```
processor::eval(ir::module const &module)                 // jit/processor.cpp:276
  auto const generated{ codegen::gen_cpp(module) };       //   :278  IR -> textual C++
  eval_string(generated.declaration);                     //   :279  feed Clang
...
  Cpp::CreateInterpreter(args, {}, vfs, CodeModel::Large) //   :224-225  CppInterOp interpreter
```
`eval_string` (jit/processor.cpp:352-386) calls into the CppInterOp `clang::Interpreter`, which compiles the C++ to LLVM IR and JITs through ORC `LLJIT` (includes `llvm/ExecutionEngine/Orc/LLJIT.h`, `llvm/IR/Verifier.h`, `IRReader/IRReader.h`). AOT object loading via `load_object` / `load_bitcode` / `load_ir_module` on the same processor.

### jank-IR is a real SSA stream, not a tree
`include/cpp/jank/ir/instruction.hpp`:
- `enum class instruction_kind : u8` (lines 35-81): ~50 opcodes — `parameter`, `literal`, `persistent_{list,vector,array_map,hash_map,hash_set}`, `function`, `closure`, `def`, `var_deref/ref`, `dynamic_call`, `branch`/`branch_set`/`branch_get` (upsilon/phi, lines 412-413 cite pizlonator gist), `loop`, `case_`, `try_/catch_/finally`, `throw_`, `ret`, and a full **C++ interop opcode family** `cpp_raw/cpp_value/cpp_into_object/cpp_from_object/cpp_call/cpp_constructor_call/cpp_member_call/cpp_member_access/cpp_box/cpp_unbox/cpp_new/cpp_delete`.
- `struct instruction` (83-99): `kind`, `name` (= `jtl::immutable_string` SSA id), `type` (`jtl::ptr<void>` = a Clang type), `location`. `is_terminator()` virtual.
- `struct block { native_vector<jtl::ref<instruction>> instructions; }` and `struct function { native_vector<block> blocks; }` and `struct module { native_vector<function> functions; lifted_constants; lifted_vars; }` (`ir/processor.hpp:29-85`).

The `cpp_*` opcode family is the decisive piece for crane: **jank-IR already has first-class typed C++ interop instructions** carrying real Clang types — exactly the layer crane's MiniCpp lives at.

---

## 2. The jank "scaffold" a second front-end can plug into

A reusable, front-end-agnostic host. Four reusable surfaces:

**(a) Boxed value runtime / object model** — `include/cpp/jank/runtime/object.hpp`
- `struct object : gc` with a closed `object_type` enum (object.hpp:10-99) + an **open** `object_behavior` bitset (284-309) dispatched through virtual methods (`equal/to_string/to_hash/call/get/find/compare`). GC-managed (Boehm; `object` derives `gc`).
- `oref<T>` / `object_ref` smart handle (`runtime/oref.hpp`). Everything boxed flows as `object_ref`.

**(b) immer-backed persistent collections** — see §3-immer below. `persistent_vector/hash_map/hash_set` wrap immer; `persistent_list/array_map/sorted_*` use other backings.

**(c) IR node types** — `ir::module/function/block/instruction` (§1). A second front-end emits these (or feeds codegen directly).

**(d) Codegen + JIT surface** — `codegen::gen_cpp(ir::module) → generated_cpp` and `jit::processor::{eval, eval_string, load_object, load_bitcode}`. This is a **reusable backend**: give it textual C++ (or an `ir::module`) and it returns native, JIT or AOT.

---

## immer usage — exactly where it lives (file:line)

immer is vendored at `third-party/immer/` but jank's **own** use is tightly localized to two files:

- `include/cpp/jank/type.hpp:12-29` — the global **memory policy**:
  ```cpp
  using memory_policy = immer::memory_policy<immer::heap_policy<immer::gc_heap>,
                                             immer::no_refcount_policy,      // GC owns lifetime, not RC
                                             immer::default_lock_policy,
                                             immer::gc_transience_policy,
                                             false>;
  ```
  i.e. immer nodes are allocated on the **Boehm GC heap with no refcounting** — jank does *not* use immer's shared_ptr RC; the GC reclaims.
- `include/cpp/jank/runtime/detail/type.hpp:3-55` — the persistent container typedefs:
  - `native_persistent_vector = immer::vector<object_ref, memory_policy>` (RRB-tree) — `:38`
  - `native_persistent_hash_set = immer::set<object_ref, …, memory_policy>` (HAMT/CHAMP) — `:41-42`
  - `native_persistent_hash_map = immer::map<object_ref, object_ref, …, memory_policy>` (HAMT/CHAMP) — `:50-54`
  - plus `extern template` instantiations of these immer classes — `:24-33`
  - sorted map/set fall back to `std::map`/`std::set` (`:45-48,57-62`, "TODO: bring in proper immutable sorted").
- Consumed by the boxed wrappers: `include/cpp/jank/runtime/obj/persistent_vector.hpp:22` (`using value_type = runtime::detail::native_persistent_vector; … value_type data;`), and the analogous `persistent_hash_map.hpp` / `persistent_hash_set.hpp`.

So: **immer = the value layer of three jank collections (vector/hashmap/hashset), allocated on GC, no RC.** array_map (small maps), list, and sorted variants are non-immer.

---

## 3. The j+c graft — how crane fits in

### (a) Can crane's MiniCpp emit into jank's runtime instead of `rc.h`/`persistent_array.h`?
**Partially yes, and crane already anticipates it.** `c/crane/theories/cpp/persistent_array.h:28-34` literally says *"Why not immer::flex_vector? … immer is a drop-in replacement — same interface, O(log n) persistent set. The extraction directives don't change; only this header would be swapped out."* So crane's `persistent_array<T>` → jank's `native_persistent_vector` (`immer::vector<object_ref, jank::memory_policy>`) is a header swap on crane's side.

Matches:
- Both are COW/persistent-by-value; `set`/`get`/`length` map onto immer `update`/`operator[]`/`size`.
- crane's escape-analysis-driven in-place `set(...) &&` (use_count==1) corresponds to immer's `transient_type` (`native_transient_vector`, detail/type.hpp:39) — jank already exposes transients (`persistent_vector::to_transient()`).

Conflicts:
- **Lifetime model clash.** crane's `rc.h` is *non-atomic, single-threaded, single-allocation Rc/Weak with manual cycle-breaking* (rc.h:6-8,42-108). jank's immer policy is **`gc_heap` + `no_refcount_policy`** — Boehm GC owns everything, RC is *off* (type.hpp:25-29). To emit into jank you must drop crane's `rc.h` entirely and let the GC reclaim, OR keep `rc.h` and lose jank's GC integration. You cannot mix per-object: jank objects derive `gc`.
- **Element type.** jank collections are monomorphized to `object_ref` (everything boxed). crane's `persistent_array<T>` is generic over native `T`. Emitting into jank means **boxing** crane's `T` into jank `object`s (or using jank's `opaque_box` / `native_pointer_wrapper`, object.hpp:75,96) — lossy/heavyweight vs crane's typed native arrays.

Verdict: crane→immer for the *array* is a near-free swap; crane→full-jank-object-model forces boxing + GC adoption and is a deeper commitment.

### (b) Can jank-IR become a crane lowering target (Gallina → jank-IR → LLVM), reusing jank's backend?
**Yes — this is the strongest structural fit, because jank-IR already has the cpp_* interop opcodes.** Crane's MiniCpp is essentially "typed C++ with persistent arrays and RC"; jank-IR's `cpp_call/cpp_constructor_call/cpp_member_call/cpp_member_access/cpp_new/cpp_delete/cpp_box/cpp_unbox/cpp_raw` (instruction.hpp:601-810) carry **real Clang `jtl::ptr<void>` types**, which is exactly MiniCpp's abstraction level. Crane would:
1. Lower Gallina/MiniML/MiniCpp → `ir::module` (build `function`/`block`/`instruction` via `ir::builder`, `include/cpp/jank/ir/builder.hpp`), using the `cpp_*` opcodes for the typed core and bypassing the dynamic Clojure opcodes (`dynamic_call`, `var_deref`, etc.) entirely.
2. Call `codegen::gen_cpp(module)` → textual C++ → `jit::processor::eval_string` / `load_object`.

Caveat: jank "reuses LLVM" only **through textual C++ + CppInterOp**, not via direct `llvm::IRBuilder` (there is *no* `llvm_processor.hpp`; grep for `IRBuilder/LLVMContext` in `ir/` returns nothing). So "Gallina → jank-IR → LLVM" really means "Gallina → jank-IR → **C++** → Clang → LLVM" — which is the *same shape* as crane's existing C++ printer, just with jank's IR + JIT/AOT plumbing and immer collections underneath. That is the convergence, not a shortcut around C++.

### (c) Cleanest single seam — concrete files/types on each side

The **textual-C++ waist** is the cleanest seam, and within it the **immer collection swap** is the single tightest meeting point.

| | jank side | crane side |
|---|---|---|
| **Primary seam (collections)** | `include/cpp/jank/runtime/detail/type.hpp:38` `native_persistent_vector = immer::vector<…>` | `theories/cpp/persistent_array.h` (the file crane says to swap) |
| **Backend seam (codegen/JIT)** | `codegen::gen_cpp(ir::module)` in `codegen/cpp_processor.cpp` + `jit::processor::eval_string` in `jit/processor.cpp:352` | crane's MiniCpp **textual C++ printer** (its `extraction` C++ emitter) |
| **IR seam (deeper)** | `ir::builder` (`include/cpp/jank/ir/builder.hpp`) + the `cpp_*` opcodes in `ir/instruction.hpp:601-810` | crane MiniCpp AST nodes (member-call / new / delete / ctor) |

**Recommended single point of contact:** crane keeps its C++ printer but (i) swaps `persistent_array.h` for an immer-backed header parameterized by `jank::memory_policy`, and (ii) targets `jank::jit::processor::eval_string`/`load_object` as its backend. One header + one backend call. This avoids forcing either the full jank object model or a from-scratch IR re-lowering on crane while still converging both onto immer + the same Clang/LLVM JIT/AOT surface.

---

## 4. Honest mismatches

1. **Memory model: GC-no-RC vs linear-RC.** jank = `immer::gc_heap` + `no_refcount_policy` (Boehm GC owns lifetime, type.hpp:25-29); every `object` derives `gc`. crane = `unique_ptr` + non-atomic `rc<T>`/`weak<T>` with manual cycle-breaking (rc.h:6-8). These are *incompatible ownership disciplines*: adopting jank's runtime means adopting Boehm GC and discarding `rc.h`; keeping `rc.h` means forgoing jank's collection policy. immer the *data structure* is shared, but immer the *memory policy* is the conflict.

2. **Typing: dynamic-boxed vs dependently-typed-native.** jank is dynamically typed — everything is `object_ref`, collections are monomorphized to `object_ref`, dispatch is runtime virtual/behavior-bitset (object.hpp:284-309). crane is dependently typed, erased to *native* monomorphic C++ types via verified extraction. Feeding crane values into jank collections requires **boxing** (losing crane's static type info); feeding jank values into crane requires unboxing/casts the proofs can't see. The `cpp_box/cpp_unbox/cpp_into_object/cpp_from_object` opcodes (instruction.hpp:627-655,750-780) are exactly jank's boundary machinery for this, and they are *not free*.

3. **Backend reality: no direct LLVM emission on either side.** Despite "Clojure-on-LLVM", jank emits **textual C++** (`gen_cpp`) into CppInterOp/Clang, not `llvm::IRBuilder` — there is no `llvm_processor`. crane likewise prints **textual C++**. So "reuse jank's LLVM backend" overstates it: the shared substrate is *Clang consuming C++ text*, then LLVM. Good news for convergence (the waists literally line up); bad news for anyone hoping to skip C++ via a native LLVM-IR bridge.

4. **Persistence algorithms differ: RRB/HAMT-tree vs COW-vector.** jank's immer vector is an RRB-tree (O(log n) persistent set, structural sharing); crane's `persistent_array` is a `shared_ptr<vector>` **copy-on-write** (O(1) on the unique-owner fast path crane's PR#22 escape analysis guarantees, O(n) deep-copy otherwise). They expose the same `get/set/length` interface but have different cost models and different sharing — crane's escape-analysis fast path ≈ immer's transient, but crane's *slow* path is a full O(n) copy where immer is O(log n). Behaviorally equivalent (both persistent), operationally divergent.

5. **Opcode-set overlap is partial.** jank-IR carries a large *dynamic Clojure* opcode set (`dynamic_call`, `var_ref/deref`, `closure`, `named_recursion`, `case_` with hash-shift, lazy seq machinery) that crane has no source for, and crane needs nothing of. Crane would use only the `cpp_*` + control-flow (`branch`, `loop`, `ret`, `try_`) subset. The IR is a *superset* host, not a tailored target — fine, but the fit is "crane uses a corner of jank-IR", not "jank-IR == MiniCpp".
