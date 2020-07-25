function createFilterMenuButton (button, menu, menuAlignment, menuMinScrollHeight) {
    var MIN_NUM_OPTIONS = 5;
    var menuButton = new YAHOO.widget.Button(button, {
        type: "menu",
        menu: menu,
        menualignment: menuAlignment,
        menuminscrollheight: menuMinScrollHeight
    });

    var filter = _createFilter(menuButton._menu);

    menuButton._menu.element.appendChild(filter);
    menuButton._menu.showEvent.subscribe(function() {
        filter.firstElementChild.value = '';
        _applyFilterKeyword(menuButton._menu, filter.firstElementChild);
        filter.style.display = (_getItemList(menuButton._menu).children.length >= MIN_NUM_OPTIONS) ? '' : 'NONE';
    });
    menuButton._menu.setInitialFocus = function () {
        setTimeout(function() {
            filter.firstElementChild.focus();
        }, 0);
    };

    return menuButton;
}

function _createFilter (menu) {
    var filterInput = document.createElement("input");
    filterInput.style.width = '100%';
    filterInput.setAttribute("placeholder", "Filter");
    filterInput.onkeyup = _onFilterKeyUp.bind(menu);

    var filterContainer = document.createElement("div");
    filterContainer.appendChild(filterInput);

    return filterContainer;
}

function _onFilterKeyUp (evt) {
    _applyFilterKeyword(this, evt.target);
}

function _applyFilterKeyword (menu, filterInput) {
    var filterKeyword = filterInput.value;
    var itemList = _getItemList(menu);
    var item, match;
    for (item of itemList.children) {
        match = (item.innerText.toLowerCase().indexOf(filterKeyword) !== -1);
        item.style.display = match ? '' : 'NONE';
    }
    menu.align();
}

function _getItemList (menu) {
    return menu.body.children[0];
}
