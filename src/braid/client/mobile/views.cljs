(ns braid.client.mobile.views
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [braid.client.mobile.state]
            [braid.client.mobile.style :refer [styles]]
            [braid.client.ui.views.sidebar]
            [braid.client.ui.views.thread]
            [braid.client.ui.views.new-message :refer [upload-button-view]]
            [retouch.core :refer [drawer-view swipe-view]]))

(defn message-view [message]
  [:div.message
   [:a.avatar
    [:img]]
   [:div.info
    [:div.nickname "username"]
    [:div.time "4:35 PM"]]
   [:div.content (:content message)]])

(defn new-message-view [thread-id]
  (let [message (r/atom "")
        group-id (subscribe [:open-group-id])]
    (fn [thread-id]
      [:div.new.message
       [upload-button-view {:thread-id "TODO"
                            :group-id "TODO"}]
       [:textarea {:value @message
                   :on-change (fn [e]
                                (reset! message (.. e -target -value)))
                   :on-something (fn [_]
                                   (dispatch [:new-message-text {:group-id @group-id
                                                                 :thread-id thread-id
                                                                 :content message}]))}]])))

(defn tag-view [tag-id]
  (let [tag (subscribe [:get-tag tag-id])]
    [:div.tag (@tag :name)]))

(defn thread-view [thread]
  [:div.thread
   [:div.card
    [:div.head
     [braid.client.ui.views.thread/thread-tags-view thread]
     [:div.close {:on-click (fn [_]
                              (dispatch [:hide-thread {:thread-id (thread :id)}]))}]]]

   [:div.messages
    (doall
      (for [message (:messages thread)]
        ^{:key (message :id)}
        [message-view message]))]

   [new-message-view {:thread-id (thread :id)}]])

(defn inbox-view []
  (let [group-id (subscribe [:open-group-id])
        threads (subscribe [:open-threads] [group-id])]
    (fn []
      [:div.inbox.page
       [:div.threads
        [swipe-view @threads thread-view]]])))

(defn main-view []
  [:div.main
   [drawer-view
    [:div.sidebar
     [braid.client.ui.views.sidebar/groups-view]]]
   [inbox-view]])

(defn login-flow-view []
  (let [method (r/atom nil) ; :login or :register
        stage (r/atom nil)  ; :email or :password
        email (r/atom nil)
        password (r/atom nil)]
    (fn []
      [:div.login-flow
       (case @method
         nil
         [:div.content.welcome
          [:img.logo {:src "/images/braid.svg"}]
          [:button.login
           {:on-click (fn [_]
                        (reset! method :login)
                        (reset! stage :email))}
           "Log In"]
          [:button.register
           {:on-click (fn [_]
                        (reset! method :register))}
           "Register"]]

         :login
         (case @stage
           :email
           [:div.content.login.email
            "Email"
            [:input.email {:placeholder "you@awesome.com"
                           :type "email"
                           :key "email"
                           :on-change (fn [e]
                                        (reset! email (.. e -target -value)))}]
            [:button.next {:on-click (fn [e]
                                       (reset! stage :password))}]]

           :password
           [:div.content.login.password
            "Password"
            [:input.password {:placeholder "••••••"
                              :type "password"
                              :key "password"
                              :on-change (fn [e]
                                           (reset! password (.. e -target -value)))}]
            [:button.next {:on-click (fn [e]
                                       (dispatch [:auth
                                                  {:email @email
                                                   :password @password
                                                   :on-complete (fn [])
                                                   :on-error (fn [])}]))}]])

         :register
         (if-not @email
           [:div.content.register.email
            [:input.email {:placeholder "you@awesome.com"}]
            [:button.next {:on-click (fn [e]
                                       (reset! email (.. e -target -value))
                                       ; TODO
                                       )}]]))])))

(defn style-view []
  [:style {:type "text/css"
           :dangerouslySetInnerHTML {:__html styles}}])

(defn app-view []
  (let [login-state (subscribe [:login-state])]
    (fn []
      [:div.app
       [style-view]
       (case @login-state
         :auth-check
         [:div.status.authenticating "Authenticating..."]

         :ws-connect
         [:div.status.connecting "Connecting..."]

         :login-form
         [login-flow-view]

         :app
         [main-view])])))


