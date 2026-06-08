# All users of crane (bloomberg/crane) — enumeration

crane is young (created 2025-11-22) and niche (Rocq→C++ extraction): **144★ · 9 forks · 4 watchers**.
"Users" splits into actual code-users, forks, stargazers (interest), and mentions. Via `gh` (forks API +
code search for `Crane Extraction` / `Require Crane.Mapping` + stargazers).

## 1. Maintainers, dogfooding (Bloomberg)
- **bloomberg/crane** — canonical. Maintainers Joomy Korkut (**joom**) + Matthew Weaver (**mweaver89**).
- joom uses his own tool to extract real programs (the strongest "user" signal — dogfooding):
  - **joom/rocq-crane-sdl2** — SDL2 bindings via crane.
  - **joom/rocqsweeper** — Minesweeper, Rocq → C++ via crane.
  - **joom/rocqman** — Pac-Man, Rocq → C++ via crane.

## 2. External applications that USE crane extraction (real users)
- **CharlesCNorton/rocqsheet** — a verified spreadsheet extracted via crane.
- **CharlesCNorton/jsonpath-verified** — verified JSONPath, crane-extracted. (also a forker.)
- **Carnot-EBM/carnot-ebm** — a real-world (energy/EBM) project invoking Crane Extraction.
- **jessding/crane** (+ jessding/crane-fork) — fork with actual usage.
(CharlesCNorton is the most active *external* user; joom is the most active overall.)

## 3. Forks (9) — the fork-level user set
```
TeglonLabs/crane     ← ours (this session)        plurigrid/u-crane   ← your org's fork ("u-crane")
CharlesCNorton/crane (active user, above)          jessding/crane-fork (active user)
laelath/crane   rob-baron/crane   saviorand/crane   bagnalla/crane (= Alex Bagnall, Coq researcher)   miclill/crane
```

## 4. Stargazers (144) — the interest community = PL / formal-methods researchers
sample: yiyunliu, ngernest (Ernest Ng), alpaylan (Alperen Keles), sredman, javathunderman — i.e. the
Coq/Rocq + property-based-testing + PL research crowd. Stars = attention, not use.

## 5. Adjacent / mentions (not users, but reference it)
- **cisco-ai-defense/aibom** — references crane (likely an AI-BOM / dependency listing).
- **wklm/crane_blog**, **wklm/wklm.github.io** — blog/writeup about crane.
- **wahidyankf/ose-public** — appears in a curated list.

## Summary
**Actual code-users (~7):** bloomberg/crane (joom: sdl2, rocqsweeper, rocqman), CharlesCNorton (rocqsheet,
jsonpath-verified), Carnot-EBM, jessding. **Forks (9)** incl. ours (TeglonLabs) and plurigrid/u-crane.
**Stargazers (144)** = the PL/formal-methods community. The center of gravity is the maintainer (joom)
dogfooding it for games + Charles Norton building verified apps; everyone else is a fork or a watcher.
crane's user base is small, identifiable, and research-flavored — a brand-new verified-extraction niche.
