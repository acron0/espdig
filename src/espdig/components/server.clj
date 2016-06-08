(ns espdig.components.server
  (:require [org.httpkit.server           :as httpkit]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [compojure.api.middleware     :refer [wrap-components]]
            [com.stuartsierra.component   :as component]
            [taoensso.timbre              :as log]
            [compojure.api.sweet          :refer :all]
            [ring.util.http-response      :refer :all]
            [taoensso.timbre              :as log]
            [schema.core                  :as s]))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Espdig server"
                   :description ""}
            :tags [{:name "api", :description "foobar"}]}}}

   (GET "/hello" req
        :summary "Hi there"
        :components []
        (do
          (log/debug req)
          (str "Hola amigo!")))))

(defrecord HttpKit [port]
  component/Lifecycle
  (start [this]
    (log/info "Server started at http://localhost" port)
    (assoc this :http-kit (httpkit/run-server
                           (-> #'app
                               (wrap-components this)
                               (wrap-content-type "application/json"))
                           {:port port})))
  (stop [this]
    (log/info "Stopping server")
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn make-http-server
  [{:keys [port]}]
  (->HttpKit port))
