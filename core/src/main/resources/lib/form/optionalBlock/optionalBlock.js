Behaviour.specify(".option-group-box", 'optionalBlock', 0, function(e) {
    var checkbox = e.down().down().down();
    var negative = checkbox.hasClassName("negative");
    var inline = checkbox.getAttribute("inline");
    var groupBox = e;
    var group = e.down(".option-group",0);

    // update the visibility of the body based on the current state of the checkbox.
    function updateOptionalBlock(scroll) {
        var checked = xor(checkbox.checked, negative);

        if (checked) {
            group.show();
            groupBox.addClassName('shown');

            if(scroll)
                scrollIntoView(YAHOO.util.Dom.getRegion(group));
        } else {
            group.hide();
            groupBox.removeClassName('shown');
        }

        if (checkbox.name == 'hudson-tools-InstallSourceProperty') {
            // Hack to hide tool home when "Install automatically" is checked.
            var homeField = findPreviousFormItem(checkbox, 'home');
            if (homeField != null && homeField.value == '') {
                var tr = findAncestor(homeField, 'TR');
                if (tr != null) {
                    tr.style.display = checkbox.checked ? 'none' : '';
                }
            }
        }

        layoutUpdateCallback.call();
    }

    function init() {
        checkbox.groupingNode = true;

        if (!inline) {
            // the dominating node of all the controls in option-group is the checkbox
            group.setAttribute("nameRef", checkbox.id = "cb"+(iota++));
        }

        updateOptionalBlock(false);

        checkbox.on("click",function() {
            updateOptionalBlock(true);
        });
    }

    init();
});
