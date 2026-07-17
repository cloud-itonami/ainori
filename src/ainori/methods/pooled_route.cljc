(ns ainori.methods.pooled-route
  "pooled_route — ainori multi-stop pooled sequencing, REUSING the todoke route core.

  1:1 port of `20-actors/ainori/methods/pooled_route.py` (ADR-2606071500).

  ainori does NOT ship a second routing engine. It delegates to todoke's public,
  envelope-neutral `sequence-stops` repository contract. The operational envelope remains
  actor-specific: todoke validates pedestrian delivery while ainori validates vehicular rides.

  The ONE thing ainori does NOT inherit from todoke is the *safety envelope*: todoke's ODD is
  pedestrian (sidewalk/crosswalk/doorpath/bikelane), while ainori is VEHICULAR. ainori keeps
  its own SAE-L4 vehicular envelope in agent.py (G3) and reuses only the geometric stop
  sequencing. Sequencing has no charter content; the envelope does. ainori's no-surge
  `cost-share` (agent.py) is inlined here verbatim (compose, don't duplicate; integer
  floor-division so the carrier absorbs any remainder — the platform never profits, G1/G2).

  Data maps are STRING-keyed (mirrors the Python dicts). Pure; portable .cljc."
  (:require [todoke.methods.last-mile :as todoke-route]))

(defn- ->stop
  "Build a todoke-style stop map {:id :x :y :zone} (the same shape last-mile consumes)."
  [id x y zone]
  {:id (int id) :x (double x) :y (double y) :zone zone})

;; --------------------------------------------------------------------------- #
;; cost-share (G2 no-surge) — inlined from ainori py/agent.py, verbatim behavior.
;; There is NO demand / surge multiplier: share depends only on real cost + occupancy.
;; --------------------------------------------------------------------------- #
(defn cost-share
  "Each rider's flat share of the trip's REAL fuel/wear cost. Higher occupancy ⇒ lower share,
  the opposite of surge (G2). Integer floor-division (carrier absorbs the remainder, G1)."
  [fuel-wear-minor occupancy]
  (let [occ (max 1 (int occupancy))]
    (long (quot (long fuel-wear-minor) occ))))

(defn sequence-stops
  "Order a list of stop maps (`stops[0]` pinned as origin) and return [order-of-ids length-m].

  This IS todoke's sequencing core: `_two_opt(_nearest_neighbour(stops), stops)` — the same
  primitives the Rust crate mirrors. No safety envelope is applied here (sequencing is
  charter-neutral; ainori's vehicular envelope lives in agent.py, G3), so vehicular zones like
  \"arterial\"/\"expressway\" sequence freely. The parity test pins this to todoke's
  `plan-last-mile` order on a shared pedestrian fixture."
  [stops]
  (todoke-route/sequence-stops stops))

(defn pooled-route
  "Build a pooled vehicular route: the carrier's origin (id 0) plus each rider's pickup/dropoff
  point, sequenced by the reused todoke core to minimise added distance (G11). `carrier-origin`
  is [x y]; `rider-points` is a list of string-keyed maps {\"id\" \"x\" \"y\" \"zone\"}. Returns a
  string-keyed map {\"order\" \"lengthM\" \"occupancy\"}."
  [carrier-origin rider-points]
  (let [stops (into [(->stop 0 (nth carrier-origin 0) (nth carrier-origin 1) "arterial")]
                    (map (fn [p]
                           (->stop (get p "id") (get p "x") (get p "y")
                                   (get p "zone" "arterial")))
                         rider-points))
        [order length] (sequence-stops stops)]
    {"order" order
     "lengthM" length
     "occupancy" (count rider-points)}))

(defn plan-pooled-trip
  "End-to-end pooled trip: sequence the stops with the reused todoke core, then split the REAL
  fuel/wear cost flat across the pooled riders with ainori's no-surge `cost-share`. Returns the
  route + per-rider share + total collected.

  Honest cost-share property (G1/G2): totalCollected = share × occupancy NEVER exceeds the real
  fuel/wear (floor-division rounds the per-rider share DOWN, carrier absorbs the remainder)."
  [carrier-origin rider-points fuel-wear-minor]
  (let [route (pooled-route carrier-origin rider-points)
        occ (get route "occupancy")
        share (cost-share fuel-wear-minor occ)]
    (assoc route
           "fuelWearMinor" (long fuel-wear-minor)
           "costSharePerRiderMinor" share
           "totalCollectedMinor" (* share occ))))

;; NOTE: the Python module's import-time todoke path wiring + the __main__ demo are omitted;
;; reuse is expressed directly via the `:require` of todoke.methods.last-mile above.
