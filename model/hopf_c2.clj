#!/usr/bin/env bb
;; hopf_c2.clj -- C^2 as the concrete instance of the session's contact/Reeb thread.
;; S^3 = unit sphere in C^2 is THE canonical contact manifold; its Reeb flow is the Hopf
;; fibration S^1 -> S^3 -> S^2 (the Bloch sphere). Phase rotation e^{i t} = the Reeb orbit;
;; it fixes the Hopf image = the fiber. (fastmath ships complex+quaternion; here, dep-free.)
;; Run: bb model/hopf_c2.clj

(defn cmul [[a b] [c d]] [(- (* a c) (* b d)) (+ (* a d) (* b c))])   ; complex *
(defn cconj [[a b]] [a (- b)])
(defn cabs2 [[a b]] (+ (* a a) (* b b)))
(defn rot [theta [a b]] (cmul [(Math/cos theta) (Math/sin theta)] [a b]))  ; e^{i theta} *

;; Hopf map  (z1,z2) in S^3  ->  S^2 in R^3 :  ( 2 z1 conj(z2) as (x,y),  |z1|^2 - |z2|^2 )
(defn hopf [z1 z2]
  (let [[x y] (cmul z1 (cconj z2))]
    [(* 2.0 x) (* 2.0 y) (- (cabs2 z1) (cabs2 z2))]))
(defn norm3 [[x y z]] (Math/sqrt (+ (* x x) (* y y) (* z z))))

(let [;; a unit point in C^2 (a qubit state):  z1=(1/2)+(1/2)i,  z2=(1/2)-(1/2)i  -> |z1|^2+|z2|^2=1
      z1 [0.5 0.5] z2 [0.5 -0.5]
      s3-norm (Math/sqrt (+ (cabs2 z1) (cabs2 z2)))
      img (hopf z1 z2)
      ;; Reeb flow: rotate BOTH coords by phase theta -> Hopf image must be invariant (the fiber)
      thetas [0.0 0.7 1.6 3.1]
      fiber-imgs (map (fn [t] (hopf (rot t z1) (rot t z2))) thetas)
      fiber-max-dev (apply max (map (fn [p] (norm3 (map - p img))) fiber-imgs))]
  (println "== C^2 / S^3 contact manifold, Reeb = Hopf ==")
  (println (format "S^3 point norm (== 1?)         : %.6f" s3-norm))
  (println (format "Hopf image on S^2 (Bloch), |.| : %.6f  (== 1?)" (norm3 img)))
  (println (format "Bloch vector (x,y,z)           : [%.3f %.3f %.3f]" (nth img 0) (nth img 1) (nth img 2)))
  (println (format "Reeb-orbit (phase) invariance  : max fiber deviation %.2e (== 0 => Hopf circle)" fiber-max-dev))
  (let [ok (and (< (Math/abs (- s3-norm 1.0)) 1e-9)
                (< (Math/abs (- (norm3 img) 1.0)) 1e-9)
                (< fiber-max-dev 1e-9))]
    (println (format "OVERALL: %s" (if ok "ACCEPT (S^3 -> S^2 Hopf, Reeb=phase fixes the fiber)" "REJECT")))
    (System/exit (if ok 0 1))))
