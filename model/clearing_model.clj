#!/usr/bin/env bb
;; clearing_model.clj — runnable operational model of the ewig-clearing engine.
;;
;; Demonstrates, with exact (auto-promoting) arithmetic, that the transient-lifecycle
;; clearing round CONSERVES value (rung5 multilateral netting): for each clearing group
;; (weakly-connected component of the owes-graph), Σ net ≡ 0. Mirrors ewig's value-oriented
;; architecture: persistent ledger snapshots, a transient for the fast in-place batch, freeze.
;;
;; Practices (same bar as loopify_model.clj): reproducible (fixed SplitMix64 seed), property-based
;; (conservation over a generated corpus), exact arithmetic (+'/-', F2-safe — never i32-wraps).
;; Run: bb clearing_model.clj

(def GOLDEN (Long/parseUnsignedLong "9E3779B97F4A7C15" 16))
(def M1 (Long/parseUnsignedLong "BF58476D1CE4E5B9" 16))
(def M2 (Long/parseUnsignedLong "94D049BB133111EB" 16))
(defn sm64 [x] (let [z (unchecked-add x GOLDEN)
                     z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 30)) M1)
                     z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 27)) M2)]
                 (bit-xor z (unsigned-bit-shift-right z 31))))
(defn rand-stream [seed] (rest (iterate sm64 seed)))
(defn pos [r m] (inc (mod (Math/abs (long r)) m)))

;; An obligation: `from` owes `to` an `amt` (exact). Parties are ints.
;; ---- net positions via a TRANSIENT (the fast in-place clearing batch) ----
;; net(p) = (total owed TO p) − (total p owes).  '+ / '- auto-promote → exact, never i32-wraps (F2-safe).
(defn net-positions [obligations]
  (persistent!
    (reduce (fn [t {:keys [from to amt]}]
              (let [t (assoc! t from (-' (get t from 0) amt))
                    t (assoc! t to   (+' (get t to 0) amt))]
                t))
            (transient {})
            obligations)))

;; ---- clearing groups = weakly-connected components of the owes-graph (union-find) ----
(defn wccs [parties obligations]
  (let [uf (atom (into {} (map (fn [p] [p p]) parties)))]
    (letfn [(find [p] (let [r (get @uf p p)] (if (= r p) p (find r))))
            (uni [a b] (swap! uf assoc (find a) (find b)))]
      (doseq [{:keys [from to]} obligations] (uni from to))
      (->> parties (group-by find) vals (map set)))))

;; ---- conservation audit: Σ net ≡ 0 within every clearing group ----
(defn conserved? [net groups]
  (every? (fn [g] (zero? (reduce +' 0 (map #(get net % 0) g)))) groups))

;; ---- reproducible random closed obligation system ----
(defn gen-system [n-parties n-obs seed]
  (let [rs (rand-stream seed)]
    (loop [i 0, rs rs, obs []]
      (if (= i n-obs)
        obs
        (let [[a b c & more] rs
              from (mod (Math/abs (long a)) n-parties)
              to   (mod (Math/abs (long b)) n-parties)]
          (recur (inc i) more
                 (if (= from to) obs (conj obs {:from from :to to :amt (pos c 1000)}))))))))

;; ---- the clearing round (the transient lifecycle) ----
(defn clearing-round [ledger obligations]      ; ledger = persistent snapshot of net positions
  (let [batch-net (net-positions obligations)  ; transient batch → frozen value
        parties   (into (set (keys ledger)) (keys batch-net))
        merged    (persistent!                  ; fold batch into ledger (exact)
                    (reduce (fn [t p] (assoc! t p (+' (get ledger p 0) (get batch-net p 0))))
                            (transient {}) parties))]
    merged))                                    ; new persistent snapshot (ewig: free history)

;; ---- run: property-based conservation + a multi-epoch snapshot history ----
(defn check [n-trials seed]
  (loop [i 0, rs (partition 3 (take (* 3 n-trials) (rand-stream seed))), fails 0, stats []]
    (if (empty? rs)
      {:trials n-trials :failures fails :stats (take 3 stats)}
      (let [[a b c] (map #(Math/abs (long %)) (first rs))
            np (inc (mod a 40)), no (+ 5 (mod b 200))
            obs (gen-system np no (+ seed c))
            net (net-positions obs)
            grp (wccs (range np) obs)
            ok  (conserved? net grp)
            gross (count obs)
            settled (count (filter (fn [[_ v]] (not (zero? v))) net))]
        (recur (inc i) (rest rs) (if ok fails (inc fails))
               (conj stats {:parties np :gross gross :groups (count grp) :settled settled}))))))

(let [{:keys [trials failures stats]} (check 500 1069)
      ;; multi-epoch ledger (ewig snapshot history)
      e1 (clearing-round {} (gen-system 12 60 1))
      e2 (clearing-round e1 (gen-system 12 60 2))
      e3 (clearing-round e2 (gen-system 12 60 3))
      ledger-conserved (every? #(zero? (reduce +' 0 (vals %))) [e1 e2 e3])
      all (and (zero? failures) ledger-conserved)]
  (println "== ewig-clearing operational model ==")
  (println (format "conservation Sigma-net=0 per clearing group over %d reproducible systems : %s (failures=%d)"
                   trials (if (zero? failures) "PASS" "FAIL") failures))
  (println (format "gross->net example: %s" (first stats)))
  (println (format "multi-epoch ledger (3 snapshots, ewig history) conserves Sigma=0 : %s" ledger-conserved))
  (println (format "OVERALL : %s" (if all "ACCEPT (conservation holds, transient batch, exact arithmetic)" "REJECT")))
  (System/exit (if all 0 1)))
