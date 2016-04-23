(ns braid.ui.views.pages.group-explore
  (:require [chat.client.dispatcher :refer [dispatch!]])
  (:import [goog.events KeyCodes]))

(defn group-explore-page-view
  []
  [:div.page.group-explore
   [:div.title "Group Explore"]
   [:div.content
    [:p "One day, you'll be able to see various open groups that may be worth exploring here."]

    [:h2 "Create Group"]
    [:div.new-group
     [:label "New Group"
      [:input {:placeholder "Group Name"
               :on-keydown
               (fn [e]
                 (when (= KeyCodes.ENTER e.keyCode)
                   (.preventDefault e)
                   (let [group-name (.. e -target -value)]
                     (dispatch! :create-group {:name group-name})
                     (set! (.. e -target -value) ""))))}]]]]])