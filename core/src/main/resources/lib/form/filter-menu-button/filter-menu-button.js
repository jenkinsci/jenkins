function createFilterMenuButton(button, menu, menuAlignment, menuMinScrollHeight) {
  var MIN_NUM_OPTIONS = 5;
  var menuButton = new YAHOO.widget.Button(button, {
    type: "menu",
    menu: menu,
    menualignment: menuAlignment,
    menuminscrollheight: menuMinScrollHeight
  });

  var filter = _createFilterMenuButton(menuButton._menu);

  menuButton._menu.element.appendChild(filter);
  menuButton._menu.showEvent.subscribe(function () {
    _applyFilterKeyword(menuButton._menu, filter.firstElementChild);
    filter.style.display = (_getItemList(menuButton._menu).children.length >= MIN_NUM_OPTIONS) ? '' : 'NONE';
  });
  menuButton._menu.setInitialFocus = function () {
    setTimeout(function () {
      filter.firstElementChild.focus();
    }, 0);
  };

  return menuButton;
}

function _createFilterMenuButton(menu) {
  const filterInput = document.createElement("input");
  filterInput.classList.add('jenkins-input')
  filterInput.setAttribute("placeholder", "Filter");
  filterInput.setAttribute("spellcheck", "false");
  filterInput.setAttribute("type", "search");

  filterInput.addEventListener('input', (event) => _applyFilterKeyword(menu, event.currentTarget));
  filterInput.addEventListener("keypress", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
    }
  });

  const filterContainer = document.createElement("div");
  filterContainer.appendChild(filterInput);

  return filterContainer;
}

function _applyFilterKeyword(menu, filterInput) {
  const filterKeyword = (filterInput.value || '').toLowerCase();
  const itemList = _getItemList(menu);
  let item, match;
  for (item of itemList.children) {
    match = item.innerText.toLowerCase().includes(filterKeyword);
    item.style.display = match ? '' : 'NONE';
  }
  menu.align();
}

function _getItemList(menu) {
  return menu.body.children[0];
}
