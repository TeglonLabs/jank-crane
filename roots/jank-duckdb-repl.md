# jank REPL outer loop + DuckDB primitive integration

trit 0 / witness note. Scope: use the existing jank REPL/C++ interop surface as
the live outer loop, then tighten the DuckDB boundary until it becomes a real
primitive in jank's analyzer/IR/dev tooling rather than a string-returning shim.

## Local facts

Current prototype:

- `w/src/jank/duckdb_plugin.jank` includes the C++ shim with `cpp/raw`.
- It exposes `transpile`, `query`, `get-entropy`, subscription/fanout helpers,
  and randomized routing tests.
- `w/src/cpp/duckdb_shim.cpp` tokenizes and parses S-expressions iteratively,
  resolves `->` and `->>` threading, transpiles a SQL DSL, shells out to a
  DuckDB binary, and returns JSON/markdown/csv strings.
- The shim stores string results in one global `g_result_holder`.
- Errors are currently plain string/JSON fragments such as `{"error": ...}`.

jank properties which matter:

- The REPL is also a live C++ environment. `cpp/raw` introduces global C++
  declarations into the Clang/JIT session, and `cpp/box` can keep a native
  pointer alive across later evals.
- The C++ DSL is type-aware. `cpp/new`, `cpp/unbox`, `cpp/cast`, `#cpp`, and
  member calls are analyzed with Clang types, not treated as opaque text.
- jank IR already has first-class C++ interop instructions:
  `cpp_raw`, `cpp_value`, `cpp_into_object`, `cpp_from_object`, `cpp_call`,
  `cpp_constructor_call`, `cpp_member_call`, `cpp_member_access`,
  `cpp_builtin_operator_call`, `cpp_box`, `cpp_unbox`, `cpp_new`, `cpp_delete`.
- jank errors already carry `kind`, `message`, `source`, `notes`, optional
  `cause`, and optional trace. The nREPL `safe_eval` path currently catches
  `error_ref` but stores only `e->message` in `*e`.
- jank's IR is Clojure-semantic SSA, lowered to textual C++ and then Clang/LLVM
  JIT. A DuckDB primitive should meet this IR before C++ codegen when the query
  shape is static enough to analyze.

## Thesis

The right outer loop is:

```text
Emacs/CIDER or terminal nREPL
  -> jank forms
  -> analyzer/IR sees DuckDB query intent
  -> C++ DuckDB session executes through a live native object
  -> results/errors/plans return as structured jank values
```

The current prototype is a good bootstrap because it proves the live path:

```text
jank form -> cpp/raw shim -> SQL string -> duckdb CLI -> JSON string
```

But the next integration should avoid hardening that bootstrap into the final
architecture. The important object is not "a SQL string"; it is "a query
intent with source location, lowered SQL, DuckDB plan, result schema, result
materialization policy, and a native execution handle".

## Integration ladder

### Phase 0: stabilize the shim contract

Keep the current `cpp/raw` include and the Jank wrappers, but replace ad hoc
error strings with a uniform envelope:

```clojure
{:ok? true
 :phase :duckdb/execute
 :sql "select ..."
 :columns [...]
 :rows [...]
 :elapsed-ms 4.2}
```

or

```clojure
{:ok? false
 :phase :duckdb/transpile
 :kind :duckdb/bad-dsl
 :message "..."
 :form "(select ...)"
 :source {...}
 :sql nil
 :duckdb-message nil}
```

Even if the boundary still returns JSON for a minute, the semantic contract
should be a result/error envelope. That prevents later phases from inheriting
`{"error": ...}` as an API.

### Phase 1: make DuckDB a live native session

Introduce a small C++ object, conceptually:

```cpp
struct jank_duckdb_session
{
  std::string path;
  // DuckDB database/connection handles live here.
  // Prepared statement cache and extension state live here too.
};
```

Use jank's existing native box mechanics:

```clojure
(def conn (cpp/box (cpp/new cpp/jank_duckdb_session db-path)))
(duckdb/query conn '(select [:a] (from :t) (limit 10)))
```

This is exactly what the jank C++ REPL docs demonstrate with a mutable native
world: a C++ object survives across REPL evals while jank supplies ordinary
Clojure wrappers. DuckDB benefits more than the toy world because connection
state, prepared statements, catalog cache, loaded extensions, and profiling
settings all want to be session-level state.

Phase 1 removes the `popen`/shell path from the hot query path. The CLI can
remain as a debugging fallback, not the primary primitive.

### Phase 2: return structured jank values

Current result shape is mostly "JSON string". Better shapes:

- Small result sets: persistent vectors of persistent maps.
- Wide/tabular results: a `duckdb/result` object with lazy row access,
  schema metadata, preview rendering, and explicit release semantics if needed.
- Large/columnar results: Arrow/C data interface or a native boxed result
  object with jank sequence views.

The REPL needs two renderings:

- human preview: table, row count, timing, plan summary
- data value: ordinary jank object usable by later forms

Do not force every query into eager nested maps. The REPL can preview a large
result without materializing the whole thing into boxed Clojure values.

### Phase 3: add an analyzer/IR-recognized primitive

Start by recognizing a small surface:

```clojure
(duckdb/query conn
  '(select [:table_uuid :table_name]
     (from :ducklake_table)
     (limit 3)))
```

When the DSL form is literal, the analyzer can lower it early:

```text
source form
  -> duckdb DSL AST with source spans
  -> SQL + expected schema request
  -> IR node or typed cpp_call bundle
```

There are two implementation levels:

1. Low-risk: lower to existing `cpp_*` IR instructions that call the native
   session methods.
2. Deep primitive: add `duckdb_query`, `duckdb_explain`, and maybe
   `duckdb_relation` IR instructions once enough behavior is stable.

The low-risk level is probably sufficient until optimization pressure appears.
jank's existing C++ interop IR already preserves enough type information for a
native connection call and a boxed/lazy result object. A dedicated DuckDB IR
node becomes worthwhile when jank wants compile-time SQL validation, constant
query caching, cross-form plan display, or query/result fusion.

### Phase 4: make the REPL the database workbench

The outer loop should be nREPL-visible:

- `duckdb.transpile`: form -> SQL
- `duckdb.explain`: form -> SQL + DuckDB logical/physical plan
- `duckdb.profile`: eval with timings and counters
- `duckdb.catalog`: tables, columns, extensions, attached databases
- `duckdb.preview`: bounded result preview
- `duckdb.ir`: show jank IR around the query call

For interactive development, every query eval can carry a trace:

```clojure
{:form ...
 :jank-phase :analyze
 :duckdb-phase :execute
 :sql ...
 :plan ...
 :schema ...
 :preview ...
 :elapsed-ms ...
 :error nil}
```

That is the useful "outer loop": edit a form, eval it, see the lowered SQL,
see the plan, see the result preview, keep the native session alive, then
refine. The REPL is not just a transport; it is the debugger for the language
boundary.

## Error design

Do not collapse DuckDB failures into result strings. Map them onto jank's
phaseful error model:

| Phase | Error kind | Carries |
|---|---|---|
| read/parse | `parse/invalid-*` or `duckdb/invalid-dsl` | source span, offending form |
| analyze | `analyze/invalid-duckdb-query` | query form, static type/schema expectation |
| lower | `duckdb/transpile-error` | DSL AST, SQL fragment if any |
| bind | `duckdb/bind-error` | SQL, catalog/schema facts |
| execute | `duckdb/execution-error` | SQL, DuckDB message, query id |
| materialize | `duckdb/materialization-error` | column, target jank type |
| resource | `duckdb/resource-error` | session id, path, connection state |

jank already has the internal shape for this: `kind`, `message`, `source`,
`notes`, `cause`, trace. The REPL work is to keep that structure alive. Today
`safe_eval` catches `error_ref` and stores only the message in `*e`; for this
workflow, nREPL should expose at least `kind`, `message`, `source`, and `notes`.

The source span is crucial. If `(where (> :price "oops"))` fails at DuckDB bind
or execution time, the user should see the SQL and jump back to that form, not
just read a DuckDB exception string.

## IR design

The IR choice should be staged.

First, use existing C++ interop instructions:

```text
literal conn
literal or lifted query DSL
cpp_call jank_duckdb_session::query(...)
cpp_into_object / cpp_box for result wrapping
```

This keeps the integration aligned with jank's current backend: IR -> textual
C++ -> Clang/JIT. It also lets Clang type-check the native handle calls.

Then add a DuckDB-specific IR instruction only if it unlocks real behavior:

- compile-time transpilation for literal DSL
- SQL/schema cache keyed by source and database catalog version
- planner introspection in nREPL
- result materialization policy chosen by analyzer
- query fusion with downstream jank transformations

A good dedicated instruction would not simply mean "call this SQL string". It
would represent:

```text
duckdb_query {
  connection: SSA id
  dsl_ast: source-preserving query tree
  sql: optional lowered SQL
  result_policy: preview | eager_maps | lazy_rows | arrow
  location: read::source
}
```

That location field is not decoration. It is what connects DuckDB failures back
to the editor and to jank's error reporter.

## Interactive development pattern

The everyday workflow should look like this:

```clojure
(require '[duckdb.repl :as d])

(def conn (d/open "/Users/dietrich/worlds/w/eirobri.ducklake"))

(d/explain conn
  '(select [:table_uuid :table_name]
     (from :ducklake_table)
     (limit 3)))

(def q
  '(-> (select [:topic :trit] (from :events))
       (where (= :topic "gf3"))
       (limit 20)))

(d/preview conn q)
(d/query conn q)
```

The native C++ session remains alive. The query form remains editable data.
The SQL and plan are visible. Errors report both DuckDB context and source
spans. That is the deep integration point.

## Validator conclusion

The current shim is a valid seed, not the final primitive. The best next step is
not to add more string helpers; it is to make the native DuckDB session a boxed
C++ object in the jank REPL and return structured envelopes. After that, route
literal query forms through analyzer-visible lowering, initially via existing
`cpp_*` IR opcodes. Promote to dedicated DuckDB IR only when the REPL needs
compile-time query validation, plan display, source-mapped errors, or query
fusion.

In short:

```text
cpp/raw bridge -> boxed DuckDB session -> structured jank results/errors
  -> analyzer-visible query lowering -> optional DuckDB IR intrinsic
```

Keep the REPL as the outer loop the whole time. It is the thing jank uniquely
has here: a live Clojure surface, a live C++ surface, typed interop, sourceful
errors, and an SSA IR close enough to the language semantics to make a database
query feel like a first-class form instead of a string sent through a pipe.
