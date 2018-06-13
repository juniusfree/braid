(ns braid.rss.core
  "Extension to post updates from RSS feeds as messages in a given group"
  (:require
   [braid.core.api :as core]
   #?@(:clj
       [[braid.rss.server.db :as db]
        [braid.core.server.db.group :as group-db]]
       :cljs
       [[braid.rss.client.views :as views]
        [re-frame.core :refer [dispatch]]])))

(defn init!
  []
  #?(:clj
     (do
       (core/register-db-schema! db/schema)
       (core/register-server-message-handlers!
         {:braid.server.rss/load-feeds
          (fn [{user-id :user-id group-id :?data}]
            (if (group-db/user-in-group? user-id group-id)
              {:reply! {:braid/ok (db/group-feeds group-id)}}
              {}))

          :braid.server.rss/add-feed
          (fn [{user-id :user-id {:keys [tag-ids feed-url group-id] :as new-feed} :?data}]
            (if (group-db/user-is-group-admin? user-id group-id)
              (let [new-feed (assoc new-feed
                                    :id (java.util.UUID/randomUUID)
                                    :user-id user-id)]
                {:db-run-txns! (db/add-rss-feed-txn new-feed)
                 :reply! {:braid/ok new-feed}})
              {}))

          :braid.server.rss/retract-feed
          (fn [{user-id :user-id feed-id :?data}]
            (let [group-id (db/feed-group-id feed-id)]
              (if (group-db/user-is-group-admin? user-id group-id)
                {:db-run-txns! (db/remove-feed-txn feed-id)
                 :reply! :braid/ok}
                {})))}))
    :cljs
     (do
       (core/register-group-setting! views/rss-feed-settings-view)
       (core/register-styles! [:.settings.rss-feeds
                               [:.new-rss-feed
                                [:label {:display "block"}]]])
       (core/register-state! {:rss/feeds {}} {:rss/feeds any?})
       (core/register-subs! {:rss/feeds
                             (fn [db [_ group-id]]
                               (get-in db [:rss/feeds (or group-id
                                                          (db :open-group-id))]))})
       (core/register-events!
         {:rss/load-group-feeds
          (fn [_ [_ group-id]]
            {:websocket-send (list [:braid.server.rss/load-feeds group-id]
                                   5000
                                   (fn [reply]
                                     (if-let [resp (:braid/ok reply)]
                                       (dispatch [:rss/-store-feeds group-id resp])
                                       (.error js/console "Failed to load feeds "
                                               (pr-str reply)))))})

          :rss/-store-feeds
          (fn [{db :db} [_ group-id feeds]]
            {:db (assoc-in db [:rss/feeds group-id] (set feeds))})

          :rss/-store-feed
          (fn [{db :db} [_ feed]]
            {:db (update-in db [:rss/feeds (feed :group-id)] (fnil conj #{}) feed)})

          :rss/-remove-feed
          (fn [{db :db} [_ feed]]
            {:db (update-in db [:rss/feeds (feed :group-id)] disj feed)})

          :rss/add-feed
          (fn [_ [_ new-feed]]
            {:websocket-send (list [:braid.server.rss/add-feed new-feed]
                                   5000
                                   (fn [reply]
                                     (if-let [resp (:braid/ok reply)]
                                       (dispatch [:rss/-store-feed resp])
                                       (.error js/console "Error adding feed"
                                               (pr-str reply)))))})
          :rss/retract-feed
          (fn [_ [_ feed]]
            {:websocket-send (list [:braid.server.rss/retract-feed (feed :id)]
                                   5000
                                   (fn [reply]
                                     (when (= :braid/ok reply)
                                       (dispatch [:rss/-remove-feed feed]))))})}))))
