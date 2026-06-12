import behaviorShim from "@/util/behavior-shim";
import Utils from "@/components/dropdowns/utils";

function init() {
  function convertSuggestionToItem(suggestion, e) {
    const confirm = () => {
      e.value = suggestion.name;
      Utils.validateDropdown(e);
      e.focus();
    };
    return {
      displayName: suggestion.name,
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
      .splice(0, Utils.getMaxSuggestionCount(e, 20))
      .map((s) => convertSuggestionToItem(s, e));
    if (!e.dropdown) {
      Utils.generateDropdown(
        e,
        (instance) => {
          e.dropdown = instance;
        },
        true,
        {
          trigger: "manual",
          hideOnClick: false,
          appendTo: "parent",
        },
      );
    }
    e.dropdown.setContent(Utils.generateDropdownItems(items, true));
    e.dropdown.show();
  }

  function updateSuggestions(e, items) {
    const text = e.value.trim();

    let filteredItems = text
      ? items.filter((item) => item.indexOf(text) === 0)
      : items;

    const suggestions = filteredItems
      .filter((item) => item.indexOf(text) === 0)
      .map((item) => {
        return { name: item };
      });
    createAndShowDropdown(e, suggestions || []);
  }

  behaviorShim.specify("INPUT.combobox2", "combobox", 100, function (e) {
    // form field with auto-completion support
    // insert the auto-completion container
    refillOnChange(e, function (params) {
      e.style.position = "relative";

      const url = e.getAttribute("fillUrl");
      fetch(url, {
        headers: crumb.wrap({
          "Content-Type": "application/x-www-form-urlencoded",
        }),
        method: "post",
        body: new URLSearchParams(params),
      })
        .then((rsp) => (rsp.ok ? rsp.json() : {}))
        .then((items) => {
          e.addEventListener("focus", () => updateSuggestions(e, items));

          // otherwise menu won't hide on tab with nothing selected
          // needs delay as without that it blocks click selection of an item
          e.addEventListener("focusout", () =>
            setTimeout(() => e.dropdown.hide(), 200),
          );

          e.addEventListener(
            "input",
            Utils.debounce(() => {
              updateSuggestions(e, items);
            }),
          );
        });
    });
  });
}

export default { init };
