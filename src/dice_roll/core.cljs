(ns dice-roll.core
  (:require
   ["express" :as express]
   [clojure.string :as str]
   ["cheerio" :as chro]
   ["fs" :as fs]))

(def root js/__dirname)

(defn- layouted-dice [canvas]

  )

(defn- raw-canvas []
  (let [content (. fs readFileSync (str root "/public/index.html"))
        $       (. chro load content (clj->js {:withDomLvl1         true
                                               :normalizeWhitespace false
                                               :xmlMode             false
                                               :decodeEntities      true}))]
    ($ "#app")))

(defn- func [req res]
  (let [dice1      (inc (rand-int 6))
        dice2      (inc (rand-int 6))
        sum        (+ dice1 dice2)
        canvas     (raw-canvas)           ; done
        canvas+    (layouted-dice canvas) ; now
        png64-url  (pngify canvas+)
        png64-body (str/replace png64-url #"^data:image\/png;base64," "")]
    ;; Return png in base64 form with a header of success
    (. res writeHead 200 (clj->js {:Content-Type   "image/png"
                                   :Content-Length (. png64-body -length)}))
    (. res end png64-body))
  )

;; Main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce server (atom nil))

(defonce reload-counter
  (let [counter (atom 0)]
    (fn []
      (swap! counter inc)
      @counter)))

(defn ^:dev/after-load main []
  (let [app (express.)]

    (. app get "/" func)

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

