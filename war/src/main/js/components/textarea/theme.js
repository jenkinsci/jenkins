import {EditorView} from "@codemirror/view"
import {HighlightStyle, syntaxHighlighting} from "@codemirror/language"
import {tags as t} from "@lezer/highlight"

const chalky = "#e5c07b",
  coral = "var(--red)",
  cyan = "var(--cyan)",
  invalid = "#ffffff",
  ivory = "#abb2bf",
  stone = "var(--text-color-secondary)",
  malibu = "var(--blue)",
  sage = "var(--green)",
  whiskey = "var(--orange)",
  violet = "var(--purple)",
  darkBackground = "#21252b",
  highlightBackground = "#2c313a",
  background = "var(--input-color)",
  tooltipBackground = "#353a42",
  selection = "var(--selection-color)",
  cursor = "var(--text-color)"

/// The editor theme styles for One Dark.
export const oneDarkTheme = EditorView.theme({
  "&": {
    color: ivory,
    backgroundColor: background,
    borderRadius: "10px",
    border: "2px solid var(--input-border)",
    padding: "6px",
    boxShadow: 'var(--form-input-glow)',
    transition: 'var(--standard-transition)',
  },
  "&:hover": {
    borderColor: 'var(--input-border-hover)',
  },
  "&:focus-within": {
    borderColor: 'var(--focus-input-border)',
    boxShadow: 'var(--form-input-glow--focus)'
  },
  ".cm-content": {
    caretColor: cursor
  },
  ".cm-cursor, .cm-dropCursor": { borderLeftColor: cursor },
  "&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection": { backgroundColor: selection },
  ".cm-panels": { backgroundColor: darkBackground, color: ivory },
  ".cm-panels.cm-panels-top": { borderBottom: "2px solid black" },
  ".cm-panels.cm-panels-bottom": { borderTop: "2px solid black" },
  ".cm-searchMatch": {
    backgroundColor: "#72a1ff59",
    outline: "1px solid #457dff"
  },
  ".cm-searchMatch.cm-searchMatch-selected": {
    backgroundColor: "#6199ff2f"
  },
  ".cm-scroller": {
    fontFamily: 'var(--font-family-mono)'
  },
  ".cm-activeLine": { backgroundColor: "#6699ff0b" },
  ".cm-selectionMatch": { backgroundColor: "#aafe661a" },
  "&.cm-focused .cm-matchingBracket, &.cm-focused .cm-nonmatchingBracket": {
    backgroundColor: "#bad0f847"
  },
  "&.cm-focused": {
    outline: "none",
  },
  ".cm-gutters": {
    backgroundColor: "transparent",
    color: stone,
    border: "none"
  },
  ".cm-activeLineGutter": {
    backgroundColor: "transparent"
  },
  ".cm-line": {
    backgroundColor: "transparent",
  },
  ".cm-foldPlaceholder": {
    backgroundColor: "transparent",
    border: "none",
    color: "#ddd"
  },
  ".cm-tooltip": {
    border: "none",
    backgroundColor: tooltipBackground
  },
  ".cm-tooltip .cm-tooltip-arrow:before": {
    borderTopColor: "transparent",
    borderBottomColor: "transparent"
  },
  ".cm-tooltip .cm-tooltip-arrow:after": {
    borderTopColor: tooltipBackground,
    borderBottomColor: tooltipBackground
  },
  ".cm-tooltip-autocomplete": {
    "& > ul > li[aria-selected]": {
      backgroundColor: highlightBackground,
      color: ivory
    }
  }
})

/// The highlighting style for code in the One Dark theme.
export const oneDarkHighlightStyle = HighlightStyle.define([
  {tag: t.keyword,
    color: violet},
  {tag: [t.name, t.deleted, t.character, t.propertyName, t.macroName],
    color: coral},
  {tag: [t.function(t.variableName), t.labelName],
    color: malibu},
  {tag: [t.color, t.constant(t.name), t.standard(t.name)],
    color: whiskey},
  {tag: [t.definition(t.name), t.separator],
    color: ivory},
  {tag: [t.typeName, t.className, t.number, t.changed, t.annotation, t.modifier, t.self, t.namespace],
    color: chalky},
  {tag: [t.operator, t.operatorKeyword, t.url, t.escape, t.regexp, t.link, t.special(t.string)],
    color: cyan},
  {tag: [t.meta, t.comment],
    color: stone},
  {tag: t.strong,
    fontWeight: "bold"},
  {tag: t.emphasis,
    fontStyle: "italic"},
  {tag: t.strikethrough,
    textDecoration: "line-through"},
  {tag: t.link,
    color: stone,
    textDecoration: "underline"},
  {tag: t.heading,
    fontWeight: "bold",
    color: coral},
  {tag: [t.atom, t.bool, t.special(t.variableName)],
    color: whiskey },
  {tag: [t.processingInstruction, t.string, t.inserted],
    color: sage},
  {tag: t.invalid,
    color: invalid},
])

export const oneDark = [oneDarkTheme, syntaxHighlighting(oneDarkHighlightStyle)]
