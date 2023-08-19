import { EditorView, basicSetup } from "codemirror";
import { EditorState } from "@codemirror/state";
import { codeEditorTheme } from "@/components/textarea/theme";
import { languages } from "@codemirror/language-data";
import behaviorShim from "@/util/behavior-shim";

function init() {
  behaviorShim.specify(
    "[data-type='jenkins-code-editor']",
    "jenkins-code-editor",
    0,
    (textarea) => {
      void generateEditorFromTextarea(textarea);
    },
  );
}

async function generateEditorFromTextarea(textarea) {
  const textareaLanguage = textarea.dataset.codeLanguage;
  // const textareaOptions = textarea.dataset.codeOptions;
  const language = languages.find((e) => e.alias.includes(textareaLanguage));
  const loadedLanguage = await language.load();

  const view = new EditorView({
    state: EditorState.create({
      doc: textarea.value,
      extensions: [basicSetup, loadedLanguage.language, codeEditorTheme],
    }),
  });

  textarea.parentNode.insertBefore(view.dom, textarea);
  textarea.style.display = "none";
  if (textarea.form) {
    textarea.form.addEventListener("submit", () => {
      textarea.value = view.state.doc.toString();
    });
  }

  // Make sure CodeMirror is synced with any manual height changes
  const resizeObserver = new ResizeObserver(() => {
    view.requestMeasure();
  });
  resizeObserver.observe(view.dom);

  return view;
}

export default { init };
