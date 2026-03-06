(ns llm.transport.jdk-http
  "JDK HttpClient-backed HTTP transport implementation."
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [llm.protocols :as protocols])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
                          HttpRequest$Builder HttpResponse
                          HttpResponse$BodyHandlers)
           (java.io InputStream)))

(set! *warn-on-reflection* true)

(def json-object-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(defrecord JdkHttpTransport [client])

(defn make-transport
  "Create a JDK HttpClient-backed transport implementation."
  []
  (->JdkHttpTransport (HttpClient/newHttpClient)))

(defn- decode-json
  [body-str]
  (json/read-value body-str json-object-mapper))

(defn- decode-stream-line
  [line]
  (cond
    (str/blank? line) nil
    (str/starts-with? line "data: ")
    (let [payload (subs line 6)]
      (when-not (= "[DONE]" payload)
        (decode-json payload)))
    :else
    (decode-json line)))

(defn- slurp-stream
  [input-stream]
  (with-open [^InputStream in input-stream]
    (slurp in)))

(defn- check-response
  [{:keys [status body]}]
  (when-not (<= 200 status 299)
    (throw (ex-info "HTTP request failed"
                    {:status status
                     :body body})))
  {:status status
   :body body})

(defn- apply-headers
  [^HttpRequest$Builder builder headers]
  (doseq [[k v] headers]
    (.header builder ^String (str k) ^String (str v)))
  builder)

(defn- get-request-builder
  [{:keys [url headers]}]
  (let [^HttpRequest$Builder builder
        (HttpRequest/newBuilder (URI/create url))]
    (apply-headers builder headers)
    (.GET builder)
    (.build builder)))

(defn- post-request-builder
  [{:keys [url headers body]}]
  (let [^HttpRequest$Builder builder
        (HttpRequest/newBuilder (URI/create url))]
    (apply-headers builder
                   (merge {"content-type" "application/json"}
                          headers))
    (.POST builder
           (HttpRequest$BodyPublishers/ofString
            (json/write-value-as-string body)))
    (.build builder)))

(defn- send-string
  [request client]
  (let [response (.send ^HttpClient client
                        ^HttpRequest request
                        (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (.body response)}))

(defn- send-stream
  [request client]
  (let [response (.send ^HttpClient client
                        ^HttpRequest request
                        (HttpResponse$BodyHandlers/ofInputStream))]
    {:status (.statusCode response)
     :body (slurp-stream (.body response))}))

(defn- consume-json-stream
  [body on-event]
  (doseq [line (str/split-lines body)
          :let [event (decode-stream-line line)]
          :when event]
    (on-event event)))

(extend-type JdkHttpTransport
  protocols/Transport
  (get-json [{:keys [client]} request]
    (-> request
        get-request-builder
        (#(send-string % client))
        check-response
        :body
        decode-json))
  (post-json [{:keys [client]} request]
    (-> request
        post-request-builder
        (#(send-string % client))
        check-response
        :body
        decode-json))
  (post-json-stream [{:keys [client]} {:keys [url headers body]} on-event]
    (-> {:url url
         :headers (merge {"accept" "text/event-stream"}
                         headers)
         :body body}
        post-request-builder
        (#(send-stream % client))
        check-response
        :body
        (consume-json-stream on-event))))
