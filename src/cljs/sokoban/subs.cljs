(ns sokoban.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::static-level
  (fn [db]
    (:static-level db)))

(rf/reg-sub
  ::player-pos
  (fn [db]
    (:player-pos db)))

(rf/reg-sub
  ::movable-blocks
  (fn [db]
    (:movable-blocks db)))

(rf/reg-sub
  ::target-positions
  (fn [db]
    (:target-positions db)))

(rf/reg-sub
  ::level
  (fn [_]
    [(rf/subscribe [::static-level])
     (rf/subscribe [::player-pos])
     (rf/subscribe [::movable-blocks])])
  (fn [[static-level player-pos movable-blocks]]
    (-> static-level
        (as-> l
            (reduce #(assoc-in %1 %2 "$") l movable-blocks))
        (assoc-in player-pos "@"))))

(rf/reg-sub
  ::remaining-count
  (fn [_]
    [(rf/subscribe [::movable-blocks])
     (rf/subscribe [::target-positions])])
  (fn [[movable-blocks target-positions]]
    (->> target-positions
         (remove #(some (fn [p] (= p %)) movable-blocks))
         count)))
