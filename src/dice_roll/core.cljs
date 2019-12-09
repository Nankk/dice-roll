(ns dice-roll.core
  (:require
   ["express" :as express]
   [clojure.string :as str]
   ["fs" :as fs]
   [reagent.core :as reagent]
   [reagent.dom.server :as rdom]
   ["puppeteer" :as ppt]
   [cljs.core.async :as async :refer [>! <! go chan timeout]]
   [async-interop.interop :refer-macros [<p!]]
   ["stream" :as stream]))

(def root js/__dirname)

;; Returning png image ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- pngify [canvas])

(defn- num-to-string [num]
  (case num
    1 "one"
    2 "two"
    3 "three"
    4 "four"
    5 "five"
    6 "six"))

(defn- layouted-dice-hiccup [dice1 dice2]
  [:div.container {:style {:width 500}}
   [:div.row
    ;; First dice
    [:div.col-sm-6
     [:div.card
      [:div.card-header
       [:i {:class (str "fas fa-dice-" (num-to-string dice1)) :aria-hidden true
            :style {:font-size "6rem"
                    :color "#0095F3"}}]]
      [:div.card-body [:h2 (str dice1)]]]]
    ;; +
    ;; [:div.col-sm-2
    ;;  ;; [:div.card-body
    ;;  ;;  [:h2 "+"]]
    ;;  ]
    ;; Second dice
    [:div.col-sm-6
     [:div.card
      [:div.card-header
       [:i {:class (str "fas fa-dice-" (num-to-string dice2)) :aria-hidden true
            :style {:font-size "6rem"
                    :color "#0095F3"}}]]
      [:div.card-body [:h2 (str dice2)]]]]]
   ;; [:div.row
   ;;  [:h2 "↓"]]
   [:div.row
    [:div.col-sm-12
     [:div.card {:style {:background  "#D71C1C"}}
      [:h1 {:style {:color "#FFFFFF"}}
       (str (+ dice1 dice2))]]]]])

(defn- read-raw-elem [dice1 dice2]
  (let [ch (chan)]
    (go (let [out-name    "example.png"
              browser     (<p! (. ppt launch (clj->js {;; :headless false
                                                       :args ["--window-size=500,400"
                                                              "--no-sandbox"
                                                              "--disable-setuid-sandbox"]})))
              page        (<p! (. browser newPage))
              _           (<p! (. page goto (str "file://" root "/public/index.html")))
              elem-txt    (rdom/render-to-string [layouted-dice-hiccup dice1 dice2])
              _           (println elem-txt)
              target-elem (<p! (. page $ "#app"))
              _           (. page evaluate (fn [elem]
                                             (let [target (. js/document getElementById "app")]
                                               (set! (. target -innerHTML) elem))) elem-txt)]
          (println "Screenshot...")
          (<! (timeout 100)) ; What a nasty workaround! ("waitForNavigation" doesn't work in this case)
          (<p! (. page screenshot (clj->js {:path (str "./public/img/" out-name)
                                            :clip {:x      0
                                                   :y      0
                                                   :width  500
                                                   :height 400}})))
          (println "Screenshot!")
          (. browser close)
          (>! ch out-name)))
    ch))

(defn- return-rolled-dice [req res]
  (go (let [dice1    (inc (rand-int 6))
            dice2    (inc (rand-int 6))
            png-name (<! (read-raw-elem dice1 dice2))]
        ;; Return png in base64 form with a header of success
        (let [_  (println "Reading...")
              r  (. fs createReadStream (str "./public/img/" png-name))
              _  (println "Read!")
              ps (. stream PassThrough)]
          (. stream pipeline r ps
             (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
          (. ps pipe res)))))

;; Google ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- google-roll [dice]
  (let [ch (chan)]
    (go (let [out-name    "example.png"
              browser     (<p! (. ppt launch (clj->js {;; :headless false
                                                       :args ["--window-size=500,400"
                                                              "--no-sandbox"
                                                              "--disable-setuid-sandbox"]})))
              page        (<p! (. browser newPage))
              _           (<p! (. page goto (str "https://www.google.com/search?q="
                                                 dice
                                                 "&nirf=true" ; Avoid suggestion interruption
                                                 )))]
          (println "Screenshot Google search result...")
          (<! (timeout 1100)) ; Google roll animation takes a while...
          (<p! (. page screenshot (clj->js {:path (str "./public/img/" out-name)
                                            :clip {:x      150
                                                   :y      230
                                                   :width  635
                                                   :height 250}})))
          (println "Screenshot!")
          (. browser close)
          (>! ch out-name)))
    ch))

(defn- return-roll-result [dice res]
  (let [ch (chan)]
    (go (let [png-name (<! (google-roll dice))
              _  (println "Reading...")
              r  (. fs createReadStream (str "./public/img/" png-name))
              _  (println "Read!")
              ps (. stream PassThrough)]
          (. stream pipeline r ps
             (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
          (. ps pipe res)))
    ch))

(defn- handle-roll-google [req res]
  (println "handle-roll-google")
  (go (try
        (let [query      (js->clj (. req -query) :keywordize-keys true)
              dice       (:dice query)
              matches    (re-find #"([0-9]+)d([0-9]+)" dice)
              dice-count (js/parseInt (matches 1))
              dice-type  (js/parseInt (matches 2))]
          (println dice)
          (println dice-count)
          (println dice-type)
          (if (and (not (js/isNaN dice-count))
                   (not (js/isNaN dice-type))
                   (< dice-count 100)
                   (some #(= % dice-type) '(4 6 8 10 12 20)))
            (return-roll-result dice res)
            (. (. res status 400) end)))
        (catch js/Object e
          (. (. res status 500) end)))))

;; Main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce server (atom nil))

(defonce reload-counter
  (let [counter (atom 0)]
    (fn []
      (swap! counter inc)
      @counter)))

(defn return-200 [req res]
  (. res writeHead 200 (clj->js {:Content-Type "application/json; charset=utf-8"}))
  (. res end (. js/JSON stringify (clj->js {:status "ok"}))))

(defn ^:dev/after-load main []
  (let [app (express.)]

    (. app get "/status" return-200) ; In order to keep the app active by robot access

    (. app get "/roll" return-rolled-dice)

    (. app get "/roll-google" handle-roll-google)

    (when (some? @server)
      (. @server close)
      (println "Why the fuck did you kill the server!!?"))

    (println (str "### You have re-loaded " (reload-counter) " times. ###"))

    ;; Listen process.env.PORT or fixed port 3000
    (let [env-port (.. js/process -env -PORT)
          port (if env-port env-port 3000)]
      (reset! server (. app listen port,
                        #(. js/console log
                            (str "Listening on port " port)))))))

