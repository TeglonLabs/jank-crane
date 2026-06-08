# Estimate: jank GitHub → Codeberg migration  (gh survey, 2026-06-08)

## jank project snapshot (gh)
- Issues: **91 open / 228 closed** (~319 total) — issue tracker is the dev backbone.
- Open-issue mix (labels): **compiler 41**, help-wanted 24, runtime 10, tooling 7, distribution 4,
  clang 3, clojure-test-suite 3, codegen:llvm-ir 3, needs:research 3, blocked 2.
  ⇒ the team is heads-down **building the compiler** + chasing Clojure compatibility
  (many `[clojure-test-suite]` issues #633–638), not touching forge/infra.
- Recent activity through 2026-06-05 (REPL state #801, bigint #792, nREPL #782, Windows #713 …).
- CI: **4 GitHub Actions workflows** (build-book, build-compiler+runtime, build-win, package-check).
- Codeberg/GitLab/mirror mentions in issues + README: **zero**.

## Estimate — jank UPSTREAM moving to Codeberg
**Probability: ~near-zero (this year).** Grounds:
1. **No signal** — not mentioned in any issue, the README, or discussions.
2. **Deep GitHub coupling** — 319 issues as the workflow spine; 4 Actions pipelines; the
   `clojure-test-suite` is a *separate* GitHub repo the test harness pulls; homebrew taps; the Nix
   flake inputs are `github:` refs. The whole apparatus is GitHub-native.
3. **Wrong phase** — pre-1.0, 41 open compiler issues; infra migration is pure yak-shaving with zero
   feature value right now. No rational maintainer would spend the weeks.

**If forced, the cost** (rough): issues import via Forgejo's GitHub migrator (automated, hours) +
port 4 Actions → Woodpecker/Forgejo Actions (days) + rewrite `github:` flake inputs + redirect
homebrew/docs (days) + the *real* cost: moving a community's attention (social, not technical, weeks→months).
Net: 1–2 weeks technical, open-ended social. Verdict: **won't happen barring an external forcing event**
(GitHub policy/cost shock, ICE-style deplatforming, or a values-driven maintainer decision).

## Estimate — YOUR TeglonLabs convergence work to Codeberg
This is the actionable reading, and it's cheap — but currently **un-tooled**:
- `c/crane/CODEBERG-DISTRIBUTION.md` and `codeberg-push.sh` are **empty stubs**; no `codeberg` git
  remote; no Forgejo CLI (`tea`/`berg`) installed. So it's greenfield, not a resume.
- `jank-crane` hub is small (docs + model + 2 submodules) → mirroring is trivial:
  `git remote add codeberg https://codeberg.org/<org>/jank-crane.git && git push codeberg main`.
- CI port: the one `.github/workflows/loopify-model.yml` → a `.woodpecker.yml` (you already template
  Woodpecker at `~/worlds/.woodpecker.yml.template`). ~1 file.
- The `vendor/` submodules point at `github.com/TeglonLabs/{jank,crane}` — leave on GitHub, or also
  mirror; the loopify fork is the only one with original work.
- Effort: **hours**, not weeks. Forgejo supports issues/PRs/CI, so it's a real home, not just a mirror.

## Recommendation
- Don't expect jank itself to move — track it, contribute via GitHub where the maintainers are.
- If sovereignty matters for *your* work: mirror `jank-crane` to Codeberg now (cheap), keep GitHub as
  the contribution surface to upstream. Dual-home, don't migrate. I can scaffold the remote + a
  `.woodpecker.yml` on request.
