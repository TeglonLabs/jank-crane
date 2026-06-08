# Negative test: violates safe_n < stack_limit. `nickel export` MUST fail.
let Invariants = std.contract.from_predicate (fun p => p.safe_n < p.stack_limit)
in ({ stack_limit = 500, safe_n = 600, equivalence_cases = 300, seed = 1069, deep = 500000 } | Invariants)
