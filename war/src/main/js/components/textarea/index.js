import { EditorView, basicSetup } from "codemirror";
import { EditorState } from "@codemirror/state";
import { codeEditorTheme } from "@/components/textarea/theme";
import { languages } from "@codemirror/language-data";

function init() {
  const inputs = document.querySelectorAll("[data-type='jenkins-code-editor']");
  inputs.forEach((textarea) => {
    void generateEditorFromTextarea(textarea);
  });
}

async function generateEditorFromTextarea(textarea) {
  const textareaLanguage = textarea.dataset.codeLanguage;
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
  return view;
}

export default { init };
