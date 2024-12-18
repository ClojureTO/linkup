(ns linkup.core
  (:require
    [babashka.fs :as fs]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as string])
  (:import
    [java.time Instant LocalDate LocalTime ZonedDateTime ZoneId ZoneOffset]
    [java.time.format DateTimeFormatter]))


(def crlf "\r\n")


(def line-length 72)


(def formatter (-> (DateTimeFormatter/ofPattern "yyyyMMdd'T'HHmmss'Z'")
                   (.withZone ZoneOffset/UTC)))

(defn format-instant
  [instant]
  (.format formatter instant))

#_(format-instant (Instant/now))


(defn utc-time-string
  [date time timezone]
  (-> (ZonedDateTime/of (LocalDate/parse date)
                        (LocalTime/parse time)
                        (ZoneId/of timezone))
      .toInstant
      format-instant))


(defn wrap
  "Wraps the given string to `line-length` using the iCalendar RFC approach.
  "
  [s]
  (->> (partition-all line-length s)
       (map #(apply str %))
       (string/join (str crlf " "))))


(defn vprintln
  "Print using the iCalendar RFC, wrapping lines and using a CRLF line
  ending.
  "
  [s]
  (print (wrap s))
  (print crlf))


(defn print-time-slot
  [time-slot timezone]
  (let [{:keys [id
                date
                start-time
                end-time
                rrule
                summary
                location]} time-slot]
    (vprintln "BEGIN:VEVENT")
    (vprintln (str "UID:" id))
    (vprintln (str "DTSTAMP:" (format-instant (Instant/now))))
    (vprintln (str "SUMMARY:" summary))
    (when rrule
      (vprintln (str "RRULE:" rrule)))
    (when location
      (vprintln (str "LOCATION:" location)))
    (vprintln (str "DTSTART;TZID=" timezone ":" (utc-time-string date start-time timezone)))
    (vprintln (str "DTEND:" (utc-time-string date end-time timezone)))
    (vprintln "END:VEVENT")))


(defn print-calendar
  [time-slots timezone]
  (vprintln "BEGIN:VCALENDAR")
  (vprintln "VERSION:2.0")
  (vprintln "PRODID:-//linkup//linkup//EN")
  (vprintln "METHOD:PUBLISH")
  (doseq [time-slot time-slots]
    (print-time-slot time-slot timezone))
  (vprintln "END:VCALENDAR"))


(defn build
  []
  (let [event-files (->> (fs/glob "docs/events" "*.edn")
                         (map str))]
    (doseq [file-name event-files]
      (let [[_ root] (re-find #"([^/]+).edn" file-name)
            edn (-> (slurp file-name)
                    edn/read-string)]
        (spit (str "docs/" root ".ics")
              (with-out-str
                (print-calendar [edn] "America/Toronto")))))
    (spit "docs/index.json"
          (json/encode (->> event-files
                            (map (fn [s] (string/replace s "docs/" ""))))))))

(comment

  (re-find #"([^/]+).edn" "foo/bar/baz.edn")

  (spit "docs/test.ics" (with-out-str
                          (print-calendar [{:id "clojureto"
                                            :date "2024-12-17"
                                            :start-time "19:00"
                                            :end-time "21:00"
                                            :summary "Clojure Toronto"
                                            }] "America/Toronto"))))
