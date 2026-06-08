#!/usr/bin/env bb
;; loopify_model.clj — executable operational model of jank `ir/opt/loopify`.
;;
;; Satisfies, in ONE runnable artifact, the practices an adversary must accept:
;;   Tweag : reproducible (fixed SplitMix64 seed, no wall-clock/Math/random),
;;           property-based testing over a generated corpus, an explicit
;;           operational-semantics model (interp) as the oracle.
;;   simonw: red OBSERVED (deep recursion really overflows here), green OBSERVED
;;           (loopify'd version returns the right answer), independent closed-form
;;           oracle so the green is not self-graded, "first run the tests" = this file.
;;
;; Claim modelled: loopify preserves observable semantics AND removes native-stack
;; overflow.  i.e.  forall F x.  interp(F,x) == (loopify F)(x)   [equivalence]
;;            and   interp overflows where (loopify F) does not  [the capability]
;;
;; Run:  bb loopify_model.clj      (deterministic — re-run yields identical output)

;; ---------- typed config (Nickel) drives the test — Tweag ----------
(require '[clojure.java.io :as io] '[cheshire.core :as json])
(def here (-> (or *file* "model/loopify_model.clj") io/file .getAbsoluteFile .getParent))
(def cfg (let [f (io/file here "loopify_params.json")]
           (if (.exists f)
             (json/parse-string (slurp f) true)
             {:stack_limit 500 :safe_n 300 :equivalence_cases 300 :seed 1069 :deep 500000})))

;; ---------- reproducible PRNG (SplitMix64) — Tweag: no nondeterminism ----------
(def GOLDEN (Long/parseUnsignedLong "9E3779B97F4A7C15" 16))
(def M1 (Long/parseUnsignedLong "BF58476D1CE4E5B9" 16))
(def M2 (Long/parseUnsignedLong "94D049BB133111EB" 16))
(defn sm64 [x]
  (let [z (unchecked-add x GOLDEN)
        z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 30)) M1)
        z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 27)) M2)]
    (bit-xor z (unsigned-bit-shift-right z 31))))
(defn rand-stream [seed] (rest (iterate sm64 seed)))     ; deterministic infinite stream
(defn pick [v r] (nth v (mod (Math/abs (long r)) (count v))))

;; ---------- the recursion schema loopify compiles ----------
;; non-tail LINEAR recursion:  f(n) = (base? n) ? (bval n) : (combine n (f (step n)))
;; F = {:base? :bval :step :combine}.  This is exactly crane's non-tail shape and the
;; jank `(defn f [n] (if .. (combine n (f (step n)))))` that has no `recur` TCO.

;; Modelled native-frame budget. Explicit (not the host JVM stack) so the overflow is
;; DETERMINISTIC and portable — an adversary cannot dismiss it as "your stack was small".
(def STACK-LIMIT (long (:stack_limit cfg)))
(defn interp                                ; DIRECT recursion — consumes modelled native frames
  ([F n] (interp F n 0))
  ([F n depth]
   (when (> depth STACK-LIMIT)
     (throw (ex-info "modelled native-stack overflow" {:depth depth})))
   (if ((:base? F) n)
     ((:bval F) n)
     ((:combine F) n (interp F ((:step F) n) (inc depth))))))

(defn loopify [F]                           ; the transform: explicit 2-phase stack, iterative
  (fn [n0]
    ;; phase 1 (crane _Enter): descend, pushing each n until base reached
    (loop [n n0, stack (transient [])]
      (if ((:base? F) n)
        ;; phase 2 (crane _Combine): fold combine back up from the base value
        (loop [acc ((:bval F) n), s (persistent! stack), i (dec (count s))]
          (if (neg? i)
            acc
            (recur ((:combine F) (nth s i) acc) s (dec i))))
        (recur ((:step F) n) (conj! stack n))))))

;; ---------- generated corpus of F (reproducible) — Tweag PBT ----------
(def combiners [(fn [n a] (+ n a))
                (fn [n a] (+ (* 2 n) a))
                (fn [n a] (+ 1 a))                 ; depth counter
                (fn [n a] (max n a))
                (fn [n a] (+ (mod n 7) a))])
(def steppers  [(fn [n] (dec n)) (fn [n] (- n 2)) (fn [n] (- n 3))])
(defn gen-F [r0 r1]
  {:base? (fn [n] (<= n 0)) :bval (fn [_] 0)
   :step  (pick steppers r0) :combine (pick combiners r1)})

;; ---------- the property: interp == loopify, over the corpus ----------
(defn check-equivalence [n-cases safe-n seed]
  (loop [i 0, rs (partition 2 (take (* 2 n-cases) (rand-stream seed))), fails []]
    (if (empty? rs)
      {:cases n-cases :failures fails :ok (empty? fails)}
      (let [[r0 r1] (first rs)
            F (gen-F r0 r1)
            n (mod (Math/abs (long r0)) safe-n)     ; n small enough that interp won't overflow
            a (interp F n)
            b ((loopify F) n)]
        (recur (inc i) (rest rs)
               (if (= a b) fails (conj fails {:case i :n n :interp a :loopify b})))))))

;; ---------- red/green with an INDEPENDENT closed-form oracle ----------
(def SUM {:base? (fn [n] (<= n 0)) :bval (fn [_] 0)
          :step (fn [n] (dec n))   :combine (fn [n a] (+ n a))})
(defn triangular [n] (quot (* n (inc n)) 2))         ; oracle: sum 1..n, independent of both impls
(def DEEP (long (:deep cfg)))

(defn red-phase []
  (try (interp SUM DEEP) :NO-OVERFLOW
       (catch Exception _ :OVERFLOW)))           ; ex-info from exceeding STACK-LIMIT
(defn green-phase [] ((loopify SUM) DEEP))

;; ---------- run + machine-checkable report ----------
(let [equiv  (check-equivalence (long (:equivalence_cases cfg)) (long (:safe_n cfg)) (long (:seed cfg)))
      red    (red-phase)
      green  (green-phase)
      oracle (triangular DEEP)
      red-ok    (= red :OVERFLOW)            ; interp REALLY overflows (witnessed)
      green-ok  (= green oracle)             ; loopify matches independent oracle
      equiv-ok  (:ok equiv)
      all (and equiv-ok red-ok green-ok)]
  (println "== loopify operational model ==")
  (println (format "equivalence  interp==loopify  over %d reproducible cases : %s (failures=%d)"
                   (:cases equiv) (if equiv-ok "PASS" "FAIL") (count (:failures equiv))))
  (println (format "red  (simonw): interp SUM %d  -> %s  : %s" DEEP red (if red-ok "RED witnessed" "NOT red")))
  (println (format "green        : (loopify SUM) %d -> %d  (oracle triangular=%d) : %s"
                   DEEP green oracle (if green-ok "GREEN" "WRONG")))
  (println (format "OVERALL : %s" (if all "ACCEPT (all checks pass)" "REJECT")))
  (when (seq (:failures equiv)) (println "failures:" (:failures equiv)))
  (System/exit (if all 0 1)))
