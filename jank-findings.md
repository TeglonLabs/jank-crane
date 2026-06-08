# jank-0.1-alpha — findings from exercising the built binary (2026-06-08)

Binary: `nix build .#jank-release` of TeglonLabs/jank @ loopify-pass (== upstream jank + loopify
scaffold; the scaffold is default-off and not implicated). Run via `jank run FILE`. All reproduced.

## Finding 1 — non-tail self-recursion SIGSEGVs at shallow depth (loud crash)
```clojure
(defn s [n] (if (zero? n) 0 (+ n (s (dec n)))))   ; non-tail: (+ n _) wraps the call
(println (s 500))    ; => 125250   rc=0   OK
(println (s 800))    ; => Segmentation fault: 11   rc=139
```
- Threshold is between **500 and 800** native frames. Clojure-on-JVM handles ~10k+ then throws a
  **catchable `StackOverflowError`**; jank **`SIGSEGV`s** (uncatchable hard crash) at ~500–800.
- Two defects in one: (a) crashes far shallower than the JVM, (b) crashes instead of throwing a
  recoverable error. Tail `recur` is unaffected (see below) — so this is specifically non-tail recursion.
- This is exactly what `ir/opt/loopify` targets. Not filed upstream; #348 is a separate lazy-cat bug.

## Finding 2 — SILENT 32-bit integer overflow in arithmetic (wrong answer)  ★ most severe
```clojure
(println 5000050000)             ; => 5000050000        OK  (i64 literal)
(println (+ 5000050000 1))       ; => 5000050001        OK  (i64 add)
(println (* 100000 100000))      ; => 1410065408   WRONG (10000000000 mod 2^32); want 10000000000
(println (reduce + (range 100001))) ; => 705082704  WRONG (5000050000 mod 2^32); want 5000050000
(defn st [n a] (if (zero? n) a (recur (dec n) (+ n a))))
(println (st 100000 0))          ; => 705082704    WRONG; want 5000050000
```
- `*` and small-int accumulation **truncate to 32 bits**, while i64 literals and literal-i64 `+` are fine.
  Clojure longs are i64 (`(* 100000 100000)` = 10000000000). **Silent wrong answer** — worse than a crash.
### ROOT CAUSE (hardened — confirmed via `type`)
jank has two integer types, `small_integer` (signed **i32**) and `integer` (**i64**). Literals are typed
by magnitude; **`small_integer` arithmetic wraps at signed 32 bits and never promotes to `integer`.**
```clojure
(type 100000)             ; => small_integer        (literal ≤ i32)
(type 5000050000)         ; => integer              (bigger literal)
(type (* 100000 100000))  ; => small_integer        (product stays i32 — no promotion)
(* 100000 100000)         ; => 1410065408           (want 10000000000)
(* 1000000 1000000)       ; => -727379968           (want 10^12   — SIGN FLIP)
(* 2147483647 2)          ; => -2                   (want 4294967294)
(+ 2000000000 2000000000) ; => -294967296           (want 4000000000 — SIGN FLIP)
(+ 5000050000 0)          ; => 5000050000           (i64 path, correct)
```
Ordinary expressions like `(* 1000000 1000000)` silently produce wrong, often **negative**, results.
Clojure has no i32/i64 split (all Long/i64), so all of these are correct there.
- **Distinct from #792** (that's `bigint "42N"` parse behavior). Not matched by #620/#311/#634. Unreported.
- The fix space is clear: either promote `small_integer`→`integer` on overflow (Clojure-like), or make
  the arithmetic ops always i64 (drop the i32 fast path). A maintainer can pick; the repro pins the cause.

## Finding 3 — lexer errors on valid UTF-8 in a line comment (CONFIRMED)
```clojure
(println 1)  ; plain ascii comment              => prints 1, rc=0   OK
(println 2)  ; comment with an em dash —         => prints 2, THEN  error: lex/invalid-unicode
             ;                                        "Unfinished character"
```
- The em-dash is **`e2 80 94`** = valid UTF-8 for U+2014 (verified with `xxd`). ASCII-only control passes;
  the UTF-8 comment errors → **confirmed lexer bug**, not a file-encoding artifact. The lexer's
  comment-skip reads bytes and mis-handles the high bytes (reports an "Unfinished character" literal).
  Comments must be opaque to end-of-line. Likely related to #634 ("[clojure-test-suite] Lexer issues").

## Finding 4 — string `count`/index is UTF-8 BYTES, not chars (CONFIRMED)
```clojure
(count "abc")   ; => 3   (ASCII, matches Clojure)
(count "é")     ; => 2   (Clojure: 1 ; é = U+00E9 = 2 UTF-8 bytes)
(count "—")     ; => 3   (Clojure: 1 ; em-dash = 3 bytes)
(count "café")  ; => 5   (Clojure: 4)
(subs "café" 0 3) ; => "caf"  (byte indexing)
```
jank's string length/indexing operates on UTF-8 **bytes**, not Unicode code points; Clojure strings are
UTF-16 char sequences (`(count "café")`=4). Silent wrong length/slices for any non-ASCII string.
**Likely the same root as F3** (jank's string model is byte-oriented, not codepoint-oriented) — F3 is the
lexer face, F4 the runtime face. Moderate severity; file together as "Unicode/codepoint string semantics".

## Counterfactual audit (−1: steelman "not a bug", then test)

**F1 — "native languages segfault on stack overflow; this is expected, not a bug." → COUNTERFACTUAL
LARGELY HOLDS.** C/C++/Rust all abort/segfault on native stack exhaustion; Clojure-on-JVM also cannot do
unbounded non-tail recursion (it throws SOE). jank compiles to native, so SIGSEGV on a full native stack
is *expected behavior*, not incorrect. Residuals: (a) catchable-vs-uncatchable is inherent to native
compilation (a feature request — guard pages/signal handler — not a correctness bug); (b) the ~500–800
threshold is shallow, hinting at heavy per-call frames (mild perf concern, not a bug).
⇒ **DOWNGRADE: F1 is not really a bug. loopify is an ENHANCEMENT (issue #98), not a bug fix. Don't file
as a bug.**

**F2 — "`small_integer` (i32) + no-promotion is intended C++ semantics." → COUNTERFACTUAL REFUTED; bug
CONFIRMED and root-localized.** Source evidence that jank *intends* non-wrapping integers:
- `core/math.cpp` `promoting_add/sub/mul` use `__builtin_*_overflow` and promote to `big_integer`
  (math.cpp:57/92/127) — correct by construction.
- `clojure/core.jank` has `*'` (promoting) at :1769; `unchecked-multiply` is `(throw "TODO: port …")`
  at :1886 — i.e. the wrapping op isn't even implemented; you can't have opted into it.
- Yet `(* 100000 100000)`, `(let …)`, AND `(apply * [100000 100000])` ALL give `1410065408` — so plain
  `*` on `small_integer` operands wraps at i32, **bypassing jank's own correct `promoting_mul`**. Clojure
  never wraps at i32 (all i64). ⇒ **CONFIRMED BUG — and stronger than first stated: it's a fast-path /
  binding defect that routes around the runtime's correct promoting arithmetic. THE prize. File it.**

**F3 — "jank is ASCII-only by design." → COUNTERFACTUAL REFUTED.** `(println "a—b")`→`a—b` and `(def π 3)`
work — UTF-8 is fine in strings and symbols, only comments choke. Real (minor) comment-lexer bug.
Secondary: `(count "—")`→`3` (bytes, not chars; Clojure=1) — a separate minor divergence.

## Upstream value (post-audit, honest)
- **File F2** — confirmed, root-localized, unreported, severe (silent sign-flips); maintainer invites it.
- **Do NOT file F1 as a bug** — it's expected native behavior; reframe loopify as an opt-in enhancement
  under #98 if pursued at all.
- **F3 optional** — real but minor; file with the `count` byte-vs-char note or fold into #634.
- Nothing filed yet — your go/no-go under `bmorphism`. The −1 pass changed the recommendation: the honest
  contribution narrowed from "three bugs" to **one strong bug (F2)** + one minor (F3), and an enhancement.
