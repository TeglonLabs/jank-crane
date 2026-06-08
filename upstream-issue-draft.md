# Ready-to-file upstream issue drafts (NOT yet filed — awaiting go/no-go)

Target: github.com/jank-lang/jank · reporter identity: `bmorphism`.
File only on explicit instruction. Built from `jank-0.1-alpha` (nix `.#jank-release`).

---

## DRAFT A — ★ silent 32-bit overflow in `small_integer` arithmetic (high value)

**Title:** `small_integer` arithmetic silently wraps at 32 bits (no promotion to `integer`)

**Body:**
Ordinary integer arithmetic on values that fit in `small_integer` silently wraps at signed 32 bits and
does not promote to `integer`, producing wrong (often negative) results. Clojure has no i32/i64 split,
so all of these are correct there.

```clojure
(type 100000)             ;=> small_integer
(type 5000050000)         ;=> integer
(type (* 100000 100000))  ;=> small_integer        ; product not promoted
(* 100000 100000)         ;=> 1410065408           ; expected 10000000000
(* 1000000 1000000)       ;=> -727379968           ; expected 1000000000000  (sign flip)
(* 2147483647 2)          ;=> -2                    ; expected 4294967294
(+ 2000000000 2000000000) ;=> -294967296            ; expected 4000000000      (sign flip)
(+ 5000050000 0)          ;=> 5000050000            ; i64 path is correct
```

**Expected:** Clojure semantics — integer ops behave as i64 (`(* 1000000 1000000)` => 1000000000000),
overflowing only at the i64 boundary (where Clojure throws on `*`).
**Actual:** `small_integer` (i32) operands keep `small_integer` results and wrap at signed 32 bits.
**Root cause (likely):** the runtime's `promoting_mul`/`promoting_add` (`core/math.cpp`) already do this
correctly via `__builtin_mul_overflow` + promotion to `big_integer` — but `*`/`+` on two `small_integer`
operands don't reach that path (a fast-path / op-binding routes around it). Confirmed it's not constant
folding: `(apply * [100000 100000])` also yields `1410065408`. (`unchecked-multiply` is unimplemented —
`(throw "TODO …")` — so this isn't an opted-into unchecked op.)
**Impact:** silent data corruption on routine arithmetic; sign flips. Distinct from #792 (bigint parsing).
**Env:** jank-0.1-alpha, aarch64-darwin, built via the project flake (`.#jank-release`).

---

## DRAFT B — non-tail recursion segfault  [DEMOTED by −1 audit: NOT a bug → enhancement only]

> Counterfactual audit verdict: native-compiled languages segfault on stack exhaustion (C/C++/Rust do;
> the JVM is the outlier with catchable SOE). Clojure also can't do unbounded non-tail recursion. So this
> is **expected native behavior, not a correctness bug.** Do NOT file as a bug. If anything, it motivates
> an *opt-in enhancement* (loopify, non-tail→iteration) under the Optimization epic #98 — and/or a
> recursion-depth guard that throws instead of SIGSEGV. Kept below only as enhancement context.

**Title (enhancement, optional):** Optionally convert deep non-tail self-recursion to iteration (#98)

**Context:**
```clojure
(defn s [n] (if (zero? n) 0 (+ n (s (dec n)))))
(s 500)   ;=> 125250   (ok)
(s 800)   ;=> Segmentation fault: 11
```
Tail `recur` at 1e6 is fine, so this is specific to non-tail recursion. On the JVM, Clojure handles far
deeper and throws a **catchable** `StackOverflowError`; jank `SIGSEGV`s (uncatchable) at ~500–800.
**Ask:** at minimum, convert the native-stack exhaustion into a recoverable error (a recursion-depth
guard) rather than a hard crash. (A general non-tail→iteration transform — "loopify" — is one mitigation
we're prototyping, but the crash-vs-throw gap is the bug.)
**Env:** jank-0.1-alpha, aarch64-darwin.

---

## DRAFT C — lexer errors on valid UTF-8 inside a line comment (minor)

**Title:** Lexer rejects valid UTF-8 (e.g. U+2014 em-dash) inside a `;` line comment

**Body:**
```clojure
(println 1)  ; plain ascii — fine
(println 2)  ; em dash —            ; <- errors: lex/invalid-unicode "Unfinished character"
```
A line comment containing a valid multibyte UTF-8 char (em-dash `—`, bytes `e2 80 94`) raises
`lex/invalid-unicode "Unfinished character"`. ASCII-only comments are fine. Comments should be opaque to
end-of-line regardless of byte content. Likely related to #634.
**Env:** jank-0.1-alpha, aarch64-darwin.
