# crane extraction, concrete — and why it's "loopify everywhere"

Grounded in a committed golden file (`vendor/crane/tests/basics/nat/`), so no build needed. This makes the
−1/verified side as concrete as the jank side, and unifies loopify + Finding F1 + the rc/gc fork.

## The example: Rocq `nat` → C++
```coq
Inductive nat := O | S (n : nat).
Fixpoint add m n := match m with O => n | S x => S (add x n) end.
```
extracts to (`nat.h.golden`):
```cpp
struct Nat {
  struct O {};
  struct S { std::shared_ptr<Nat> n; };          // recursive field -> shared_ptr (the RC model)
  using variant_t = std::variant<O, S>;          // inductive -> tagged union (Smatch / std::visit)
  variant_t v_;
  static Nat s(Nat n) { return Nat(S{std::make_shared<Nat>(std::move(n))}); }
  ...
};
```

## ★ The revelation: crane NEVER recurses on the native stack over an inductive
A deep `Nat` (a million `S`) would overflow the native stack on any naive recursive traversal — including
the **destructor** and the **copy**. So crane auto-generates *iterative, explicit-`std::vector`-stack*
versions of every structural operation:
- **`~Nat()`** — iterative drain: pop `S` frames into a `std::vector`, free without recursion.
- **`clone()`** — iterative deep copy: a `std::vector<_CloneFrame{_src,_dst}>` worklist + `while` loop.
- **`loopify.ml`** — recursive *functions* (`add`, …) → loops + explicit frame stacks.
Same pattern (`_Enter/_Call/_Combine` / `_CloneFrame` frames) in all three. **crane's whole extraction is
"loopify everywhere": no operation on a recursive value may use the call stack recursively.**

## Why — and the unification with F1 + the rc/gc fork
This pervasive loopification is **forced by crane's RC memory model** (`rc.h`, manual destruction): with
manual `shared_ptr` teardown, a deep free *is* deep native recursion ⇒ would segfault ⇒ must be iterative.
- **jank's GC model exempts it from loopified destructors/clones** — Boehm GC traces and frees without
  recursion, so jank needs no `~Nat()`/`clone()` stack machinery. The ⟨reclaim⟩ axis (the session's central
  H¹) decides *where loopification is mandatory*: RC ⇒ everywhere; GC ⇒ only compute.
- **But jank still overflows on deep COMPUTE recursion (Finding F1)** — GC saves teardown, not the call
  stack of a recursive `defn`. So `(s 800)` SIGSEGVs. crane already solved the compute side (`loopify.ml`);
  jank has not. ⇒ **`loopify`-for-jank is exactly the one piece of crane's "loopify everywhere" that jank's
  GC does NOT give for free.** That is precisely the pass we scaffolded.

## Net
crane is loopify-everywhere (compute + destroy + clone), mandated by RC. jank's GC removes the
destroy/clone need but leaves compute-recursion overflow (F1). The convergence's ⟨reclaim⟩ axis predicts
both. The verified-corner clearing engine would inherit crane's iterative discipline for free on the
extracted side; on the jank side, `loopify` supplies the one missing piece.
