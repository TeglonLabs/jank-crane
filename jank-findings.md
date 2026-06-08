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
- **Distinct from #792** (that's `bigint "42N"` parse behavior). Not matched by #620/#311/#634.
  Appears unreported. Root cause unknown (codegen of `*`/`range`/loop accumulator at i32?) — reported
  as observed behavior + minimal repro, for maintainers to diagnose.

## Finding 3 — lexer rejects non-ASCII in a comment (minor, lower confidence)
```clojure
(println 1)   ; em-dash —  <- this comment triggers: error lex/invalid-unicode "Unfinished character"
```
- A U+2014 in a `;` comment produced `lex/invalid-unicode / Unfinished character`. Comments should be
  opaque to the lexer. May relate to #634 ("[clojure-test-suite] Lexer issues"). Low confidence (could
  be file-encoding); needs a clean ASCII-vs-UTF8 repro before filing.

## Upstream value (honest)
- **Finding 2 is the prize** — a silent integer-correctness divergence from Clojure, unreported,
  3-line repro. Maintainer (jeaye) explicitly invites bug reports; pre-alpha; high value, low controversy.
- **Finding 1** — solid bug (segfault vs catchable error, shallow threshold); loopify is one mitigation.
- **Finding 3** — verify encoding first; file only if it reproduces from a clean ASCII source + UTF-8 comment.
- Nothing filed yet — these are repros ready for your go/no-go under the `bmorphism` identity.
