(ns ainori.methods.social
  "ainori configuration wrapper around the shared social-publication membrane."
  (:require [etzhayyim.social.publication :as publication]))

(def config {:actor-id "ainori" :display-name "ainori"})
(def DISCLAIMER (publication/disclaimer config))

(defn draft-observation-post
  ([subject body sources]
   (draft-observation-post subject body sources ""))
  ([subject body sources author]
   (publication/draft-observation-post config subject body sources author)))

(defn build-live [& args]
  (apply publication/build-live config args))
