import { EditorView } from "@codemirror/view";
import { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
import { tags as t } from "@lezer/highlight";

const chalky = "#e5c07b",
  coral = "var(--orange)",
  cyan = "var(--cyan)",
  invalid = "#ffffff",
  ivory = "var(--text-color)",
  stone = "var(--text-color-secondary)",
  malibu = "var(--blue)",
  sage = "var(--green)",
  whiskey = "var(--orange)",
  violet = "var(--purple)",
  background = "var(--input-color)",
  selection = "var(--selection-color)",
  cursor = "var(--text-color)";

export const theme = EditorView.theme({
  "&": {
    color: ivory,
    backgroundColor: background,
    borderRadius: "10px",
    border: "2px solid var(--input-border)",
    padding: "6px",
    boxShadow: "var(--form-input-glow)",
    transition: "var(--standard-transition), height 0s",
    resize: "vertical",
    overflow: "hidden",
    marginBottom: "var(--section-padding)",
  },
  "&:hover": {
    borderColor: "var(--input-border-hover)",
  },
  "&:focus-within": {
    borderColor: "var(--focus-input-border)",
    boxShadow: "var(--form-input-glow--focus)",
  },
  ".cm-content": {
    caretColor: cursor,
    minHeight: "200px",
  },
  ".cm-cursor, .cm-dropCursor": { borderLeftColor: cursor },
  "&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection":
    { backgroundColor: selection },
  ".cm-scroller": {
    fontFamily: "var(--font-family-mono)",
  },
  ".cm-activeLine": { backgroundColor: "#6699ff0b" },
  ".cm-selectionMatch": { backgroundColor: "#aafe661a" },
  "&.cm-focused .cm-matchingBracket, &.cm-focused .cm-nonmatchingBracket": {
    backgroundColor: "color-mix(in srgb, var(--blue) 30%, transparent)",
  },
  "&.cm-focused": {
    outline: "none",
  },
  ".cm-gutters": {
    backgroundColor: "transparent",
    color: stone,
    border: "none",
  },
  ".cm-activeLineGutter": {
    backgroundColor: "transparent",
  },
  ".cm-line": {
    backgroundColor: "transparent",
  },
  ".cm-foldPlaceholder": {
    backgroundColor: "transparent",
    border: "none",
    color: "#ddd",
  },
  ".cm-tooltip": {
    border: "none",
    borderRadius: "15px",
    background: "var(--input-color)",
    boxShadow: "var(--dropdown-box-shadow)",
    padding: "0.4rem",
  },
  ".cm-completionIcon": {
    display: "none",
  },
  ".cm-tooltip-autocomplete": {
    "& > ul > li": {
      padding: "0.4rem 0.55rem !important",
      borderRadius: "0.66rem",
      color: "var(--text-color)",
      fontFamily: "var(--font-family-mono)",
      "&:hover": {
        backgroundColor: "var(--item-background--hover)",
      },
      "&[aria-selected]": {
        backgroundColor: "var(--item-background--active)",
        color: "var(--text-color)",
      },
    },
  },
});

/// The highlighting style for code in the One Dark theme.
export const syntaxHighlight = HighlightStyle.define([
  { tag: t.keyword, color: violet },
  {
    tag: [t.deleted, t.character, t.propertyName, t.macroName],
    color: coral,
  },
  { tag: [t.function(t.variableName), t.labelName], color: malibu },
  { tag: [t.color, t.constant(t.name), t.standard(t.name)], color: whiskey },
  { tag: [t.definition(t.name), t.separator], color: ivory },
  {
    tag: [
      t.typeName,
      t.className,
      t.number,
      t.changed,
      t.annotation,
      t.modifier,
      t.self,
      t.namespace,
    ],
    color: chalky,
  },
  {
    tag: [
      t.operator,
      t.operatorKeyword,
      t.url,
      t.escape,
      t.regexp,
      t.link,
      t.special(t.string),
    ],
    color: cyan,
  },
  { tag: [t.meta, t.comment], color: stone },
  { tag: t.strong, fontWeight: "bold" },
  { tag: t.emphasis, fontStyle: "italic" },
  { tag: t.strikethrough, textDecoration: "line-through" },
  { tag: t.link, color: stone, textDecoration: "underline" },
  { tag: t.heading, fontWeight: "bold", color: coral },
  { tag: [t.atom, t.bool, t.special(t.variableName)], color: whiskey },
  { tag: [t.processingInstruction, t.string, t.inserted], color: sage },
  { tag: t.invalid, color: invalid },
]);

export const codeEditorTheme = [theme, syntaxHighlighting(syntaxHighlight)];
