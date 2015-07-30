(ns mpd.stage
  (:require [mpd.shared :refer [log]]
            [mpd.network :as network]
            [mpd.assets :as assets]))

(def player-sprites (atom {}))

(defn player-sprite [player]
  (let [exists (get @player-sprites (:id player))]
    (if (nil? exists)
      (let [sprite (assets/puke_sprite_for_texture assets/player_texture 0.4 true)]
        (log "creating sprite for " (:id player))
        (swap! player-sprites assoc (:id player) sprite)
        sprite)
      exists)))

(defmulti pixi (fn [x y] x))
(defmethod pixi :player [_ state]
  (let [player (player-sprite state)]
    (aset player "anchor" (js-obj "x" 0.5 "y" 0.5))
    (aset player "position" (js-obj "x" (:x state) "y" (:y state)))
    (aset player "rotation" (:rotation state))
    (if (= (:id state) @network/server-id)
      (do
        (aset assets/health_bar "text" (str (:hp state)))
        (aset assets/health_bar "position" (js-obj "x" (:x state) "y" (+ (:y state) 50)))
        (aset assets/health_bar "anchor" (js-obj "x" 0.5 "y" 0.5))
        [player, assets/health_bar])
      [player])))

(defmethod pixi :enemies [_ enemies]
  (reduce (fn [agg enemy]
            (concat agg (pixi :player (last enemy))))
          [] (seq enemies)))

(defmethod pixi :bullet [_ bullet]
  (let [shape (assets/bullet_sprite)]
    (aset shape "anchor" (js-obj "x" 0.5 "y" 0.5))
    (aset shape "position" (js-obj "x" (:x bullet) "y" (:y bullet)))
    (aset shape "rotation" (:angle bullet))
    [shape]))

(defmethod pixi :bullets [_ bullets]
  (reduce (fn [agg bullet]
            (concat agg (pixi :bullet bullet)))
          [] (seq bullets)))

(defmethod pixi :crosshair [_ crosshair]
  (aset assets/crosshair_sprite "position" (js-obj "x" (:x crosshair) "y" (:y crosshair)))
  [assets/crosshair_sprite])

(def root (js/PIXI.Container.))
(def world
  (let [c (js/PIXI.Container.)]
    (.addChild root c) c))
(def static
  (let [c (js/PIXI.Container.)]
    (.addChild root c) c))

(defn state-to-pixi [state]
  (.removeChildren world)
  (.removeChildren static)
  (.addChild world assets/background_tiling_sprite)
  (let [x (:x (:player @state))
        y (:y (:player @state))]
    (aset static "x" (- (/ (:w dimensions) 2) x))
    (aset static "y" (- (/ (:h dimensions) 2) y))
    (aset world "x" (- (/ (:w dimensions) 2) x))
    (aset world "y" (- (/ (:h dimensions) 2) y)))
  (doseq [kv @state]
    (doseq [obj (apply pixi kv)]
      (let [container (case (first kv)
                        :player static
                        :crosshair root
                        world)]
        (.addChild container obj))))
  root)

(def dimensions {:w (- (.-innerWidth js/window) 4)
                 :h (- (.-innerHeight js/window) 4)})

(defn setup []
  (log "  stage")
  (log dimensions)
  (let [renderer (js/PIXI.autoDetectRenderer
          (:w dimensions) (:h dimensions)
          #js {:antialiasing false
          :transparent false
          :resolution 1})
       ]
    (set! (.-backgroundColor renderer) "0xFFFFFF")
    (.appendChild (.-body js/document) (.-view renderer))
    ;(.generateTexture world renderer)
    (fn [state] (.render renderer (state-to-pixi state)))))
