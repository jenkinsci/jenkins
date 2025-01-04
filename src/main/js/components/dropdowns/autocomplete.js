import behaviorShim from "@/util/behavior-shim";
import Utils from "@/components/dropdowns/utils";

function init() {
  function addValue(value, item, delimiter) {
    const prev = value.includes(delimiter)
      ? value.substring(0, value.lastIndexOf(delimiter) + 1) + " "
      : "";
    return prev + item + delimiter + " ";
  }

  function convertSuggestionToItem(suggestion, e) {
    const delimiter = e.getAttribute("autoCompleteDelimChar");
    const confirm = () => {
      e.value = delimiter
        ? addValue(e.value, suggestion.name, delimiter)
        : suggestion.name;
      Utils.validateDropdown(e);
      e.focus();
    };
    return {
      label: suggestion.name,
      onClick: confirm,
      onKeyPress: (evt) => {
        if (evt.key === "Tab") {
          confirm();
          e.dropdown.hide();
          evt.preventDefault();
        }
      },
    };
  }

  function createAndShowDropdown(e, suggestions) {
    const items = suggestions
      .splice(0, Utils.getMaxSuggestionCount(e, 10))
      .map((s) => convertSuggestionToItem(s, e));
    if (!e.dropdown) {
      Utils.generateDropdown(
        e,
        (instance) => {
          e.dropdown = instance;
          instance.popper.style.minWidth = e.offsetWidth + "px";
        },
        true,
      );
    }
    e.dropdown.setContent(Utils.generateDropdownItems(items, true));
    e.dropdown.show();
  }

  function updateSuggestions(e) {
    const text = e.value.trim();
    const delimiter = e.getAttribute("autoCompleteDelimChar");
    const word = delimiter ? text.split(delimiter).reverse()[0].trim() : text;
    if (!word) {
      if (e.dropdown) {
        e.dropdown.hide();
      }
      return;
    }

    const url = e.getAttribute("autoCompleteUrl");

    const depends = e.getAttribute("fillDependsOn");
    const q = qs(e).addThis();
    if (depends && depends.length > 0) {
      depends.split(" ").forEach(
        TryEach(function (n) {
          q.nearBy(n);
        }),
      );
    }

    const queryString = q.toString();
    const idx = queryString.indexOf("?");
    const parameters = queryString.substring(idx + 1);

    fetch(url, {
      method: "post",
      headers: crumb.wrap({
        "Content-Type": "application/x-www-form-urlencoded",
      }),
      body: parameters,
    })
      .then((rsp) => (rsp.ok ? rsp.json() : {}))
      .then((response) => createAndShowDropdown(e, response.suggestions || []));
  }

  behaviorShim.specify(
    "INPUT.auto-complete",
    "input-auto-complete",
    0,
    function (e) {
      e.setAttribute("autocomplete", "off");
      e.dataset["hideOnClick"] = "false";
      // form field with auto-completion support
      e.style.position = "relative";
      // otherwise menu won't hide on tab with nothing selected
      // needs delay as without that it blocks click selection of an item
      e.addEventListener("focusout", () =>
        setTimeout(() => e.dropdown && e.dropdown.hide(), 200),
      );
      e.addEventListener(
        "input",
        Utils.debounce(() => {
          updateSuggestions(e);
        }),
      );
    },
  );
}

export default { init };
