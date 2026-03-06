(ns build
  "Build tasks for jar and GraalVM native image generation."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]))

(def lib 'llm/llm)
(def version "0.1.0")
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))
(def native-bin "target/clj-llm")

(def project-deps
  (-> "deps.edn" io/file slurp edn/read-string))

(def app-basis
  (b/create-basis {:deps project-deps}))

(def native-basis
  (b/create-basis {:deps project-deps}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber
  "Build the application uberjar."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis app-basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile ['llm.main]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis app-basis
           :main 'llm.main}))

(defn uber-native
  "Build the application uberjar for native-image input."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis native-basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile ['llm.main]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis native-basis
           :main 'llm.main}))

(defn native-image-args []
  (cond-> ["native-image"
           "--no-fallback"
           "-H:+ReportExceptionStackTraces"
           "--initialize-at-build-time=clojure,llm,cli_matic,jsonista,expound"
           "-jar" uber-file
           "-o" native-bin]
    (= "Mac OS X" (System/getProperty "os.name"))
    (into ["--native-compiler-options=-arch"
           "--native-compiler-options=arm64"])))

(defn native
  "Build the native image binary."
  [_]
  (uber-native nil)
  (b/process {:command-args (native-image-args)}))

(defn print-classpath
  "Print the native build classpath for debugging."
  [_]
  (println (str/join java.io.File/pathSeparator (:classpath-roots native-basis))))
