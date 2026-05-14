(function () {
  if (!window.CodeMirror) {
    return;
  }

  function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function getState(cm) {
    if (!cm._jenkinsSearchState) {
      cm._jenkinsSearchState = {
        caseSensitive: false,
        regex: false,
        wholeWord: false,
        marks: [],
        matches: [],
        selected: -1,
      };
    }
    return cm._jenkinsSearchState;
  }

  function clearMarks(state) {
    for (var i = 0; i < state.marks.length; i++) {
      state.marks[i].clear();
    }
    state.marks = [];
    state.matches = [];
    state.selected = -1;
  }

  function setPressed(button, pressed) {
    button.setAttribute("aria-pressed", pressed ? "true" : "false");
  }

  function stopEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    if (event.stopImmediatePropagation) {
      event.stopImmediatePropagation();
    }
  }

  function revealPanel(panel) {
    // Match Monaco/GitLab's find widget motion: render above the editor, then slide down.
    var reveal = function () {
      panel.classList.add("jenkins-codemirror-search--visible");
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

    panel.classList.remove("jenkins-codemirror-search--visible");
    panel.addEventListener("transitionend", transitionEnd);
    window.setTimeout(remove, 250);
  }

  function buildQuery(state, value) {
    if (!value) {
      return null;
    }

    if (state.regex) {
      try {
        return new RegExp(value, state.caseSensitive ? "g" : "gi");
      } catch (e) {
        return false;
      }
    }

    if (state.wholeWord) {
      return new RegExp(
        "\\b" + escapeRegExp(value) + "\\b",
        state.caseSensitive ? "g" : "gi",
      );
    }

    return value;
  }

  function collectMatches(cm, query, state) {
    var cursor = cm.getSearchCursor(
      query,
      { line: 0, ch: 0 },
      typeof query === "string" && !state.caseSensitive,
    );
    var matches = [];
    var guard = 0;

    while (cursor.findNext()) {
      var from = cursor.from();
      var to = cursor.to();
      if (from.line === to.line && from.ch === to.ch) {
        break;
      }
      matches.push({ from: from, to: to });
      state.marks.push(cm.markText(from, to, "CodeMirror-searching"));
      if (++guard > 5000) {
        break;
      }
    }

    return matches;
  }

  function updateCount(state) {
    var count = state.panel.querySelector(".jenkins-codemirror-search__count");
    var previous = state.panel.querySelector(
      ".jenkins-codemirror-search__previous",
    );
    var next = state.panel.querySelector(".jenkins-codemirror-search__next");
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

  function selectMatch(cm, state, index) {
    if (!state.matches.length) {
      return;
    }
    state.selected = (index + state.matches.length) % state.matches.length;
    var match = state.matches[state.selected];
    cm.setSelection(match.from, match.to);
    cm.scrollIntoView(match.from);
    updateCount(state);
  }

  function refresh(cm) {
    var state = getState(cm);
    clearMarks(state);
    state.invalid = false;

    var query = buildQuery(state, state.input.value);
    if (query === false) {
      state.invalid = true;
      updateCount(state);
      return;
    }
    if (!query) {
      updateCount(state);
      return;
    }

    cm.operation(function () {
      state.matches = collectMatches(cm, query, state);
      if (state.matches.length) {
        selectMatch(cm, state, 0);
      }
    });
    updateCount(state);
  }

  function navigate(cm, reverse) {
    var state = getState(cm);
    if (!state.panel) {
      openSearch(cm);
      return;
    }
    if (!state.matches.length) {
      refresh(cm);
      return;
    }
    selectMatch(cm, state, state.selected + (reverse ? -1 : 1));
  }

  function closeSearch(cm) {
    var state = getState(cm);
    var panel = state.panel;

    clearMarks(state);
    state.panel = null;
    state.input = null;

    if (panel) {
      removePanel(panel);
    }
    cm.focus();
  }

  function addToggle(panel, className, label, title, getValue, setValue, cm) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "jenkins-codemirror-search__button " + className;
    button.textContent = label;
    button.title = title;
    button.setAttribute("aria-label", title);
    button.setAttribute("aria-pressed", "false");
    button.addEventListener("click", function (event) {
      stopEvent(event);
      setValue(!getValue());
      setPressed(button, getValue());
      refresh(cm);
    });
    panel
      .querySelector(".jenkins-codemirror-search__actions")
      .appendChild(button);
    return button;
  }

  function openSearch(cm) {
    var state = getState(cm);
    if (state.panel) {
      state.input.focus();
      state.input.select();
      return;
    }

    var panel = document.createElement("div");
    panel.className = "jenkins-codemirror-search";
    panel.innerHTML =
      '<div class="jenkins-codemirror-search__field">' +
      '<input class="jenkins-codemirror-search__input" type="text" autocomplete="off" spellcheck="false" aria-label="Find">' +
      '<span class="jenkins-codemirror-search__count" aria-live="polite">0 of 0</span>' +
      "</div>" +
      '<div class="jenkins-codemirror-search__actions">' +
      '<button type="button" class="jenkins-codemirror-search__button jenkins-codemirror-search__previous" title="Previous match" aria-label="Previous match">&uarr;</button>' +
      '<button type="button" class="jenkins-codemirror-search__button jenkins-codemirror-search__next" title="Next match" aria-label="Next match">&darr;</button>' +
      '<button type="button" class="jenkins-codemirror-search__button jenkins-codemirror-search__close" title="Close" aria-label="Close">&times;</button>' +
      "</div>";

    cm.getWrapperElement().appendChild(panel);
    state.panel = panel;
    state.input = panel.querySelector(".jenkins-codemirror-search__input");
    revealPanel(panel);

    var actions = panel.querySelector(".jenkins-codemirror-search__actions");
    var closeButton = panel.querySelector(".jenkins-codemirror-search__close");
    actions.insertBefore(
      addToggle(
        panel,
        "jenkins-codemirror-search__case",
        "Aa",
        "Match case",
        function () {
          return state.caseSensitive;
        },
        function (value) {
          state.caseSensitive = value;
        },
        cm,
      ),
      panel.querySelector(".jenkins-codemirror-search__previous"),
    );
    actions.insertBefore(
      addToggle(
        panel,
        "jenkins-codemirror-search__whole-word",
        "ab",
        "Match whole word",
        function () {
          return state.wholeWord;
        },
        function (value) {
          state.wholeWord = value;
        },
        cm,
      ),
      panel.querySelector(".jenkins-codemirror-search__previous"),
    );
    actions.insertBefore(
      addToggle(
        panel,
        "jenkins-codemirror-search__regex",
        ".*",
        "Use regular expression",
        function () {
          return state.regex;
        },
        function (value) {
          state.regex = value;
        },
        cm,
      ),
      panel.querySelector(".jenkins-codemirror-search__previous"),
    );

    panel
      .querySelector(".jenkins-codemirror-search__previous")
      .addEventListener("click", function (event) {
        stopEvent(event);
        navigate(cm, true);
        state.input.focus();
      });
    panel
      .querySelector(".jenkins-codemirror-search__next")
      .addEventListener("click", function (event) {
        stopEvent(event);
        navigate(cm, false);
        state.input.focus();
      });
    closeButton.addEventListener("click", function (event) {
      stopEvent(event);
      closeSearch(cm);
    });

    panel.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        stopEvent(event);
        navigate(cm, event.shiftKey);
        state.input.focus();
      } else if (event.key === "Escape") {
        stopEvent(event);
        closeSearch(cm);
      }
    });

    state.input.addEventListener("input", function () {
      refresh(cm);
    });

    var selection = cm.getSelection && cm.getSelection();
    if (selection && selection.indexOf("\n") === -1) {
      state.input.value = selection;
    }

    state.input.focus();
    state.input.select();
    refresh(cm);
  }

  var originalClearSearch = CodeMirror.commands.clearSearch;
  CodeMirror.commands.find = openSearch;
  CodeMirror.commands.findNext = function (cm) {
    navigate(cm, false);
  };
  CodeMirror.commands.findPrev = function (cm) {
    navigate(cm, true);
  };
  CodeMirror.commands.clearSearch = function (cm) {
    closeSearch(cm);
    if (originalClearSearch) {
      originalClearSearch(cm);
    }
  };
})();
