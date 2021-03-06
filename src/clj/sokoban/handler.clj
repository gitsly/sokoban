(ns sokoban.handler
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [GET routes]]
            [compojure.route :refer [not-found resources]]
            [config.core :refer [env]]
            [hiccup.page :refer [include-js include-css html5]]
            [org.httpkit.client :as http-client]
            [ring.util.response :as resp]
            [sokoban.cache :refer [add-catalog! add-level! get-catalog get-level
                                   set-catalog-list!]]
            [sokoban.game-sokoban-parser :refer [extract-catalog-list
                                                 extract-catalog
                                                 extract-level
                                                 extract-catalog-page-count]]
            [sokoban.middleware :refer [wrap-middleware]]))

(def mount-target
  [:div#app])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (include-js "/js/app.js")]))

(defn- build-catalog [id]
  (let [page-size  24
        page-count (-> @(http-client/get
                         (str "http://www.game-sokoban.com/index.php"
                              "?mode=catalog")
                         {:query-params {:cid  id
                                         :page 1
                                         :q    page-size}})
                       :body
                       extract-catalog-page-count)]
    (vec (reduce
          (fn [levels page]
            (concat levels
                    (-> @(http-client/get (str "http://www.game-sokoban.com/"
                                               "index.php?mode=catalog")
                                          {:query-params {:cid  id
                                                          :page (inc page)
                                                          :q    page-size}})
                        :body
                        extract-catalog)))
          []
          (range page-count)))))

(defn routes-wrapper [cache]
  (routes
   (GET "/" [] (loading-page))
   (GET "/catalogs" _
     (if (seq @(:catalog-list cache))
       (do
         (log/debug "Returning cached catalog list")
         (resp/response @(:catalog-list cache)))
       (let [resp @(http-client/get (str "http://www.game-sokoban.com/"
                                         "index.php?mode=catalog"))]
         (if (:error resp)
           (do (log/error (str "Failed to download catalogs from "
                               "http://www.game-sokoban.com: " (:error resp)))
               (resp/response resp))
           (let [catalogs (-> resp :body extract-catalog-list)]
             (log/debug "Downloaded catalogs from http://www.game-sokoban.com")
             (set-catalog-list! cache catalogs)
             (resp/response catalogs))))))
   (GET "/catalog/:id" [id]
     (let [id (Integer/parseInt id)]
       (if (contains? @(:catalogs cache) id)
         (do
           (log/debug "Returning cached catalog:" id)
           (resp/response (get-catalog cache id)))
         (let [catalog (build-catalog id)]
           (add-catalog! cache id catalog)
           (resp/response catalog)))))
   (GET "/level/:id" [id]
     (let [id (Integer/parseInt id)]
       (if (contains? @(:levels cache) id)
         (resp/response (get-level cache id))
         (let [resp @(http-client/get (str "http://www.game-sokoban.com/"
                                           "index.php?mode=level_info"
                                           "&view=general")
                                      {:query-params {:ulid id}})
               level (-> resp :body extract-level)]
           (log/debug "Downloaded level from http://www.game-sokoban.com:" id)
           (add-level! cache id level)
           (resp/response level)))))
   (resources "/")
   (not-found "Not Found")))

(defn app-fn [{:keys [cache]}]
  (-> (routes-wrapper cache)
      wrap-middleware))
