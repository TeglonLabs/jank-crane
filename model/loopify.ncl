# loopify.ncl — Tweag: typed, contract-checked config that DRIVES the model test.
# The contract encodes the invariants the test depends on; `nickel export` fails
# (loudly) if any are violated — so the harness cannot be silently misconfigured.
#
#   nickel export --format json model/loopify.ncl > model/loopify_params.json
#
let Params = {
  stack_limit       | Number,   # modelled native-frame budget
  safe_n            | Number,   # max input used by equivalence cases
  equivalence_cases | Number,   # number of property-based differential trials
  seed              | Number,   # SplitMix64 seed -> reproducible corpus
  deep              | Number,   # input for the red/green overflow demonstration
}
in
# Cross-field invariants (this is exactly the bug class that bit the first run):
let Invariants = std.contract.from_predicate (fun p =>
      p.safe_n < p.stack_limit        # equivalence inputs must stay UNDER the budget,
                                       #   else the differential check spuriously overflows
   && p.deep > p.stack_limit          # the red case MUST exceed the budget (real red)
   && p.equivalence_cases > 0
   && p.seed >= 0)
in
({
  stack_limit       = 500,
  safe_n            = 300,
  equivalence_cases = 300,
  seed              = 1069,
  deep              = 500000,
} | Params | Invariants)
