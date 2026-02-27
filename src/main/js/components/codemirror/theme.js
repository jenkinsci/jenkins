import { EditorView } from "@codemirror/view";
import { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
import { tags as t } from "@lezer/highlight";

const theme = EditorView.theme({
  "&": {
    color: "var(--text-color)",
    backgroundColor: "var(--input-color)",
    borderRadius: "var(--form-input-border-radius)",
    border: "var(--jenkins-border-width) solid var(--input-border)",
    boxShadow: "var(--form-input-glow)",
    transition: "var(--standard-transition), height 0s",
    resize: "vertical",
    overflow: "hidden",
    marginBottom: "var(--section-padding)",
    width: "100%",
    maxWidth: "var(--form-item-max-width)",
  },
  "&:hover": {
    borderColor: "var(--input-border-hover)",
  },
  "&:focus-within": {
    borderColor: "var(--focus-input-border)",
    boxShadow: "var(--form-input-glow--focus)",
  },
  "&.cm-focused": {
    outline: "none",
  },
  ".cm-content": {
    caretColor: "var(--text-color)",
    fontFamily: "var(--font-family-mono)",
    lineHeight: "1.66",
    minHeight: "130px",
    padding: "var(--form-input-padding)",
  },
  ".cm-cursor, .cm-dropCursor": {
    borderLeftColor: "var(--text-color)",
  },
  "&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection":
    {
      backgroundColor: "var(--selection-color)",
    },
  ".cm-scroller": {
    fontFamily: "var(--font-family-mono)",
  },
  ".cm-activeLine": {
    backgroundColor: "transparent",
  },
  ".cm-selectionMatch": {
    backgroundColor: "#aafe661a",
  },
  "&.cm-focused .cm-matchingBracket": {
    backgroundColor: "color-mix(in srgb, var(--green) 30%, transparent)",
  },
  "&.cm-focused .cm-nonmatchingBracket": {
    backgroundColor: "color-mix(in srgb, var(--red) 30%, transparent)",
  },
  ".cm-gutters": {
    backgroundColor: "transparent",
    color: "var(--text-color-secondary)",
    border: "none",
  },
  ".cm-activeLineGutter": {
    backgroundColor: "transparent",
  },
  ".cm-foldPlaceholder": {
    backgroundColor: "transparent",
    border: "none",
    color: "var(--text-color-secondary)",
  },
  ".cm-tooltip": {
    border: "none",
    borderRadius: "var(--form-input-border-radius)",
    background: "var(--input-color)",
    boxShadow: "var(--dropdown-box-shadow)",
    padding: "0.4rem",
  },
  ".cm-tooltip-autocomplete > ul > li": {
    padding: "0.4rem 0.55rem",
    borderRadius: "0.66rem",
    color: "var(--text-color)",
    fontFamily: "var(--font-family-mono)",
  },
  ".cm-tooltip-autocomplete > ul > li:hover": {
    backgroundColor: "var(--item-background--hover)",
  },
  ".cm-tooltip-autocomplete > ul > li[aria-selected]": {
    backgroundColor: "var(--item-background--active)",
    color: "var(--text-color)",
  },
});

const syntaxHighlight = HighlightStyle.define([
  { tag: t.keyword, color: "var(--purple)" },
  { tag: [t.deleted, t.character, t.macroName], color: "var(--orange)" },
  { tag: [t.propertyName], color: "var(--text-color)" },
  {
    tag: [t.function(t.variableName), t.labelName],
    color: "var(--blue)",
  },
  {
    tag: [t.color, t.constant(t.name), t.standard(t.name)],
    color: "var(--orange)",
  },
  { tag: [t.definition(t.name), t.separator], color: "var(--text-color)" },
  {
    tag: [
      t.typeName,
      t.className,
      t.changed,
      t.annotation,
      t.modifier,
      t.self,
      t.namespace,
    ],
    color: "var(--blue)",
  },
  { tag: [t.number], color: "var(--green)" },
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
    color: "var(--text-color)",
  },
  { tag: [t.meta, t.comment], color: "var(--text-color-secondary)" },
  { tag: t.strong, fontWeight: "bold" },
  { tag: t.emphasis, fontStyle: "italic" },
  { tag: t.strikethrough, textDecoration: "line-through" },
  {
    tag: t.link,
    color: "var(--link-color)",
    textDecoration: "underline",
  },
  { tag: t.heading, fontWeight: "bold", color: "var(--blue)" },
  {
    tag: [t.atom, t.bool, t.special(t.variableName)],
    color: "var(--blue)",
  },
  {
    tag: [t.processingInstruction, t.string, t.inserted],
    color: "var(--green)",
  },
  { tag: t.invalid, color: "var(--error-color)" },
]);

export const codeEditorTheme = [theme, syntaxHighlighting(syntaxHighlight)];
