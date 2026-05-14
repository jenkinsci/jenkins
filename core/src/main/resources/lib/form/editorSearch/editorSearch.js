(function () {
  if (window.jenkinsEditorSearch) {
    return;
  }

  function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function closest(element, selector) {
    while (element && element.nodeType === 1) {
      if (element.matches(selector)) {
        return element;
      }
      element = element.parentElement;
    }
    return null;
  }

  function stopEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    if (event.stopImmediatePropagation) {
      event.stopImmediatePropagation();
    }
  }

  function setPressed(button, pressed) {
    button.setAttribute("aria-pressed", pressed ? "true" : "false");
  }

  function revealPanel(panel) {
    var reveal = function () {
      panel.classList.add("jenkins-editor-search--visible");
    };

    if (window.requestAnimationFrame) {
      window.requestAnimationFrame(reveal);
    } else {
      reveal();
    }
  }

  function removePanel(panel) {
    var removed = false;
    var remove = function () {
      if (removed) {
        return;
      }
      removed = true;
      if (panel.parentNode) {
        panel.parentNode.removeChild(panel);
      }
    };
    var transitionEnd = function (event) {
      if (event.target === panel) {
        panel.removeEventListener("transitionend", transitionEnd);
        remove();
      }
    };

    panel.classList.remove("jenkins-editor-search--visible");
    panel.addEventListener("transitionend", transitionEnd);
    window.setTimeout(remove, 250);
  }

  function createRegex(state) {
    var value = state.input.value;
    var flags = state.caseSensitive ? "g" : "gi";

    if (!value) {
      return null;
    }

    if (!state.regex) {
      value = escapeRegExp(value);
      if (state.wholeWord) {
        value = "\\b" + value + "\\b";
      }
    }

    try {
      return new RegExp(value, flags);
    } catch {
      return false;
    }
  }

  function collectMatches(value, regex) {
    var matches = [];
    var match;
    var guard = 0;

    regex.lastIndex = 0;
    while ((match = regex.exec(value)) !== null) {
      if (match[0].length === 0) {
        regex.lastIndex += 1;
        continue;
      }
      matches.push({
        index: match.index,
        length: match[0].length,
      });
      if (++guard > 5000) {
        break;
      }
    }
    return matches;
  }

  function updateCount(state) {
    var count = state.panel.querySelector(".jenkins-editor-search__count");
    var previous = state.panel.querySelector(
      ".jenkins-editor-search__previous",
    );
    var next = state.panel.querySelector(".jenkins-editor-search__next");
    var total = state.matches.length;

    if (state.invalid) {
      count.textContent = "Invalid";
    } else if (!state.input.value) {
      count.textContent = "0 of 0";
    } else {
      count.textContent = total
        ? state.selected + 1 + " of " + total
        : "0 of 0";
    }

    previous.disabled = next.disabled = total === 0;
  }

  function clearMatches(state) {
    state.adapter.clearMarks(state);
    state.matches = [];
    state.selected = -1;
  }

  function selectMatch(state, index) {
    if (!state.matches.length) {
      return;
    }

    state.selected = (index + state.matches.length) % state.matches.length;
    state.adapter.selectMatch(state.matches[state.selected]);
    updateCount(state);
  }

  function refresh(state) {
    var regex;

    clearMatches(state);
    state.invalid = false;
    regex = createRegex(state);

    if (regex === false) {
      state.invalid = true;
      updateCount(state);
      return;
    }
    if (!regex) {
      updateCount(state);
      return;
    }

    state.matches = collectMatches(state.adapter.getValue(), regex);
    state.adapter.markMatches(state);
    if (state.matches.length) {
      selectMatch(state, 0);
    }
    updateCount(state);
  }

  function navigate(state, reverse) {
    if (!state.matches.length) {
      refresh(state);
      return;
    }
    selectMatch(state, state.selected + (reverse ? -1 : 1));
  }

  function closeSearch(state) {
    var panel = state.panel;

    clearMatches(state);
    state.panel = null;
    state.input = null;

    if (panel) {
      removePanel(panel);
    }
    state.adapter.focus();
  }

  function addToggle(state, className, label, title, getValue, setValue) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "jenkins-editor-search__button " + className;
    button.textContent = label;
    button.title = title;
    button.setAttribute("aria-label", title);
    button.setAttribute("aria-pressed", "false");
    button.addEventListener("click", function (event) {
      stopEvent(event);
      setValue(!getValue());
      setPressed(button, getValue());
      refresh(state);
    });
    state.panel
      .querySelector(".jenkins-editor-search__actions")
      .appendChild(button);
    return button;
  }

  function createPanel(state) {
    var panel = document.createElement("div");
    panel.className = "jenkins-editor-search";
    panel.innerHTML =
      '<div class="jenkins-editor-search__field">' +
      '<input class="jenkins-editor-search__input" type="text" autocomplete="off" spellcheck="false" aria-label="Find">' +
      '<span class="jenkins-editor-search__count" aria-live="polite">0 of 0</span>' +
      "</div>" +
      '<div class="jenkins-editor-search__actions">' +
      '<button type="button" class="jenkins-editor-search__button jenkins-editor-search__previous" title="Previous match" aria-label="Previous match">&uarr;</button>' +
      '<button type="button" class="jenkins-editor-search__button jenkins-editor-search__next" title="Next match" aria-label="Next match">&darr;</button>' +
      '<button type="button" class="jenkins-editor-search__button jenkins-editor-search__close" title="Close" aria-label="Close">&times;</button>' +
      "</div>";

    panel.jenkinsEditorSearchState = state;
    state.adapter.wrapper.appendChild(panel);
    state.panel = panel;
    state.input = panel.querySelector(".jenkins-editor-search__input");
    revealPanel(panel);
  }

  function wirePanel(state) {
    var actions = state.panel.querySelector(".jenkins-editor-search__actions");
    var closeButton = state.panel.querySelector(
      ".jenkins-editor-search__close",
    );
    var previousButton = state.panel.querySelector(
      ".jenkins-editor-search__previous",
    );

    actions.insertBefore(
      addToggle(
        state,
        "jenkins-editor-search__case",
        "Aa",
        "Match case",
        function () {
          return state.caseSensitive;
        },
        function (value) {
          state.caseSensitive = value;
        },
      ),
      previousButton,
    );
    actions.insertBefore(
      addToggle(
        state,
        "jenkins-editor-search__whole-word",
        "ab",
        "Match whole word",
        function () {
          return state.wholeWord;
        },
        function (value) {
          state.wholeWord = value;
        },
      ),
      previousButton,
    );
    actions.insertBefore(
      addToggle(
        state,
        "jenkins-editor-search__regex",
        ".*",
        "Use regular expression",
        function () {
          return state.regex;
        },
        function (value) {
          state.regex = value;
        },
      ),
      previousButton,
    );

    previousButton.addEventListener("click", function (event) {
      stopEvent(event);
      navigate(state, true);
      state.input.focus();
    });
    state.panel
      .querySelector(".jenkins-editor-search__next")
      .addEventListener("click", function (event) {
        stopEvent(event);
        navigate(state, false);
        state.input.focus();
      });
    closeButton.addEventListener("click", function (event) {
      stopEvent(event);
      closeSearch(state);
    });

    state.panel.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        stopEvent(event);
        navigate(state, event.shiftKey);
        state.input.focus();
      } else if (event.key === "Escape") {
        stopEvent(event);
        closeSearch(state);
      }
    });

    state.input.addEventListener("input", function () {
      refresh(state);
    });
  }

  function getState(adapter) {
    var wrapper = adapter.wrapper;
    if (!wrapper.jenkinsEditorSearchState) {
      wrapper.jenkinsEditorSearchState = {
        adapter: adapter,
        caseSensitive: false,
        regex: false,
        wholeWord: false,
        marks: [],
        matches: [],
        selected: -1,
      };
    }
    wrapper.jenkinsEditorSearchState.adapter = adapter;
    return wrapper.jenkinsEditorSearchState;
  }

  function openSearch(adapter) {
    var state = getState(adapter);
    var selection;

    if (state.panel) {
      state.input.focus();
      state.input.select();
      return;
    }

    createPanel(state);
    wirePanel(state);

    selection = adapter.getSelection();
    if (selection && selection.indexOf("\n") === -1) {
      state.input.value = selection;
    }

    state.input.focus();
    state.input.select();
    refresh(state);
  }

  function createCodeMirrorAdapter(wrapper) {
    var cm = wrapper.codemirrorObject || wrapper.CodeMirror;
    if (!cm) {
      return null;
    }

    return {
      wrapper: wrapper,
      getValue: function () {
        return cm.getValue();
      },
      getSelection: function () {
        return cm.getSelection ? cm.getSelection() : "";
      },
      clearMarks: function (state) {
        for (var i = 0; i < state.marks.length; i++) {
          state.marks[i].clear();
        }
        state.marks = [];
      },
      markMatches: function (state) {
        for (var i = 0; i < state.matches.length; i++) {
          var match = state.matches[i];
          state.marks.push(
            cm.markText(
              cm.posFromIndex(match.index),
              cm.posFromIndex(match.index + match.length),
              "jenkins-editor-search__match",
            ),
          );
        }
      },
      selectMatch: function (match) {
        var from = cm.posFromIndex(match.index);
        var to = cm.posFromIndex(match.index + match.length);
        cm.setSelection(from, to);
        cm.scrollIntoView(from);
      },
      focus: function () {
        cm.focus();
      },
    };
  }

  function getAceRange(editor, start, end) {
    var Range =
      window.ace && window.ace.require && window.ace.require("ace/range").Range;

    if (!Range) {
      Range = editor.getSelectionRange().constructor;
    }
    return new Range(start.row, start.column, end.row, end.column);
  }

  function createAceAdapter(wrapper) {
    var editor = wrapper.aceEditor;
    if (!editor || !editor.session || !editor.session.doc) {
      return null;
    }

    return {
      wrapper: wrapper,
      getValue: function () {
        return editor.getValue();
      },
      getSelection: function () {
        return editor.getSelectedText ? editor.getSelectedText() : "";
      },
      clearMarks: function (state) {
        for (var i = 0; i < state.marks.length; i++) {
          editor.session.removeMarker(state.marks[i]);
        }
        state.marks = [];
      },
      markMatches: function (state) {
        for (var i = 0; i < state.matches.length; i++) {
          var match = state.matches[i];
          var from = editor.session.doc.indexToPosition(match.index, 0);
          var to = editor.session.doc.indexToPosition(
            match.index + match.length,
            0,
          );
          state.marks.push(
            editor.session.addMarker(
              getAceRange(editor, from, to),
              "jenkins-editor-search__match",
              "text",
            ),
          );
        }
      },
      selectMatch: function (match) {
        var from = editor.session.doc.indexToPosition(match.index, 0);
        var to = editor.session.doc.indexToPosition(
          match.index + match.length,
          0,
        );
        editor.selection.setSelectionRange(getAceRange(editor, from, to));
        editor.renderer.scrollCursorIntoView();
      },
      focus: function () {
        editor.focus();
      },
    };
  }

  function findAdapterFromElement(element) {
    var wrapper;
    if (!element || element.nodeType !== 1) {
      return null;
    }

    wrapper = closest(element, ".CodeMirror");
    if (wrapper) {
      return createCodeMirrorAdapter(wrapper);
    }

    wrapper = closest(element, ".ace_editor");
    if (wrapper) {
      return createAceAdapter(wrapper);
    }

    return null;
  }

  function isFindShortcut(event) {
    return (
      (event.ctrlKey || event.metaKey) &&
      !event.altKey &&
      event.key &&
      event.key.toLowerCase() === "f"
    );
  }

  document.addEventListener(
    "keydown",
    function (event) {
      var panel;
      var adapter;

      if (!isFindShortcut(event)) {
        return;
      }

      panel = closest(document.activeElement, ".jenkins-editor-search");
      if (panel && panel.jenkinsEditorSearchState) {
        stopEvent(event);
        panel.jenkinsEditorSearchState.input.focus();
        panel.jenkinsEditorSearchState.input.select();
        return;
      }

      adapter = findAdapterFromElement(document.activeElement);
      if (!adapter) {
        return;
      }

      stopEvent(event);
      openSearch(adapter);
    },
    true,
  );

  window.jenkinsEditorSearch = {
    findAdapterFromElement: findAdapterFromElement,
    openSearch: openSearch,
  };
})();
