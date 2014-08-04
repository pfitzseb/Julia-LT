(ns lt.objs.langs.julia.module
  (:require [lt.objs.langs.julia.proc :as proc]
            [lt.objs.statusbar :as statusbar]
            [lt.object :as object]
            [lt.objs.eval :as eval]
            [lt.objs.clients :as clients]
            [crate.binding :refer [bound map-bound]])
  (:require-macros [lt.macros :refer [behavior defui]]))

;; Status bar object

(defn ->module-str [{:keys [module]}]
  [:span.module (str module "\t")])

(object/object* ::statusbar.module
                :triggers #{}
                :behaviors #{::update-module-status}
                :module "Main"
                :init (fn [this]
                        (statusbar/statusbar-item (bound this ->module-str) "")))

(def statusbar-module (object/create ::statusbar.module))
(statusbar/add-statusbar-item statusbar-module)

(defn ->module [editor]
  (::module @editor))

(defn isactive? [editor]
  (identical? (lt.objs.editor.pool/last-active) editor))


(behavior ::update-module-statusbar
          :triggers #{:active :module-update}
          :reaction (fn [editor]
                      (if (isactive? editor)
                        (object/merge! statusbar-module {:module (->module editor)}))))

;; Backend communication

;; ::get-module and ::get-module-on-connect are separated into separate behaviors because:
;; a) the debounce on ::get-module improves performance on tab change, so is desired
;; b) the debounce implementation is per function call, not per editor object that it's
;;    called with so when :julia.connected event is sent to all open .jl tabs/editors
;;    all but one call is dropped.
;; If a per object debounce or a throttle with a delayed leading call were implemented
;; in LT these could be recombined.

(def get-module
  (fn [editor & [client]]
    (when-let [client (or client (proc/default-client))]
      (clients/send client
                    :editor.julia.module.update
                    {:path (-> @editor :info :path)}
                    :only editor))))

(behavior ::get-module
  :triggers #{:object.instant :active :save}
  :debounce 100
  :reaction get-module)

(behavior ::get-module-on-connect
  :triggers #{:julia.connected}
  :reaction get-module)

(behavior ::update-module
  :triggers #{:editor.julia.module.update}
  :reaction (fn [editor module]
              (object/merge! editor {::module module})
              (object/raise editor :module-update)))
