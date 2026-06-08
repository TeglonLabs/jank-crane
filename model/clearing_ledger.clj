#!/usr/bin/env bb
;; clearing_ledger.clj — ewig-clearing snapshots -> DuckDB HUGEINT ledger (jank_duckdb_repl seed).
;; Generates net positions per epoch (exact, auto-promoting) -> CSV -> runs clearing_ledger.sql,
;; which audits conservation (Σ=0, necessary) AND per-position i32-overflow (sufficient) in SQL.
;; Run: bb model/clearing_ledger.clj   (needs duckdb on PATH)

(require '[clojure.java.shell :refer [sh]] '[clojure.string :as str])

(defn net-positions [obs]            ;; transient batch (the fast clearing epoch), exact arithmetic
  (persistent!
    (reduce (fn [t {:keys [from to amt]}]
              (let [t (assoc! t from (-' (get t from 0) amt))]
                (assoc! t to (+' (get t to 0) amt))))
            (transient {}) obs)))

(def epochs
  {1 [{:from 1 :to 0 :amt 3000000000} {:from 2 :to 0 :amt 3000000000} {:from 3 :to 0 :amt 3000000000}]
   2 [{:from 0 :to 1 :amt 1000000000} {:from 0 :to 2 :amt 1000000000}]
   3 [{:from 3 :to 4 :amt 2000000000} {:from 4 :to 1 :amt 2000000000}]})

(let [here (-> (or *file* "model/clearing_ledger.clj") (java.io.File.) .getAbsoluteFile .getParent)
      rows (for [[ep obs] (sort epochs) [party net] (sort (net-positions obs))]
             (str ep "," party "," net))
      csv  (str "epoch,party,net\n" (str/join "\n" rows) "\n")]
  (spit "/tmp/clearing_ledger.csv" csv)
  (let [r (sh "bash" "-lc" (str "duckdb < " here "/clearing_ledger.sql"))]
    (println (:out r))
    (System/exit (:exit r))))
