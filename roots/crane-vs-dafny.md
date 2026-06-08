# Crane vs Dafny

This note compares Bloomberg's `crane` approach with the Dafny approach, using
the local Crane checkout, the existing `jc-converge` notes, the local
`dafny-zig` backend notes, and current Dafny reference documentation.

## One-line distinction

Crane starts from Rocq terms checked by the Rocq kernel and extracts them into
idiomatic C++; Dafny starts from a specification-rich programming language,
proves verification conditions with Boogie/Z3, and then translates verified
programs to target languages.

That is the load-bearing difference: Crane is proof-assistant extraction into a
C++ ecosystem; Dafny is SMT-backed program verification plus compilation.

## What Bloomberg/Crane is

Crane is a Rocq plugin for extracting Rocq code to modern C++, with optional
Bloomberg BDE-flavored mappings. The local README states the project extracts
Rocq to valid, performant, memory-safe C++ and is a fork of Rocq's built-in
Rocq-to-OCaml extraction machinery. See:

- `c/crane/README.md:16-18`
- `c/crane/README.md:89-99`
- `c/crane/README.md:103-131`

Its concrete compiler spine is:

```text
Rocq CIC -> MiniML -> MiniCpp -> C++ text -> clang/native
```

The source evidence is explicit in `c/crane/src/minicpp.mli:4-18`: MiniML handles
type erasure and language-agnostic extraction, while MiniCpp captures
C++-specific idioms such as smart-pointer memory management, variants,
templates, concepts, namespaces, structs, move semantics, enum classes, and
constructors.

Crane's design target is also explicit: verified Rocq components should become
readable, reviewable C++ libraries consumable by C++ teams. See
`c/crane/docs/Design-Principles.md:7-29` and
`c/crane/docs/Design-Principles.md:35-64`.

Crane does not claim a fully verified compiler pipeline. Its design doc says it
chooses practical assurance plus verified source programs, with differential
testing and static analysis validating the extractor. See
`c/crane/docs/Design-Principles.md:88-103`.

## What Dafny is

Dafny is a programming language and verifier. The official reference describes
it as an imperative, sequential language with specification constructs including
preconditions, postconditions, frame specifications, and termination metrics.
The `dafny` tool verifies by translating programs to verification conditions and
checking them with Boogie and usually Z3, then can compile verified programs to
targets such as C#, Java, JavaScript, Python, Go, and limited C++.

Primary source:

- https://dafny.org/latest/DafnyRef/DafnyRef
- https://dafny.org/latest/Compilation/Boogie

The current reference also says `dafny translate <language>` runs resolution and
verification unless `--no-verify` is used, then emits target-language code. It
notes that the target rendering is intended to be semantically faithful, but
resource and target-language limits can obstruct that intent.

## Where Princeton CoqGym sits

`princeton-vl/CoqGym` is not a third compiler/extraction approach alongside
Crane and Dafny. It is a learning environment, dataset, and benchmark for
automating interaction with the Coq proof assistant.

Primary sources:

- https://github.com/princeton-vl/CoqGym
- https://arxiv.org/abs/1905.09381
- https://proceedings.mlr.press/v97/yang19a/yang19a.pdf

The ICML 2019 paper constructs CoqGym from 71K human-written proofs across 123
Coq projects and introduces ASTactic, a model that generates Coq tactics as
abstract syntax trees. The agent loop is: observe goals, local context, and
global environment; emit a tactic; let Coq execute it; receive new goals; stop
when no goals remain. The GitHub README exposes the same role concretely:
`data/*.json` files store Coq commands, goals, hypotheses, proof trees, and
hashes into an LMDB `sexp_cache`; ASTactic is trained on individual proof steps,
not whole compiler outputs.

So CoqGym's place in this map is:

```text
CoqGym/ASTactic -> candidate Coq tactics -> Coq/Rocq kernel accepts/rejects
                                      -> checked Rocq artifact -> Crane extraction
```

It is upstream of Crane. CoqGym may help produce the Rocq proof/program that
Crane later extracts, but it does not change Crane's trusted extraction boundary.
If a learned tactic succeeds, the trust still flows through the Coq/Rocq kernel,
not through the neural model.

Relative to Dafny, CoqGym occupies the "proof automation/search policy" slot.
Dafny gets much of its automation from Boogie/Z3 VC solving; CoqGym studies a
different automation style: learned tactic synthesis inside an interactive proof
assistant. Dafny automation discharges VCs; CoqGym automation writes candidate
tactic scripts. Both are search helpers. Neither is itself the semantic
guarantee.

The local skill at `a/asi/skills/coqgym/SKILL.md` matches that reading: it calls
CoqGym a machine-learning environment for automated theorem proving with Coq,
centering the dataset, ASTactic, CoqHammer integration, proof-state
representation, and evaluation metrics.

## What local `dafny-zig` adds

The local `dafny-zig` material is not upstream Dafny itself. It is a backend
experiment/skill that consumes Dafny's compiled AST shape and emits Zig with a
small runtime layer. It frames the core problem as bridging Dafny's mathematical
model, especially unbounded integers, memory, and termination, into Zig's finite
systems model.

Relevant local evidence:

- `a/asi/skills/dafny-zig/SKILL.md:10-22`
- `a/asi/skills/dafny-zig/SKILL.md:31-56`
- `a/asi/skills/dafny-zig/src/dast.zig:1-12`
- `a/asi/skills/dafny-zig/src/compiler.zig:55-85`
- `a/asi/skills/dafny-zig/src/runtime.zig:11-21`
- `a/asi/skills/dafny-zig/.topos/papers/zig-syrup-manifesto.txt:6-30`

This makes `dafny-zig` comparable to Crane at the backend/runtime boundary, but
not at the proof-source boundary. The source proof story is still Dafny's
SMT/VC story, not Rocq kernel-checked proof-term extraction.

## Comparison axes

| Axis | Bloomberg/Crane | Dafny |
|---|---|---|
| Source language | Rocq/Gallina terms, definitions, and proofs | Dafny programs with contracts, invariants, ghost state, frames, and termination metrics |
| Verification style | Proof assistant kernel checks terms/proofs before extraction | Automated VC generation discharged by Boogie/Z3, with user-supplied specs/lemmas/invariants |
| Typical user loop | Prove in Rocq, extract to C++, inspect/build generated code | Write program plus specs, fix verifier failures, compile verified code |
| Intermediate representation | Rocq extraction MiniML, then Crane-specific MiniCpp | Dafny AST/resolved program, verification conditions via Boogie, then backend-specific compiler IRs/ASTs |
| Target philosophy | High-quality C++ as the primary product, optionally BDE-shaped | Multi-target compilation with target runtimes: C#, Java, JavaScript, Python, Go, limited C++ |
| Runtime model | C++ value/smart-pointer surface, custom headers such as `persistent_array.h`, `rc.h`, STM/ITree support | Target-language runtime libraries implement Dafny abstractions; local `dafny-zig` uses `BigInt`, `RcSlice`, explicit allocators |
| Memory semantics | Crane tries to make safe C++ hard to misuse; MiniML escape analysis drives owned/borrowed and reuse choices | Dafny verifies against mathematical/semantic abstractions; each backend must realize them faithfully under target resource limits |
| Trust boundary | Rocq kernel and source proofs are trusted; extractor, mappings, generated C++, headers, clang, and externs are trusted/validated | Dafny resolver/typechecker/VC generator, Boogie, Z3, compiler backend, target runtime/toolchain, and externs are in the TCB |
| Automation | Lower automation for proofs, stronger dependent/proof-assistant expressiveness | Higher automation for routine imperative correctness, with SMT brittleness around quantifiers/triggers/nonlinear arithmetic |
| Best fit | Verified components for C++ shops that want readable native libraries | Whole programs or algorithms specified in a mainstream verification language with relatively pushbutton proof automation |

CoqGym is orthogonal to this table: it is a tactic-learning layer for the
Crane/Rocq side's proof authoring problem, not a target language, verifier, or
runtime. If the table had a third column, it would say "input: Coq proof states;
output: candidate tactics/proof scripts; checker: Coq kernel; target: none."

## The biggest practical differences

1. Proof provenance differs.

Crane's assurance starts in Rocq. If the Rocq development proves a property, the
proof is checked by the Rocq kernel before extraction. Dafny's assurance starts
from specifications over Dafny code; the tool generates VCs and asks an SMT
solver to discharge them.

2. The generated code occupies different ecosystems.

Crane is intentionally C++-first. BDE is optional, but the design is visibly
shaped by Bloomberg's C++ review, style, and library environment. Dafny is
multi-target: it wants verified Dafny code to move into C#, Java, JavaScript,
Python, Go, and other targets with runtime support.

3. The backend trust problem is present in both, but differently shaped.

Crane openly says it is not a CompCert-style verified compiler. The trust story
is verified Rocq source plus a pragmatic, tested extractor. Dafny likewise
verifies source-level obligations, but compilation still depends on unverified
backend/runtime/toolchain components. The Dafny reference's note about target
resource limits is important here: a proved mathematical abstraction still has
to fit a finite target.

4. Memory is a central design object for Crane, a backend obligation for Dafny.

Crane's `escape.ml` explicitly classifies owned versus borrowed parameters and
enables reuse when a value has a unique owner (`c/crane/src/escape.ml:4-19`,
`c/crane/src/escape.ml:174-220`). Its `persistent_array` models Rocq persistent
arrays with copy-on-write and a unique-owner rvalue fast path
(`c/crane/theories/cpp/persistent_array.h:4-34`, `:66-104`).

Dafny's source model is more abstract. Backends must implement sequences,
arrays, integers, objects, and ghost erasure in target-specific ways. The local
`dafny-zig` runtime makes this visible by implementing Dafny `int` as `BigInt`
and shared sequences through explicit runtime structures.

5. Interop is configured at different semantic levels.

Crane has extraction mappings from Rocq entities to C++ terms/types/headers,
including BDE mappings. Dafny has `{:extern}` declarations and target-specific
interop wrappers. In both systems, extern mappings are a proof/trust boundary:
the verifier cannot automatically prove the foreign implementation satisfies
the source-level meaning.

## Guarantee ledger

| Boundary | Crane | Dafny |
|---|---|---|
| Source acceptance | Rocq kernel checks terms and proofs before extraction | Dafny parses/resolves/types the program, then generates VCs from specs and code |
| Proof engine | Interactive/dependent proof in Rocq; automation only where Rocq tactics/decision procedures are used | Mostly automated SMT via Boogie and usually Z3 |
| Translation to executable code | Not fully verified; Crane explicitly chooses pragmatic assurance plus generated-code readability and testing | The source program is verified, but compiler/runtime/target execution remain trusted infrastructure |
| Foreign/runtime primitives | Crane extraction mappings and C++ headers must implement the Rocq-side model | `{:extern}` declarations and runtime libraries must implement the Dafny-side model |
| Resource model | Rocq mathematical types can be mapped to bounded C++ types; Crane's own base-library docs warn that some such mappings are unsafe for serious use, e.g. `nat` to `unsigned int` | Dafny's docs explicitly warn that target resource/language limits can obstruct perfect semantic faithfulness |
| Empirical validation | Crane has an extract/clang/test pipeline (`c/crane/src/extract_env.ml:1914-2020`) plus the design goal of differential testing/static analysis | Dafny verification failures are surfaced before compilation by default; backend validation is target/runtime-specific |

The short form: Crane's strongest checked object is the Rocq source artifact;
Dafny's strongest checked object is the Dafny program against its specifications.
Neither system makes generated target code magically outside the TCB.

## Where each approach can surprise you

Crane surprises:

- A theorem about a Rocq term is only as executable as the extraction mappings
  used for that term. `Crane Extract Inlined Constant`, `Extract Inductive`, and
  `Extract Skip` are power tools, but every such mapping is also a semantic
  promise.
- The generated C++ can be readable and memory-safe by construction, while still
  depending on unverified C++ headers, compiler behavior, and target library
  semantics.
- Bounded mappings can change the mathematical model. The base library's
  `NatIntStd.v` maps `nat` to `unsigned int` and warns this is unsafe for serious
  use because Rocq `nat` is infinite while `unsigned int` is bounded
  (`c/crane/docs/Crane-Base-Library.md:60-67`).

Dafny surprises:

- A Dafny proof is a proof of the written specification, not of the user's
  unstated intent. The automation can verify the wrong contract very quickly.
- SMT automation is powerful but can become brittle around quantifiers, triggers,
  nonlinear arithmetic, heap framing, and induction-like arguments.
- Compiling verified Dafny is still a backend/runtime obligation. Official Dafny
  docs say translation verifies by default, but also warn that resource and
  target-language limits can prevent perfect semantic rendering.
- `{:extern}` is analogous to Crane custom extraction: useful for integration,
  but it shifts semantic responsibility to the user and target implementation.

## If you wanted them to meet

The honest convergence is not "Crane should become Dafny" or "Dafny should
become Rocq." They occupy different proof cultures. A realistic meeting point is
a backend contract:

1. Define a small shared semantic surface for executable primitives: integers,
   arrays/sequences, maps/sets, effects, exceptions/errors, allocation, and
   concurrency.
2. For Crane, show that each extraction mapping and C++ header realizes the Rocq
   model for that surface.
3. For Dafny, show that each backend runtime realizes the Dafny model for the
   same surface.
4. Use translation validation, differential testing, and runtime/static analysis
   at that surface instead of trying to prove the entire industrial compiler at
   once.

The local `dafny-zig` work is interesting exactly here. It consumes DAST JSON and
compiles DAST to ZAST (`a/asi/skills/dafny-zig/src/main.zig:72-79`,
`a/asi/skills/dafny-zig/src/compiler.zig:55-85`), maps Dafny `int` to
`rt.BigInt` (`a/asi/skills/dafny-zig/src/compiler.zig:515-524`), and implements
`BigInt`/`RcSlice`/`Seq` in a Zig runtime
(`a/asi/skills/dafny-zig/src/runtime.zig:11-39`,
`a/asi/skills/dafny-zig/src/runtime.zig:260-330`). That makes it a backend
semantics experiment, not a replacement for Dafny's verifier. It also shows why
Crane and Dafny are closer at the runtime-contract layer than at the proof
front-end layer.

## A useful mental model

Use Crane when the artifact you most want is:

- a proved Rocq component,
- rendered as idiomatic C++,
- with proof/source shape still mentally traceable in the generated library,
- for integration into an existing C++ codebase.

Use Dafny when the artifact you most want is:

- an executable program written directly in a verification language,
- with contracts, invariants, frames, and termination obligations checked by SMT,
- then compiled to one of several mainstream targets.

Use local `dafny-zig`-style work when the artifact you want is:

- Dafny's SMT verification front end,
- but a systems-language backend with explicit allocation and predictable runtime
  costs,
- accepting that the backend/runtime becomes the delicate part of the TCB.

## Bottom line

Crane and Dafny both turn verified source artifacts into executable code, and
both have an unverified translation/runtime boundary. They differ in where the
proof lives and who the generated code is for. Crane is a Rocq-to-C++ extraction
strategy for bringing proof-assistant components into C++ production. Dafny is a
verification-aware programming language whose compiler uses Boogie/Z3 to prove
source-level contracts before emitting code to multiple targets.
