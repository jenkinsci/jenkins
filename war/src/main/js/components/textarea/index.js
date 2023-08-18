import {EditorView, basicSetup } from "codemirror"
import {EditorState} from "@codemirror/state"
import {groovy} from "@codemirror/legacy-modes/mode/groovy"
import {StreamLanguage} from "@codemirror/language"
import { oneDark } from "@/components/textarea/theme";

function editorFromTextArea(textarea, extensions) {
    let view = new EditorView({
      state: EditorState.create({doc: textarea.value, extensions: [basicSetup, StreamLanguage.define(groovy), oneDark]}),
    });
    textarea.parentNode.insertBefore(view.dom, textarea)
    textarea.style.display = "none"
    if (textarea.form) {
      textarea.form.addEventListener("submit", () => {
        textarea.value = view.state.doc.toString()
      })
    }
    return view
  }

function init() {
    const textareas = document.querySelectorAll(".script");
    textareas.forEach((textarea) => {
      const view = editorFromTextArea(textarea)
    });
}

export default { init };
