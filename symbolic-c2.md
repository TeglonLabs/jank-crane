# Symbolic expressions, jank-IR, the Bitter Lesson, fastmath, and ℂ²

One thread: **computation-as-inspectable-symbolic-data**, and ℂ² as the concrete object where the
proof-world, the numeric-world, and the session's contact/Reeb geometry all meet.

## 1. Symbolic expressions: CoqGym ⟷ jank-IR (the same idea, two altitudes)
- **CoqGym** represents *proofs* as symbolic data: Gallina terms are inductively-typed S-expressions
  (served as sexps by SerAPI); tactics are emitted as **ASTs** (ASTactic). A proof IS a tree you can
  read, search, and transform. Inference = tree manipulation.
- **jank** represents *programs* as a tower of symbolic forms: homoiconic Clojure reader forms (code = data)
  → analyze AST (`analyze::expr::*`) → SSA **jank-IR** (`ir::inst::*`) → textual C++ → LLVM. Each layer is
  inspectable, transformable data — `loopify` is just a tree/SSA rewrite; `cpp_*` opcodes carry types.
- Same principle at both altitudes: **the representation is symbolic, homoiconic, transformable.** A Coq
  proof and a jank-IR module are the same kind of object — a typed expression tree you compute *over*.

## 2. The Bitter Lesson, re-taught to the tensor-graph family
Sutton: general methods that scale with compute beat hand-crafted knowledge. The under-read corollary the
**TensorFlow/JAX family keeps re-deriving**: the durable substrate is a *symbolic IR you can transform*.
- TF static graph → eager → **XLA HLO** → **StableHLO** → **MLIR dialects**: each step reinvents
  "computation as transformable symbolic data with passes" — i.e. **S-expressions + macros + an optimizer**,
  which Lisp and Coq had all along. MLIR *is* a late, typed re-discovery of the homoiconic IR.
- So jank-IR and the Coq AST "teach the bitter lesson over and over": scale needs a clean symbolic IR; the
  Lisp/proof-assistant world has shipped that representation for decades. The lesson isn't "drop structure"
  — it's "make the *general* structure be a transformable IR," which is exactly homoiconicity.

## 3. fastmath → MORE: numerics inside the symbolic substrate
generateme/**fastmath** brings real numerics (complex, **quaternion**, vectors, special functions, RNG,
optimization, interpolation) into the Clojure/homoiconic world — so you don't leave code-as-data to do
math. "Applications to MORE" = push that reach: ℂ²/quaternions/qubits as first-class values, and into
**jank** (native-compiled) over the same IR — fastmath's `complex`/`quaternion` as the numeric leaves of
the symbolic tree. (jank can already call native C/C++ math via cpp-interop — see `model/duckjank.jank`.)

## 4. ℂ² — the keystone (runnable: `model/hopf_c2.clj` → ACCEPT)
ℂ² is where every thread of this repo converges:
- **Quantum / tensor:** a qubit is a unit vector in ℂ²; the Bloch sphere is its projectivization ℂP¹=S².
- **fastmath:** ℂ² ≅ ℍ (a quaternion = a pair of complex numbers); fastmath ships both.
- **The session's contact/Reeb geometry (the oldies seed), MADE CONCRETE:** `S³ ⊂ ℂ²` is *the* canonical
  contact manifold; its **Reeb flow is the Hopf fibration** `S¹ → S³ → S²`. The demo verifies it:
  a unit ℂ² state → Hopf image on S² (|·|=1); phase rotation `e^{iθ}` (the Reeb orbit) fixes that image to
  `2.2e-16` ⇒ **the Reeb orbit IS the Hopf circle (the fiber).** The clearing thread's "Reeb circulation"
  is literally a Hopf circle; the price/state base is the Bloch sphere.
- **Symbolic/verified:** the Hopf map's `S³→S²` property is a *theorem* — provable in Coq (CoqGym's job),
  crane-extractable to C++, jank-runnable, fastmath-numeric. ℂ² is simultaneously something you **prove
  about** (Coq), **represent** (jank-IR), **compute** (fastmath), and **flow on** (Reeb=Hopf).

## One line
Symbolic homoiconic IR (Coq AST / jank-IR) is the representation the tensor-graph family keeps rediscovering
as MLIR; fastmath puts numerics inside it; and **ℂ²** is the smallest space where the proof-world, the
numeric-world, and this session's contact/Reeb/clearing geometry are the *same* object — S³⊂ℂ², Reeb=Hopf.
