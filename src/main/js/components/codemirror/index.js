import { EditorView, basicSetup } from "codemirror";
import { EditorState } from "@codemirror/state";
import { keymap } from "@codemirror/view";
import { codeEditorTheme } from "@/components/codemirror/theme";
import behaviorShim from "@/util/behavior-shim";

// Java language support — also used for Groovy (syntactically very similar, no CM6 Groovy parser exists)
import { java } from "@codemirror/lang-java";

// Map CM2 mode names/MIME types to CM6 language support.
// Only Java/Groovy is bundled in core. Plugins needing other languages
// can contribute additional language support.
const LANGUAGES = {
  groovy: java,
  "text/x-groovy": java,
  java: java,
  "text/x-java": java,
  clike: java,
  "text/x-csrc": java,
  "text/x-c++src": java,
  "text/x-csharp": java,
};

function getLanguageSupport(mode) {
  if (!mode) {
    return null;
  }
  const factory = LANGUAGES[mode] ?? LANGUAGES[mode.toLowerCase()];
  if (typeof factory === "function") {
    return factory();
  }
  // Unknown modes get plain text editing (no syntax highlighting)
  return null;
}

function createEditor(textarea, options) {
  const mode = options.mode;
  const lineNumbers =
    options.lineNumbers !== undefined ? options.lineNumbers : false;
  const readOnly = textarea.hasAttribute("readonly");

  const extensions = [basicSetup, codeEditorTheme];

  const languageSupport = getLanguageSupport(mode);
  if (languageSupport) {
    extensions.push(languageSupport);
  }

  if (readOnly) {
    extensions.push(EditorState.readOnly.of(true));
  }

  // Sync changes back to the textarea on every update
  extensions.push(
    EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        textarea.value = update.state.doc.toString();
        textarea.dispatchEvent(new Event("change"));
      }
    }),
  );

  if (!lineNumbers) {
    extensions.push(EditorView.lineWrapping);
  }

  const view = new EditorView({
    state: EditorState.create({
      doc: textarea.value,
      extensions: extensions,
    }),
  });

  // Handle Cmd/Ctrl+Enter to submit the form
  if (textarea.form) {
    const submitKeymap = keymap.of([
      {
        key: "Mod-Enter",
        run: () => {
          textarea.value = view.state.doc.toString();
          textarea.form.submit();
          return true;
        },
      },
    ]);
    view.dispatch({
      effects: EditorState.appendConfig.of(submitKeymap),
    });
  }

  textarea.parentNode.insertBefore(view.dom, textarea);
  textarea.style.display = "none";

  // Store reference on textarea for backward compatibility
  textarea.codemirrorObject = {
    getValue: () => view.state.doc.toString(),
    setValue: (text) => {
      view.dispatch({
        changes: { from: 0, to: view.state.doc.length, insert: text },
      });
    },
    setLine: (line, text) => {
      const lineObj = view.state.doc.line(line + 1);
      view.dispatch({
        changes: { from: lineObj.from, to: lineObj.to, insert: text },
      });
    },
    save: () => {
      textarea.value = view.state.doc.toString();
    },
    getView: () => view,
  };

  // Sync value before form submission
  if (textarea.form) {
    textarea.form.addEventListener("submit", () => {
      textarea.value = view.state.doc.toString();
    });
    textarea.form.addEventListener("jenkins:apply", () => {
      textarea.value = view.state.doc.toString();
    });
  }

  return view;
}

function init() {
  // Handle textareas with codemirror-mode attribute (from f:textarea)
  behaviorShim.specify(
    "TEXTAREA.codemirror",
    "codemirror-textarea",
    0,
    (textarea) => {
      const mode = textarea.getAttribute("codemirror-mode") || "text/x-groovy";
      let config = {};
      const configAttr = textarea.getAttribute("codemirror-config");
      if (configAttr) {
        try {
          config = JSON.parse("{" + configAttr + "}");
        } catch {
          const match = configAttr.match("^mode: ?'([^']+)'$");
          if (match) {
            config = { mode: match[1] };
          }
        }
      }
      createEditor(textarea, {
        mode: config.mode || mode,
        lineNumbers: false,
      });
    },
  );

  // Handle Script Console textareas
  behaviorShim.specify(
    "TEXTAREA.script",
    "codemirror-script-console",
    0,
    (textarea) => {
      const mode = textarea.getAttribute("script-mode") || "text/x-groovy";
      createEditor(textarea, {
        mode: mode,
        lineNumbers: true,
      });
    },
  );
}

export default { init };
