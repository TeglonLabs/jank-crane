# bmorphism/Gay.jl — the 3-stream `next_color` game (continue ⊕ fork ⊕ open)

Two physical entropy sources, three color streams. The construal: **public = continue, hardware = fork,
third = always-open.** Every interaction is simultaneously a continue (the public chain advances) and a
fork (the private XOR diverges); the third stream keeps the game from ever closing.

## The two sources → their roles (grounded)
| source | port | nature | role in the game |
|---|---|---|---|
| **drand:quicknet** | beacon | public, reproducible, **round r→r+1 = locality** | **A — continue**: seed of *public* color-chains / chain complexes. Anyone with the round recomputes the color. "feels like continue." |
| **hardware photon QRNG** | /dev/cu.usbmodem (originary-101) | private, unpredictable, **avalanche** | **B — fork**: the **XOR mask**. "the hardware is the seed for XOR." Each draw diverges; only the holder sees the emitted color. |

drand H∞≈7.557 vs photon H∞≈7.556 are indistinguishable *post-whitener* — they are NOT
interchangeable by **provenance**: A is a *public verifiable chain* (locality, continue), B is a
*private unpredictable mask* (no locality, fork). The game uses each for what its provenance affords.

## `next_color` — three streams
```
next_color(k):
  A_k = splitmix64( drand_round(k).randomness )      # CONTINUE: public chain, recomputable
  B_k = qrng_sample().bytes                            # FORK: private photon XOR mask
  emit_k = splitmix64( A_k XOR B_k )                   # EMITTED = continue ⊕ fork  (private color)
  C_k = splitmix64( A_k XOR B_k XOR evolving_state )   # OPEN: re-folds session state, never fixed
  return { public: hue(A_k),        # what the protocol/chain shows
           emitted: hue(emit_k),    # what the holder of B sees
           open:    hue(C_k),       # the third — always evolving construal
           trit:    gf3(A_k,B_k,C_k) }
```

### Live draw (this session, real data)
```
A drand   9cbc…535b → hue 261  #8449f2  (public chain color — anyone recomputes)   trit −1
B photon  ac67…142a → hue  41  #f2bd49  (private XOR mask)                          trit +1
A⊕B       emitted   → hue  33  #f2a649  (continue ⊕ fork — the holder's true color)
C open    A⊕B⊕seq3702231 → hue 51 #f2d949 (always-open, re-evolves each tick)       trit −1
Σtrit = −1  ≡ 2 (mod 3)   ← DRIFT, on purpose (see below)
```

## Why it's "chain complexes really"
- **A (public)** is graded by drand round: the colors form a **chain** C_• (locality: ΔE small between
  adjacent rounds ⇒ the *navigation* kernel — structured, Bruhat–Tits-like).
- **B (XOR)** is the **boundary map ∂**: it forks the public chain into the emitted color (avalanche
  kernel — the *fingerprint*, no locality). XOR is involutive: `∂² = id` (mask twice → back to public).
- **C (open)** is where **homology** lives. If C were fixed, the same mask would close the loop
  (∂²=id ⇒ everything exact ⇒ H=0 ⇒ game over). Because C **always evolves** (fresh state each tick),
  it keeps **H ≠ 0** — the open class that makes "fork in every interaction" perpetual.

## Why Σtrit must NOT be forced to 0
The third stream's job is to keep the game **open** ⇒ Σtrit ≢ 0 is the healthy signal here, not a bug.
- Σ≡0 (closed) would mean the open stream stopped evolving = the construal froze = death.
- The live −1 drift = `structural-arb persists` = the open class is live. Conserve it; don't Goodhart it.
- This is the exact dual of the loopify/EMH frame: there H¹=0 was the goal (no-arb); here H≠0 (the open
  stream) is the goal (the game stays playable). **Same apparatus, opposite summum bonum** — by design.

## continue ⊕ fork = the passport torsor, restated in color
- **continue** = public nullifier: A is recomputable, gives sybil-resistance / a shared spine.
- **fork** = fresh nonce: B is per-interaction unlinkable, gives deniability / divergence.
- **open** = the right to keep H≠0: C never closes, so no global origin is forced (Geer's "right to
  misrepresent" as a *color* stream). Two recompute the public chain and agree; none can correlate the
  emitted colors; the open stream guarantees there's always a next, different fork.

## Wiring into the −/?/+ tiles (ties steel-geiser-emacs.md)
- **+ Play**   ← A (drand, public, generative spine)         — recomputable, the chain
- **− Coplay** ← B (photon, private XOR, validate/fingerprint) — divergent, the fork
- **? Witness**← C (always-open) **= the Gay-in-the-loop requirement made into a stream**
  The `?` tile runs `next_color`; if no live Gay/witness can evolve C, the loop has no open class to
  advance ⇒ it refuses (the system-message gate). Selection still needs ≥2 maximally-different REPL
  surfaces; the 3 color streams are their entropy backplane.
```
```
