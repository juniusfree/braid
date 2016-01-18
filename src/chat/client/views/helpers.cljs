(ns chat.client.views.helpers
  (:require [om.dom :as dom]
            [clojure.string :as string]
            [cljs-utils.core :refer [flip]]
            [cljs-time.format :as f]
            [cljs-time.core :as t]
            [chat.client.emoji :as emoji]))

(defn tag->color [tag]
  ; normalized is approximately evenly distributed between 0 and 1
  (let [normalized (-> (tag :id)
                       str
                       (.substring 33 36)
                       (js/parseInt 16)
                       (/ 4096))]
    (str "hsl(" (* 360 normalized) ",70%,35%)")))


(def replacements
  {:urls
   {:pattern #"(http(?:s)?://\S+(?:\w|\d|/))"
    :replace (fn [match]
               (dom/a #js {:href match :target "_blank"} match))}
   :users
   {:pattern #"@(\S*)"
    :replace (fn [match]
               (dom/span #js {:className "user-mention"} "@" match)) }
   :tags
   {:pattern #"#(\S*)"
    :replace (fn [match]
               (dom/span #js {:className "tag-mention"} "#" match))}
   :emoji-shortcodes
   {:pattern #"(:\S*:)"
    :replace (fn [match]
               (if (emoji/unicode match)
                 (emoji/shortcode->html match)
                 match))}
   :emoji-ascii
   {:replace (fn [match]
               (if-let [shortcode (emoji/ascii match)]
                 (emoji/shortcode->html shortcode)
                 match))}
   })

(defn text-replacer
  [match-type]
  (fn [text-or-node]
    (if (string? text-or-node)
      (let [text text-or-node
            pattern (get-in replacements [match-type :pattern])]
        (if-let [match (second (re-find pattern text))]
          ((get-in replacements [match-type :replace]) match)
          text))
      text-or-node)))

(def url-replace (text-replacer :urls))
(def user-replace (text-replacer :users))
(def tag-replace (text-replacer :tags))
(def emoji-shortcodes-replace (text-replacer :emoji-shortcodes))

(defn emoji-ascii-replace [text-or-node]
  (if (string? text-or-node)
    (let [text text-or-node]
      (if (contains? emoji/ascii-set text)
        ((get-in replacements [:emoji-ascii :replace]) text)
        text))
    text-or-node))

(defn extract-code-blocks
  "Return a transducer to processs the sequence of words into multiline code blocks"
  []
  (fn [xf]
    (let [state (volatile! ::start)
          block-type (volatile! nil)
          in-code (volatile! [])]
      (fn
        ([] (xf))
        ([result] (if (= @state ::in-code)
                    (let [prefix (if (= @block-type :block) "```" "`")]
                      (reduce xf result (update-in @in-code [0] (partial str prefix))))
                    (xf result)))
        ([result input]
         (if (string? input)
           (cond
             ; TODO: handle starting code block with backtick not at beginning of word
             ; start multiline
             (and (= @state ::start) (.startsWith input "```"))
             (if (and (not= "```" input) (.endsWith input "```"))
               (xf result (dom/code #js {:className "multiline-code"} (.slice input 3 (- (.-length input) 3))))
               (do (vreset! state ::in-code)
                   (vreset! block-type :block)
                   (vswap! in-code conj (.slice input 3))
                   result))

             ; start inline
             (and (= @state ::start) (.startsWith input "`"))
             (if (and (not= input "`") (.endsWith input "`"))
               (xf result (dom/code #js {:className "inline-code"} (.slice input 1 (dec (.-length input)))))
               (do (vreset! state ::in-code)
                   (vswap! in-code conj (.slice input 1))
                   result))

             ; end multiline
             (and (= @state ::in-code) (= @block-type :block) (.endsWith input "```"))
             (let [code (conj @in-code (.slice input 0 (- (.-length input) 3)))]
               (vreset! state ::start)
               (vreset! in-code [])
               (xf result (dom/code #js {:className "multiline-code"} (string/join " " code))))

             ; end inline
             (and (= @state ::in-code) (.endsWith input "`"))
             (let [code (conj @in-code (.slice input 0 (dec (.-length input))))]
               (vreset! state ::start)
               (vreset! in-code [])
               (xf result (dom/code #js {:className "inline-code"} (string/join " " code))))

             ; both
             (= @state ::in-code) (do (vswap! in-code conj input) result)

             :else (xf result input))
           (xf result input)))))))

(defn format-message
  "Given the text of a message body, turn it into dom nodes, making urls into
  links"
  [text]
  (let [stateless-transform (map (comp
                                   url-replace
                                   user-replace
                                   tag-replace
                                   emoji-shortcodes-replace
                                   emoji-ascii-replace))
        statefull-transform (extract-code-blocks)]
    (->> (into [] (comp statefull-transform stateless-transform) (string/split text #" "))
         (interleave (repeat " "))
         rest)))

(defn format-date
  "Turn a Date object into a nicely formatted string"
  [datetime]
  (let [datetime (t/to-default-time-zone datetime)
        now (t/to-default-time-zone (t/now))
        format (cond
                 (= (f/unparse (f/formatter "yyyydM") now)
                    (f/unparse (f/formatter "yyyydM") datetime))
                 "h:mm A"

                 (= (t/year now) (t/year datetime))
                 "h:mm A MMM d"

                 :else
                 "h:mm A MMM d yyyy")]
    (f/unparse (f/formatter format) datetime)))
