// @include lib.form.filter-menu-button.filter-menu-button

// do the ones that extract innerHTML so that they can get their original HTML before
// other behavior rules change them (like YUI buttons.)
Behaviour.specify(
  "DIV.hetero-list-container",
  "hetero-list",
  -100,
  function (e) {
    if (isInsideRemovable(e)) {
      return;
    }

    // components for the add button
    var menu = document.createElement("SELECT");
    // In case nested content also uses hetero-list
    var btn = Array.from(e.querySelectorAll("INPUT.hetero-list-add")).pop();
    if (!btn) {
      return;
    }
    YAHOO.util.Dom.insertAfter(menu, btn);

    var prototypes = e.lastElementChild;
    while (!prototypes.classList.contains("prototypes")) {
      prototypes = prototypes.previousElementSibling;
    }
    var insertionPoint = prototypes.previousElementSibling; // this is where the new item is inserted.

    // extract templates
    var templates = [];
    var children = prototypes.children;
    for (var i = 0; i < children.length; i++) {
      var n = children[i];
      var name = n.getAttribute("name");
      var tooltip = n.getAttribute("tooltip");
      var descriptorId = n.getAttribute("descriptorId");
      // YUI Menu interprets this <option> text node as HTML, so let's escape it again!
      var title = n.getAttribute("title");
      if (title) {
        title = escapeHTML(title);
      }
      menu.options[i] = new Option(title, "" + i);
      templates.push({
        html: n.innerHTML,
        name: name,
        tooltip: tooltip,
        descriptorId: descriptorId,
      });
    }
    prototypes.remove();

    // Initialize drag & drop for this component
    var withDragDrop = registerSortableDragDrop(e);

    var menuAlign = btn.getAttribute("menualign") || "tl-bl";

    var menuButton = createFilterMenuButton(
      btn,
      menu,
      menuAlign.split("-"),
      250
    );
    // copy class names
    for (i = 0; i < btn.classList.length; i++) {
      menuButton._button.classList.add(btn.classList.item(i));
    }
    menuButton._button.setAttribute("suffix", btn.getAttribute("suffix"));
    menuButton.getMenu().clickEvent.subscribe(function (type, args) {
      var item = args[1];
      if (item.cfg.getProperty("disabled")) {
        return;
      }
      var t = templates[parseInt(item.value)];

      var nc = document.createElement("div");
      nc.className = "repeated-chunk";
      nc.setAttribute("name", t.name);
      nc.setAttribute("descriptorId", t.descriptorId);
      nc.innerHTML = t.html;
      nc.style.opacity = "0";

      renderOnDemand(
        nc.querySelector("div.config-page"),
        function () {
          function findInsertionPoint() {
            // given the element to be inserted 'prospect',
            // and the array of existing items 'current',
            // and preferred ordering function, return the position in the array
            // the prospect should be inserted.
            // (for example 0 if it should be the first item)
            function findBestPosition(prospect, current, order) {
              function desirability(pos) {
                var count = 0;
                for (var i = 0; i < current.length; i++) {
                  if (i < pos == order(current[i]) <= order(prospect)) {
                    count++;
                  }
                }
                return count;
              }

              var bestScore = -1;
              var bestPos = 0;
              for (var i = 0; i <= current.length; i++) {
                var d = desirability(i);
                if (bestScore <= d) {
                  // prefer to insert them toward the end
                  bestScore = d;
                  bestPos = i;
                }
              }
              return bestPos;
            }

            var current = Array.from(e.children).filter(function (e) {
              return e.matches("DIV.repeated-chunk");
            });

            function o(did) {
              if (did instanceof Element) {
                did = did.getAttribute("descriptorId");
              }
              for (var i = 0; i < templates.length; i++) {
                if (templates[i].descriptorId == did) {
                  return i;
                }
              }
              return 0; // can't happen
            }

            var bestPos = findBestPosition(t.descriptorId, current, o);
            if (bestPos < current.length) {
              return current[bestPos];
            } else {
              return insertionPoint;
            }
          }
          var referenceNode = e.classList.contains("honor-order")
            ? findInsertionPoint()
            : insertionPoint;
          referenceNode.parentNode.insertBefore(nc, referenceNode);

          // Initialize drag & drop for this component
          if (withDragDrop) {
            registerSortableDragDrop(nc);
          }

          new YAHOO.util.Anim(
            nc,
            {
              opacity: { to: 1 },
            },
            0.2,
            YAHOO.util.Easing.easeIn
          ).animate();

          Behaviour.applySubtree(nc, true);
          ensureVisible(nc);
          layoutUpdateCallback.call();
        },
        true
      );
    });

    menuButton.getMenu().renderEvent.subscribe(function () {
      // hook up tooltip for menu items
      var items = menuButton.getMenu().getItems();
      for (i = 0; i < items.length; i++) {
        var t = templates[i].tooltip;
        if (t != null) {
          applyTooltip(items[i].element, t);
        }
      }
    });

    // does this container already has a configured instance of the specified descriptor ID?
    function has(id) {
      return (
        e.querySelector('DIV.repeated-chunk[descriptorId="' + id + '"]') != null
      );
    }

    if (e.classList.contains("one-each")) {
      menuButton.getMenu().showEvent.subscribe(function () {
        var items = menuButton.getMenu().getItems();
        for (i = 0; i < items.length; i++) {
          items[i].cfg.setProperty("disabled", has(templates[i].descriptorId));
        }
      });
    }
  }
);

Behaviour.specify("DIV.dd-handle", "hetero-list", -100, function (e) {
  e.addEventListener("mouseover", function () {
    this.closest(".repeated-chunk").classList.add("hover");
  });
  e.addEventListener("mouseout", function () {
    this.closest(".repeated-chunk").classList.remove("hover");
  });
});
