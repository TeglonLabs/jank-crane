# `−` root — c+j map (crane-first): crane's unique aspects pulled back toward jank-IR/immer

trit **−1 (Coplay / Validate)** · direction **c+j** · read-only study, neither repo modified.

Convergence thesis (given), restated crane-first:
- **crane**: Gallina/Rocq-CIC → **MiniML** → **MiniCpp** → C++ (`rc.h`, `persistent_array.h`, `unique_ptr`) → native. A **Rocq (Coq) plugin in OCaml**, forking Rocq's built-in CIC→OCaml extraction and **retargeting it to memory-safe modern C++**.
- **jank**: Clojure → reader → analyze → **jank-IR (SSA)** → textual C++ → CppInterOp/Clang → LLVM ORC JIT/AOT → native.

**Validated up front:** crane's unique aspect is *not* parsing — confirmed it has no tree-sitter; the front end is Rocq's own kernel/`Constr`. The unique artifact is the **two-IR verified-extraction pipeline (MiniML → MiniCpp) plus the RC/persistent memory model in `theories/cpp/`**. Both compilers are source→IR→**textual C++**→native, RC/persistent-backed. The crane-side proofs live in `theories/` (Coq `.v`), which is the half jank has no analog of.

---

## 1. Crane's IR pipeline, concretely

```
Rocq CIC  --[extraction.ml]-->  MiniML  --[translation.ml]-->  MiniCpp  --[loopify.ml]-->  MiniCpp'  --[cpp_print.ml]-->  C++ text
                                  ^                                                                         (driven by extract_env.ml / extraction.ml)
                          mlutil.ml optimizes here
```

Driver/orchestration: `src/extract_env.ml(.mli)` exposes the vernacular commands (`simple_extraction`, `full_extraction`, `separate_extraction`, `extraction_library`) and "extract → write files → compile with clang". The stage helpers it calls are below.

### Stage A — CIC → MiniML (`extraction.ml` / `miniml.ml`)
- `miniml.mli` defines a **language-agnostic functional core** (~15 term constructors). Key types:
  - `type ml_ast` — `MLrel | MLapp | MLlam | MLletin | MLglob | MLcons | MLtuple | MLcase | MLfix | MLexn | MLdummy | MLaxiom | MLmagic | MLuint | MLfloat | MLstring | MLparray` (`miniml.mli:105-122`). De-Bruijn lambda calculus + constructors + `match` + `fix` + primitive literals (note `MLparray` = the persistent-array primitive, `MLuint` = int63).
  - `type sign = Keep | Kill of kill_reason` and `signature = sign list` (`miniml.mli:23-34`): the **type-erasure record** — which CIC arguments survive (`Keep`) vs are dropped as types/props/implicits (`Kill Ktype | Kprop | Kimplicit`). This is the heart of extraction.
  - `type ml_type = Tarr | Tglob | Tvar | Tmeta {mutable contents} | Tdummy | Tunknown | Taxiom | Tstring` (`miniml.mli:43-57`) — `Tmeta` carries **mutable unification cells** for ML type reconstruction.
  - `type ml_ind_packet { ip_typename; ip_consnames; ip_logical; ip_sign; ip_vars; ip_types; ip_consarg_names }` and `inductive_kind = Coinductive | Standard | Record of … | TypeClass of …` (`miniml.mli:61-79`). `ip_consarg_names` keeps Rocq binder names so C++ fields are `d_left` not `d_a0`. **Typeclasses are classified already at MiniML level** (`TypeClass`).
  - Top level: `ml_decl = Dind | Dtype | Dterm | Dfix` and `ml_structure = (ModPath.t * ml_module_structure) list` (`miniml.mli:137-186`).
- `extraction.mli`: `extract_constant`, `extract_fixpoint`, `extract_constant_spec` — type erasure, signature (`Keep`/`Kill`) computation, drops universe levels/implicits.
- `mlutil.ml` (~1700 lines) runs **on MiniML**: beta-iota reduction, dead-code elim, inlining, match simplification. MiniML exists separate from MiniCpp precisely to optimize on a simple type-erased AST, not raw CIC.

### Stage B — MiniML → MiniCpp (`translation.ml` / `minicpp.ml`) — the crucial artifact
`minicpp.mli` (the unique IR; `minicpp.ml:4-40` explains why it can't merge with MiniML). It is a **C++-shaped AST** — separate `cpp_type`/`cpp_expr`/`cpp_stmt`/`cpp_decl` sorts where MiniML had only `ml_type`/`ml_ast`:

- `cpp_type` (`minicpp.mli:90-117`): `Tshared_ptr`, `Tvariant of cpp_type list`, `Tref`, `Tptr`, `Tany` (std::any for erased existentials), `Tauto`, `Tdecltype`, `Tnamespace`, `Tqualified` (`typename Base<T>::nested`), `Tmod (const/static/extern)`. **The memory model is in the type grammar**: sum types are `std::variant`, managed pointers are `std::shared_ptr`.
- `cpp_expr` (`minicpp.mli:244-327`): `CPPmk_shared`, `CPPshared_ptr_ctor`, `CPPmove`, `CPPforward`, `CPPvisit`/`CPPoverloaded` (std::visit + overloaded visitor for `match`), `CPPstd_get`/`CPPstd_holds_alternative`/`CPPstd_get_if`, `CPPshared_from_this`, `CPPany_cast`, `CPPparray`. C++ idioms are first-class.
- `cpp_stmt` (`minicpp.mli:129-193`): `Sreturn`, `Sswitch` (enum class), `Smatch` (variant if/else-if chain via `holds_alternative`/`get`), `Swhile`/`Scontinue`/`Sbreak` (**loopify targets**), `Sassign_field`/`Sderef_asgn` (in-place mutation for memory reuse). `smatch_branch` (`minicpp.mli:201-239`) carries the **reuse fast-path**: `smb_reuse : (cond, rf_var, body) option` with `cond = use_count() == 1` → mutate-in-place else allocate-fresh.
- `cpp_decl` (`minicpp.mli:501-545`): `Dtemplate` (params + optional concept constraint), `Dnspace`, `Dfundef`/`Dfundecl` (bool = suppress pure/constexpr for monadic fns), `Dstruct {ds_fields; ds_tparams; ds_constraint; ds_needs_shared_from_this}`, `Dconcept`, `Denum`, `Dstatic_assert`.
- Classification: `cpp_ind_kind = IK_Standard (variant) | IK_Enum (enum class) | IK_Record (struct) | IK_Eponymous (record merged into module struct) | IK_TypeClass (concept)` (`minicpp.mli:42-50`).

**How translation works** (`translation.mli`): `gen_expr : env -> ml_ast -> cpp_expr`, `convert_ml_type_to_cpp_type`, `gen_decl`, `gen_dfuns` (mutual fixpoints), `gen_ind_cpp`/`gen_ind_header_v2`/`gen_record_cpp`/`gen_typeclass_cpp`, `gen_instance_struct`. Pattern maps (`minicpp.ml:28-35`):
- `MLcase` → `std::visit`/overloaded visitor (or `Smatch` holds_alternative chain).
- `MLcons` → factory returning `shared_ptr` (`CPPmk_shared`).
- Rocq **module** → C++ **struct**; **module type** → **concept**.
- **typeclass** → C++ **concept** + instances become structs with static methods (`gen_instance_struct` + `static_assert` that the instance models the concept).
- **inductives**: `Standard` → tagged `std::variant` struct; all-nullary/no-param → `enum class`; single-record → struct.

### Stage C — pre-analysis passes (before rendering)
- `method_registry.ml(.mli)`: scans the whole `ml_structure` **once** to decide which free functions become **methods** on their "eponymous" type's struct (module `List` + inductive `list` + `app : list A -> list A -> list A` ⇒ `app` is a method, `this_pos=0`). Carries `method_info { epon_ref; this_pos; ind_tvar_positions; returns_any }`. `body_safe_for_method` blocks `return this` (raw ptr ≠ shared_ptr) — a memory-safety guard in method selection.
- `structure_analysis.ml(.mli)`: enum registration, inductive-name collision collection, module classification (wrapper vs regular) + **topological sort** so defs precede uses; main module last.
- `name_resolution.ml`, `cpp_names.ml`, `cpp_state.ml`: pre-resolve C++ names (`cpp_name { cn_base; cn_qualified; cn_needs_typename }`) so the printer is logic-free.

### Stage D — loopify (`loopify.ml(.mli)`) — recursion → loop
Operates on `Minicpp.cpp_decl` **after translation, before printing** (`loopify.mli:4-9`). Classifies recursion and rewrites:
- **tail recursion** → `while` loop with mutable shadow vars (no stack);
- **single non-tail** → explicit `std::vector` stack with `_Enter`/`_Call` frames;
- **multi-recursion (fib)** → chained `_Call` / `_Enter/_After/_Combine` frames dispatched by `std::visit(Overloaded{…}, frame)`.
Exists because Rocq programs recurse structurally on big trees; naive C++ would stack-overflow. Also **bypasses the reuse fast path** when a reuse branch has residual recursive calls it can't decompose (`loopify.ml:58-68`). Untyped frame fields fall back to `decltype(expr)` with `std::declval<T&>()` rewriting.

### Stage E — print (`cpp.ml`, `cpp_ind.ml`, `cpp_print.ml`)
Pure textual C++ emission of `cpp_decl`; no name resolution (already done). This is the **textual-C++ waist** crane shares with jank.

---

## 2. Crane's runtime memory model (`theories/cpp/`) — its analog of immer

Hand-written, header-only runtime that extracted code links against. Mostly GPT-5-generated, dependency-free, single-threaded.

- **`rc.h`** — `crane::rc<T>`/`crane::weak<T>`: **Rust-style, non-atomic, single-allocation** RC. `ControlBlock<T>` holds `strong`/`weak` counts **inline with `T`'s storage** (one `new` for both, `make_rc`). `release()`: `--strong; if 0 → ~T(); if weak==0 → delete`. Explicitly **not thread-safe, no `enable_shared_from_this` by design** (`rc.h:6-8`); `weak<T>` to break cycles manually. crane's "linear-ish RC" — closer to Rust `Rc`/`std::unique_ptr` than to a GC.
- **`persistent_array.h`** — `persistent_array<T>` = **`shared_ptr<vector<T>>` + copy-on-write**, NOT a tree. Mirrors Rocq `PrimArray` (`make`/`get`/`set`/`length`/`copy`). The clever bit is **ref-qualified `set`**:
  - `set(...) const &` (lvalue, named var still in use) → **always deep-copy** (O(n)) to preserve persistence;
  - `set(...) &&` (rvalue temporary) → if `data_.use_count()==1`, **mutate in place** (O(1)); else deep-copy.
  crane's **escape analysis (PR #22)** decides unique ownership at the Rocq level and emits `a = set(a,i,v)` so the old binding becomes a temporary → `&&` fires → O(1). Header-doc (`persistent_array.h:28-34`) notes immer::flex_vector is a **drop-in O(log n) replacement** swappable without changing extraction directives.
- **`lazy.h`** — `crane::lazy<T>`: thunk + `mutable optional<T>` memo; `force()` evaluates once. Call-by-need.
- **`mini_stm.h` / `crane-stm/`** — STM: `stm::TVar<T> : TVarBase` with atomic `version`, `mutex`, `condition_variable`; `StagedWrite` (type-erased staged writes); commit via nothrow-swap + `version.fetch_add` (`mini_stm.h:46-52`); `static_assert`s require nothrow-move/swap for strong exception safety. Runtime for crane's verified concurrency monads (`theories/Monads/STM.v`).
- **`crane_itree.h`** — C++ realization of **Interaction Trees** (`theories/Monads/ITree*.v`): the verified effect/coinductive-IO monad. `skipnode.h` = skip-list node.

**One-line characterization:** crane's memory model is **manual non-atomic RC (`rc.h`) + COW persistent array (`persistent_array.h`) with Rocq-level escape analysis driving an O(1) in-place fast path**, plus a small verified monadic runtime (lazy/STM/ITree) that is the C++ image of proofs in `theories/Monads/`. RC owns lifetime; no GC.

---

## 3. The c+j pullback — duals

### (a) crane MiniCpp ⟷ jank-IR

| concern | crane **MiniCpp** (`minicpp.mli`) | jank **IR** (`ir/instruction.hpp`) | verdict |
|---|---|---|---|
| shape | **typed tree** of `cpp_type`/`cpp_expr`/`cpp_stmt`/`cpp_decl` | **SSA stream**: `instruction_kind` enum, `block`→`function`→`module`, pizlo upsilon/phi | different shape; same altitude ("typed C++") |
| typed C++ interop core | `CPPmethod_call`, `CPPnew`, `CPPstructmk`, `CPPmk_shared`, `CPPget'`, `Tglob`, `Dstruct` | `cpp_call`, `cpp_member_call`, `cpp_member_access`, `cpp_constructor_call`, `cpp_new`, `cpp_delete`, `cpp_box`/`cpp_unbox`, `cpp_raw` (`:601-810`) carrying real Clang `jtl::ptr<void>` types | **tight dual** — the meeting altitude |
| control flow | `Sif`, `Smatch`(variant), `Sswitch`(enum), `Swhile`/`Scontinue`/`Sbreak`, `Sreturn` | `branch`/`branch_set`/`branch_get`, `case_`(hash-shift), `loop`, `jump`, `ret`, `try_/catch_/finally` | dual, but jank is **explicit-block SSA** vs crane **structured-stmt** |
| sum-type match | `CPPvisit`+`CPPoverloaded` / `Smatch` holds_alternative | `case_` (Clojure case, hash shift/mask) — *no* variant-visitor opcode | **crane-only**; jank dispatches dynamically |
| recursion→loop | **loopify pass** emits `Swhile`/frame-stacks | `loop` + `named_recursion`/`recur` are native IR ops (front-end emits them) | crane *derives* iteration; jank *has* it as a primitive |
| missing on crane side | — | `dynamic_call`, `var_ref`/`var_deref`, `closure`, `def`, `persistent_*` literal ops, `type_erase` (the whole **dynamic Clojure** half) | crane has no source for these, needs none |
| missing on jank side | — | no type-erasure signature (`Keep`/`Kill`), no concept/typeclass decl, no template-param IR, no `static_assert` model | jank is dynamically typed → no need |

**Net:** MiniCpp ≈ the **`cpp_*` + control-flow corner of jank-IR**, *plus* a static-types/templates/concepts layer jank lacks, *minus* jank's dynamic-Clojure opcodes crane lacks. Neither is a superset; they overlap precisely on "typed C++ calls/ctors/members + branch/loop/ret".

### (b) crane `persistent_array.h` / `rc.h` ⟷ immer

| | crane | jank (immer) | same idea? |
|---|---|---|---|
| persistent vector | `persistent_array<T>` = `shared_ptr<vector<T>>` **COW** | `native_persistent_vector = immer::vector<object_ref, memory_policy>` **RRB-tree** (`detail/type.hpp:38`) | **same interface (get/set/length), different algorithm**: COW O(1)-fast/O(n)-slow vs RRB O(log n) structural sharing |
| in-place fast path | escape-analysis `set(...) &&` when `use_count()==1` | `transient_type`/`to_transient()` (mutation under unique ownership) | **direct dual** — crane's unique-owner mutation ≡ immer transients |
| structural sharing | **none** (whole vector copied on branch) | **yes** (HAMT/RRB share unchanged subtrees) | crane shares only via `shared_ptr` at *whole-array* granularity; immer at *node* granularity |
| hashmap/hashset | not in `theories/cpp/`; crane uses BDE/std mappings (`theories/Mapping/`) | `immer::map`/`immer::set` (HAMT/CHAMP) | crane has no persistent HAMT; immer does |
| refcount | `rc.h`: **non-atomic single-alloc Rc/Weak**, manual cycle break, RC owns lifetime | immer `memory_policy = heap_policy<gc_heap> + no_refcount_policy` → **Boehm GC owns lifetime, RC off** (`type.hpp:25-29`) | **opposite disciplines.** `rc<T>` ≈ Rust `Rc`/`unique_ptr`; jank ≈ tracing GC |

**Net:** the *data structure* idea converges (persistent, COW-or-shared, unique-owner in-place fast path = transients). The *memory discipline* **diverges hard**: crane = linear/manual RC, no GC; jank = Boehm GC + `no_refcount_policy`. crane's `persistent_array.h` even names immer as the swap-in upgrade — so crane→immer **for the array** is near-free; crane→jank's *policy* means dropping `rc.h` and adopting GC.

### (c) crane's verified-extraction guarantee ⟷ jank analog

Crane's distinctive asset has **no jank counterpart.** `theories/` is real Coq: verified `Monads/{ITree,STM,IO,Thread,Par,Path,Clock,Env,TempFile}.v`, `Mapping/*.v` (proved-correct mappings of Nat/Z/Real to GMP/`int63`/BDE), `External/{Vector,StringView}*.v`, `Extraction.v` driving it. The pipeline is a **fork of Rocq's own CIC→OCaml extraction**, so MiniML inherits Rocq's extraction metatheory (erasure of `Prop`/universes), and crane adds C++-mapping correctness (`theories/Mapping/`). TCB = `extraction.ml` + `translation.ml` + the runtime headers.

jank has **nothing of this kind**: a dynamically-typed Clojure compiler whose "correctness" is test-suite + soundness of CppInterOp/Clang, not machine-checked proofs. The closest structural analog is **type-correctness of the C++-interop layer** (`analyze/cpp_util`, the `cpp_*` opcodes carrying Clang types) — jank trusts Clang's type checker where crane trusts Coq's kernel. So the dual of "verified extraction" on the jank side is **"Clang-typed interop"**: a *type-soundness* witness, not a *behavioral-correctness* proof. No jank artifact proves the emitted program refines a spec.

---

## 4. The single cleanest seam (crane → jank)

**Swap crane's `persistent_array.h` for an immer-backed header; keep everything else crane-side.** The one place the two worlds already line up by design.

- **crane side:** `theories/cpp/persistent_array.h` — its own header-doc (`:28-34`) says immer::flex_vector is a drop-in with the *same `get`/`set`/`length` interface* and "the extraction directives don't change; only this header would be swapped out." MiniML already emits the array primitive (`MLparray`, `miniml.mli:122`); MiniCpp carries it (`CPPparray`, `minicpp.mli:276`).
- **jank side:** `include/cpp/jank/runtime/detail/type.hpp:38` — `native_persistent_vector = immer::vector<object_ref, memory_policy>`, with `to_transient()` matching crane's `set(...) &&` fast path one-to-one.

Concretely: replace `shared_ptr<vector<T>>` COW with `immer::vector<T, P>`; map `set const&` → `immer::vector::set` (persistent), `set &&`/`use_count()==1` → `transient::set`. **Validation caveat:** using *jank's* `memory_policy` inherits `gc_heap + no_refcount_policy` (Boehm GC), incompatible with `rc.h` and with crane's native-typed elements (jank monomorphizes to `object_ref`, forcing boxing). So the **clean, low-commitment seam** is: crane keeps `rc.h` and native `T`, adopts immer's *data structure* under a **non-GC `memory_policy`** (immer supports refcounted/heap policies), converging on RRB/transients without Boehm GC. The deeper seam (crane emits `ir::module` via jank's `ir::builder` using only `cpp_*`+control-flow opcodes, then `codegen::gen_cpp` + `jit::processor`) is structurally available but costs an IR re-lowering and still routes through textual C++ (jank has no direct `llvm::IRBuilder`).

**Best single point of contact:** `persistent_array.h` ⟷ `detail/type.hpp:38 native_persistent_vector` (crane's `set &&` ⟷ immer `transient`). One header, interface-identical, named by crane itself.
