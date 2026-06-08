# Adding ewig to jank-crane — transients as the fast-clearing primitive

**ewig** = arximboldi's value-oriented text editor, the canonical **immer** demo (by immer's author):
state is ONE persistent value; edits make new values via structural sharing; **batch edits use
transients** (`to_transient`/`persistent`) for O(1) amortized mutation; **undo/redo is free** (keep old
values). Adding "ewig to jank-crane" = adopt that architecture for a **clearing/trading engine**, with
transients as the hot path. The three "transient" senses fuse here:
- **immer transient** — fast in-place batch mutation of a persistent structure.
- **clearing transient** — the epoch where the book/obligation-graph is uniquely owned and netted
  in place (`use_count()==1` ⇒ no copy ⇒ the no-spread region; η iso in the Melliès clearing round-trip).
- **Emacs transient** — causal-jank's `C-o` menu (Plurigrid convention) driving it live.

## The map: ewig editor → clearing engine
| ewig | clearing engine |
|---|---|
| document = `immer::vector<line>` | ledger / order book = `immer::map<party, position>` + `immer::map<price, vector<order>>` |
| keystroke/edit | an order / obligation (an *intent*) |
| transient batch (a burst of edits) | a **clearing epoch** (a burst of intents netted together) |
| `persistent()` freeze | the **settled snapshot** `L_{t+1}` (a ledger entry / audit witness) |
| undo history (old values) | the **snapshot history** `{L_t}` = replay / audit trail (free, structural sharing) |
| — | H¹ over the snapshot sheaf = **residual arbitrage** (rung3_exact); netting = the dual (rung5) |

## The clearing round IS a transient lifecycle
```
L_t                         ; persistent settled snapshot at epoch t (immutable witness, 0/Witness)
tx = L_t.transient()        ; O(1), shares structure                    (+1 Play: open the epoch)
for ev in burst:            ; FAST PATH — apply intents in place, no per-op alloc
  apply!(tx, ev)            ;   when uniquely owned: in-place (crane use_count()==1 ≡ immer transient)
net!(tx, clearing_groups)   ; rung5: transitive-closure → WCC → multilateral net to Σ≡0  (−1 Coplay: verify)
L_{t+1} = tx.persistent()   ; O(1) freeze → new snapshot, shares with L_t
audit(L_t, L_{t+1})         ; Σ net ≡ 0 (conservation) ; H¹ = leftover arbitrage
```

## "A number of transients" = one per clearing group, in parallel
rung5's clearing groups are the **weakly-connected components** of the owes-graph — and WCCs net
**independently**. immer transients are single-thread-owned (the SPI invariant: a replica stream is a pure
fn of (master, shard) — see gay-pigeons-splitmix reconciliation), so the natural design is **one transient
per WCC**, run in parallel, each netting in place, **all frozen into one persistent ledger** at the epoch
boundary. The transient *count* = the shard count = the parallelism. (Sharing transients across threads is
the one thing immer forbids — so shard, don't share.)

## Who provides what (the convergence, cashed out)
- **crane** → the **verified netting core**: transitive closure + multilateral net + the conservation law
  `Σ net ≡ 0`, extracted from a Coq/Rocq proof → C++. This is exactly the kind of invariant crane proves.
- **immer (jank)** → the **fast transient** substrate + value-history (ewig's architecture).
  REQUIRES the borrow-ledger seam **immer→crane** (`persistent_array.h` → immer `flex_vector`): crane's
  COW array is O(n) on shared mutation; only immer gives O(log n) transients. So this design *is* the
  jank→crane borrow #1, realized.
- **jank** → **live**: nREPL + causal-jank Emacs transient menu = trade/clear interactively, replay
  snapshots, watch Σ and H¹ live (ties steel-geiser-emacs.md: the −/?/+ tiles over the clearing loop).

## ★ Honest blocker (post-counterfactual): F2 makes this UNSAFE today
A clearing engine's whole point is **conservation** (Σ net ≡ 0 — no value created or destroyed). jank's
**`small_integer` i32 silent wrap (Finding F2)** would silently violate it: `(* 1000000 1000000)` →
`-727379968` means a netting computation can fabricate or destroy money with no error. **You cannot build
this on jank's default integers until F2 is fixed** (or you force `big_integer`/exact `Ratio` everywhere,
as rung5 does with `Rational{Int}`). rung5's "exact rational, no float drift" discipline is non-negotiable
here. ⇒ **F2 isn't just a bug to file — it's the gating dependency for the ewig-clearing goal.**

## Honest status
- **Real now**: the architecture (ewig pattern is proven; immer transients are real; rung5 netting exists
  in Julia; the transient-lifecycle = clearing-round mapping is sound).
- **Needs building**: the immer→crane seam (verified core gets fast transients); the jank port of rung5
  (TC + WCC + net) on `big_integer`/Ratio; the nREPL/Emacs live loop.
- **Gated on**: F2 fixed (or exact-arithmetic-only). Without conservation safety, "fast" is worthless.
- **Why ewig specifically**: it's the existence proof that a mutation-heavy interactive app runs on immer
  values + transients with a free immutable history — which is structurally a clearing engine (hot
  mutation + immutable audited ledger). Don't reinvent the architecture; port ewig's.
