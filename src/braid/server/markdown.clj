(ns braid.server.markdown
  (:require [instaparse.core :as insta]
            [clojure.string :as string]))

(def markdown-parser
  "Simple markdown parser. Only parsing enough to handle CHANGELOG.md, so lots
  is probably missing"
  (insta/parser
    "S ::= ( <#'^'> LINE <#'\\n|$'> )*
    <LINE> ::= ( HEADER | LIST ) / PLAIN_LINE
    ws ::= #'[ \\t\\x0b]*'
    <DOT> ::= #'.'
    TEXT ::= DOT *
    HEADER ::= #'#+' <ws> TEXT <ws> <'#'*>
    LIST ::= ( LIST_LINE <'\\n'> ) +
    LIST_LINE ::= <#'\\s+(-|\\*)'> <ws> TEXT
    PLAIN_LINE ::= TEXT (* TODO: add inline things *)
    "))

(defn markdown->hiccup
  "Parse markdown into hiccup."
  [md-str]
  (->> (markdown-parser md-str)
       (insta/transform {:HEADER (fn [delim rst]
                                   [(keyword (str "h" (count delim)))
                                    rst])
                         :TEXT (fn [& args]
                                 (string/join "" args))
                         :LIST (fn [& args]
                                 (vec (cons :ul args)))
                         :LIST_LINE (fn [txt] [:li txt])
                         ; TODO: group plain lines into paragraphs
                         :PLAIN_LINE identity
                         :S (fn [& args]
                              (vec (cons :div args)))})))
