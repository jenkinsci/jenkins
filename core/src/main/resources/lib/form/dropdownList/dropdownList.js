Behaviour.specify("SELECT.dropdownList", 'dropdownList', 0, function(e) {
    if(isInsideRemovable(e))    return;

    var subForms = [];
    var start = $(findFollowingTR(e, 'dropdownList-container')).down().next(), end;
    do { start = start.firstChild; } while (start && start.tagName != 'TR');

    if (start && !Element.hasClassName(start,'dropdownList-start'))
        start = findFollowingTR(start, 'dropdownList-start');
    while (start != null) {
        subForms.push(start);
        start = findFollowingTR(start, 'dropdownList-start');
    }

    // control visibility
    function updateDropDownList() {
        for (var i = 0; i < subForms.length; i++) {
            var show = e.selectedIndex == i;
            var f = $(subForms[i]);

            if (show)   renderOnDemand(f.next());
            f.rowVisibilityGroup.makeInnerVisisble(show);

            // TODO: this is actually incorrect in the general case if nested vg uses field-disabled
            // so far dropdownList doesn't create such a situation.
            f.rowVisibilityGroup.eachRow(true, show?function(e) {
                e.removeAttribute("field-disabled");
            } : function(e) {
                e.setAttribute("field-disabled","true");
            });
        }
    }

    e.onchange = updateDropDownList;

    updateDropDownList();
});