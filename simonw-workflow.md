# How simonw would set up these workflows better to succeed  (living doc, /loop)

Grounded in Simon Willison's **Agentic Engineering Patterns** guide (simonwillison.net, Feb 2026) —
his actual 13 patterns — applied to *our* workflow: the +/−/? subagent tree, the /loop dissection,
the crane↔jank convergence, and the loopify non-cascade proof.

Status: ITER 1 — findings + concrete restructure. Verdict at bottom.

## simonw's 13 patterns → what we did → what he'd change

| # | pattern | our current practice | simonw's better move |
|---|---|---|---|
| 1 | What is agentic engineering | agents generate+execute in a loop | ✅ aligned (that's /loop) |
| 2 | **Writing code is cheap now** | we *argued* non-cascade in prose (loopify-spec L5) | **stop arguing — write the code & run it**; a green test beats five lemmas |
| 3 | **Hoard things you know how to do** | q.md / plus.md / minus.md / loopify-spec.md | ✅ strongly aligned; sharpen → store as `files-to-prompt`-able examples a future agent ingests verbatim |
| 4 | AI should produce better code | read-only study, no code yet | turn the spec into a real pass + keep tech-debt out |
| 5 | **Anti-patterns: don't push unreviewed** | /loop self-paces autonomously | keep a **human-review gate per diff**; never auto-land |
| 6 | How coding agents work (token cache) | ScheduleWakeup tuned to 5-min cache window | ✅ aligned (cache-aware delays) |
| 7 | **Using Git with coding agents** | **0 commits — pure read-only, no checkpoints** | **commit per /loop iteration** → linear reviewable, revertible history |
| 8 | **Subagents** | +/−/? parallel tree | ✅ he endorses this; but make roots emit **patches/diffs**, not prose, when the goal is code |
| 9 | **Red/green TDD** | differential test is `TODO(iter2)` | **write the FAILING test first** (deep-recursion jank fn that overflows today) → implement loopify to green |
| 10 | **First run the tests** | never ran jank's suite | run `clojure-test-suite` baseline BEFORE touching anything |
| 11 | Agentic manual testing | n/a (no UI) | the analog: actually *execute* loopified output via `jit::eval_string`, observe |
| 12 | **Linear walkthroughs** | abstract opcode tables | walk ONE concrete example end-to-end: a specific recursive jank `defn` → loopified IR → emitted C++ |
| 13 | Interactive explanations | static markdown | make the example runnable/steppable |

## The deeper tooling gap (his core kit: files-to-prompt + llm + sqlite)

Our subagents **re-discovered the same files twice** (the `−` root emitted 3×, re-grepping
`instruction.hpp` each time). simonw's fix is deterministic, logged, cacheable context assembly:

```
files-to-prompt c/crane/src/loopify.ml c/crane/src/minicpp.mli \
                j/jank/compiler+runtime/include/cpp/jank/ir/instruction.hpp \
                j/jank/compiler+runtime/src/cpp/jank/codegen/cpp_processor.cpp \
  | llm --schema-multi 'stage: str, file: str, line: int, role: str' \
        -s 'extract the loopify-relevant pipeline stages' 
# every prompt+response auto-logged to SQLite; inspect with `llm logs -n 1` / datasette
```

vs. opaque subagents groping via grep: **reproducible, inspectable, free to re-run, no double work.**
The maps (plus/minus) become `llm --schema` JSON we can diff and machine-check, not prose to re-read.

## VERDICT — the single highest-leverage change

**Convert the loopify non-cascade proof from prose (5 lemmas + `TODO(differential test)`) into a
red/green test suite, and run jank's existing suite as the baseline FIRST.**

simonw's whole guide collapses to: *writing code is cheap, so don't argue about behavior — encode it
as a test and run it.* Our two loops converge here:
- loopify-spec.md L5 ("equivalence obligations") **succeeds when its differential harness is green**,
  not when its prose is complete.
- The non-cascade claim becomes: `clojure-test-suite` is green before AND after the pass (First run
  the tests #10 + Red/green #9), plus a property test asserting `f(x) == loopify(f)(x)` over a corpus.

Concretely, next iteration (advances BOTH loops):
1. `git init`/commit the jc-converge dir so each iteration is a reviewable diff (#7).
2. Locate + write the **failing** test: a deep non-tail recursive jank program that overflows today
   (the red). Cite jank test dir.
3. Sketch the differential property harness (`f` vs `loopify(f)`) as the executable form of L5.
4. Reduce the +/−/? roots' next run to emit a **patch**, not a map (#8).

## Sources
- https://simonwillison.net/guides/agentic-engineering-patterns/
- https://simonwillison.net/2026/Feb/23/agentic-engineering-patterns/
- https://simonwillison.net/2025/Jun/29/agentic-coding/
- https://simonwillison.net/2025/May/27/llm-tools/
- https://simonwillison.net/2024/Apr/8/files-to-prompt/
- https://simonwillison.net/2024/Jun/17/cli-language-models/
