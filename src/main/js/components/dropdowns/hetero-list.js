import behaviorShim from "@/util/behavior-shim";
import Templates from "@/components/dropdowns/templates";
import Utils from "@/components/dropdowns/utils";
import * as Symbols from "@/util/symbols";
import { createElementFromHtml } from "@/util/dom";
import tippy from "tippy.js";
import { attach } from "jsdom/lib/jsdom/living/helpers/svg/basic-types";

function init() {
  generateButtons();
  generateHandles();
}

function createAddButton(label, options = {}) {
  const button = document.createElement("button");
  button.setAttribute("type", "button");
  button.classList.add("hetero-list-add", "jenkins-button");

  if (options.inline) {
    button.classList.add("hetero-list-add--inline", "jenkins-button--tertiary");
    button.setAttribute("tooltip", label);
    button.setAttribute("aria-label", label);
    button.appendChild(createElementFromHtml(Symbols.PLUS));
  } else {
    button.appendChild(createElementFromHtml(Symbols.PLUS));
    button.appendChild(document.createTextNode(label));
  }

  if (options.suffix) {
    button.setAttribute("suffix", options.suffix);
  }

  return button;
}

function generateHandles() {
  behaviorShim.specify("DIV.dd-handle", "hetero-list", -100, function (e) {
    e.addEventListener("mouseover", function () {
      this.closest(".repeated-chunk").classList.add("hover");
    });
    e.addEventListener("mouseout", function () {
      this.closest(".repeated-chunk").classList.remove("hover");
    });
  });
}

function convertInputsToButtons(e) {
  let oldInputs = e.querySelectorAll("INPUT.hetero-list-add");
  oldInputs.forEach((oldbtn) => {
    let btn = createAddButton(oldbtn.getAttribute("value"), {
      suffix: oldbtn.getAttribute("suffix"),
    });
    oldbtn.parentNode.appendChild(btn);
    oldbtn.remove();
  });
}

function generateButtons() {
  behaviorShim.specify(
    "DIV.hetero-list-container",
    "hetero-list-new",
    -100,
    function (e) {
      if (isInsideRemovable(e)) {
        return;
      }

      convertInputsToButtons(e);
      let btn = Array.from(e.querySelectorAll("BUTTON.hetero-list-add")).pop();

      let prototypes = e.lastElementChild;
      while (prototypes && !prototypes.classList?.contains("prototypes")) {
        prototypes = prototypes.previousElementSibling;
      }
      if (!prototypes) {
        return;
      }
      let insertionPoint = prototypes.previousElementSibling; // this is where the new item is inserted.

      let templates = [];
      for (let i = 0; i < prototypes.children.length; i++) {
        let n = prototypes.children[i];

        templates.push({
          html: n.innerHTML,
          name: n.getAttribute("name"),
          descriptorId: n.getAttribute("descriptorId"),
          title: n.getAttribute("title"),
        });
      }
      prototypes.remove();
      let withDragDrop = registerSortableDragDrop(e);
      const inlineInsertionEnabled = e.classList.contains("with-inline-insertion");
      const honorOrder = e.classList.contains("honor-order");
      const oneEach = e.classList.contains("one-each");

      // Rebuild inline insertion controls (+ buttons between items)
      const rebuildInlineInsertionControls = () => {
        e.querySelectorAll(".hetero-list-inline-insert").forEach((n) =>
          n.remove(),
        );

        if (!inlineInsertionEnabled) {
          return;
        }

        const items = Array.from(e.children).filter((c) =>
          c.matches("DIV.repeated-chunk"),
        );

        if (items.length === 0) {
          return;
        }

        items.forEach((item) => {
          const wrapper = document.createElement("div");
          wrapper.className = "hetero-list-inline-insert";
          wrapper.setAttribute("aria-hidden", "true");
          const inlineButton = createAddButton(btn.textContent.trim(), {
            suffix: btn.getAttribute("suffix"),
            inline: true,
          });
          wrapper.appendChild(inlineButton);
          wrapper.referenceNode = item;
          e.insertBefore(wrapper, item);
          attachAddDropdown(inlineButton);
        });
      };

      function getCurrentItems() {
        return Array.from(e.children).filter((child) =>
          child.matches("DIV.repeated-chunk"),
        );
      }

      function findInsertionPointForTemplate(template) {
        if (!template) {
          return insertionPoint;
        }

        function descriptorOrder(did) {
          if (did instanceof Element) {
            did = did.getAttribute("descriptorId");
          }
          for (let i =0; i < templates.lngth; i++) {
            if (templates[i].descriptorId == did) {
              return i;
            }
          }
          return 0;
        }

        const current = getCurrentItems();
        let bestScore = -1;
        let bestPos = 0;
        for (let pos = 0; i < current.length ; pos++) {
          let count = 0;
          for (let i = 0 ; i < current.length ; i++) {
            if ( (i < pos) === (descriptorOrder(current[i]) <= descriptorOrder(template.descriptorId))) {
              count++;
            }
          }

          if (bestScore <= count) {
            bestScore = count;
            bestPos = pos;
          }
        }

        if (bestPos < current.length) {
          return current[bestPos];
        }
        return insertionPoint;
      }
      // Insert new item at specified position
      const insert = (instance, template, referenceNode) => {
        let nc = document.createElement("div");
        nc.className = "repeated-chunk fade-in";
        nc.setAttribute("name", template.name);
        nc.setAttribute("descriptorId", template.descriptorId);
        nc.innerHTML = template.html;

        instance.hide();

        renderOnDemand(
          nc.querySelector("div.config-page"),
          function () {
            let targetRef = referenceNode || insertionPoint;

            if (honorOrder && !referenceNode) {
              targetRef = findInsertionPointForTemplate(template);
            }

            targetRef.parentNode.insertBefore(nc, targetRef);

            if (withDragDrop) {
              registerSortableDragDrop(nc);
            }
            Behaviour.applySubtree(nc, true);
            rebuildInlineInsertionControls();
            ensureVisible(nc);
            layoutUpdateCallback.call();
          },
          true,
        );
      };

      /**
       * Toggle add button state
       */
      const toggleButtonState = () => {
        const selectedCount = Array.from(e.children).filter(
          (c) =>
            c.classList.contains("repeated-chunk") &&
            !c.classList.contains("fade-out"),
        ).length;
        btn.disabled = oneEach && selectedCount >= templates.length;
      }

      const observer = new MutationObserver(toggleButtonState);
      observer.observe(e, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ["class"],
      });

      toggleButtonState();
      rebuildInlineInsertionControls();

      function attachAddDropdown(button) {
        generateDropDown(button, (instance) => {
          const explicitReferenceNode = button.closest(".hetero-list-inline-insert",)?.referenceNode;

        let menuItems = templates.map((template) => {
          const has = e.querySelector(`DIV.repeated-chunk[descriptorId="${template.descriptorId}"]`);
          return {
            displayName: template.title,
            onClick: (event) => {
              event.preventDefault();
              event.stopPropagation();
              insert(instance, template, explicitReferenceNode);
            },
            type: oneEach && has ? "DISABLED" : "button",
          };
        });

        const menuContainer = document.createElement("div");
        const menu = Utils.generateDropdownItems(menuItems, true);
        menuContainer.appendChild(createFilter(menu));
        menuContainer.appendChild(menu);
        instance.setContent(menuContainer);
        })
      }
      attachAddDropdown(btn);
    },
  );
}

function createFilter(menu) {
  const filterInput = createElementFromHtml(`
    <input class="jenkins-input jenkins-search__input jenkins-dropdown__filter-input" placeholder="Filter" spellcheck="false" type="search"/>
  `);

  filterInput.addEventListener("input", (event) =>
    applyFilterKeyword(menu, event.currentTarget),
  );
  filterInput.addEventListener("click", (event) => event.stopPropagation());
  filterInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
    }
  });

  const filterContainer = createElementFromHtml(`
    <div class="jenkins-dropdown__filter">
      <div class="jenkins-dropdown__item__icon">
        ${Symbols.FUNNEL}
      </div>
    </div>
  `);
  filterContainer.appendChild(filterInput);
  return filterContainer;
}

function applyFilterKeyword(menu, filterInput) {
  const filterKeyword = (filterInput.value || "").toLowerCase();
  let items = menu.querySelectorAll(
    ".jenkins-dropdown__item, .jenkins-dropdown__disabled",
  );
  for (let item of items) {
    let match = item.innerText.toLowerCase().includes(filterKeyword);
    item.style.display = match ? "inline-flex" : "none";
  }
}

function generateDropDown(button, callback) {
  tippy(
    button,
    Object.assign({}, Templates.dropdown(), {
      appendTo: undefined,
      onCreate(instance) {
        if (instance.loaded) {
          return;
        }
        instance.popper.addEventListener("click", () => {
          instance.hide();
        });
        instance.popper.addEventListener("keydown", (event) => {
          if (event.key === "Escape") {
            instance.hide();
          }
        });
      },
      onShow(instance) {
        callback(instance);
        button.dataset.expanded = "true";
      },
      onHide() {
        button.dataset.expanded = "false";
      },
    }),
  );
}

export default { init };
