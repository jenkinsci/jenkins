// @include lib.form.dragdrop.dragdrop

var repeatableSupport = {
    // set by the inherited instance to the insertion point DIV
    insertionPoint: null,

    // HTML text of the repeated chunk
    blockHTML: null,

    // containing <div>.
    container: null,

    // block name for structured HTML
    name : null,

    // enable to display add button on top
    enableTopButton : false,

    withDragDrop: false,

    // do the initialization
    init : function(container,master,insertionPoint) {
        this.container = $(container);
        this.container.tag = this;
        master = $(master);
        this.blockHTML = master.innerHTML;
        master.parentNode.removeChild(master);
        this.insertionPoint = $(insertionPoint);
        this.name = master.getAttribute("name");
        if (this.container.getAttribute("enableTopButton") == "true") {
            this.enableTopButton = true;
        } else {
            this.enableTopButton = false;
        }
        this.update();
        this.withDragDrop = initContainerDD(container);
    },

    // insert one more block at the insertion position
    expand : function(addOnTop) {
        if (addOnTop == null) {
            addOnTop = false;
        }

        // importNode isn't supported in IE.
        // nc = document.importNode(node,true);
        var nc = $(document.createElement("div"));
        nc.className = "repeated-chunk";
        nc.setOpacity(0);
        nc.setAttribute("name",this.name);
        nc.innerHTML = this.blockHTML;
        if (!addOnTop) {
            this.insertionPoint.parentNode.insertBefore(nc, this.insertionPoint);
        } else if (this.enableTopButton) {
            var children = $(this.container).childElements().findAll(function (n) {
                return n.hasClassName("repeated-chunk");
            });
            this.container.insertBefore(nc, children[0]);
        }
        if (this.withDragDrop) prepareDD(nc);

        new YAHOO.util.Anim(nc, {
            opacity: { to:1 }
        }, 0.2, YAHOO.util.Easing.easeIn).animate();

        Behaviour.applySubtree(nc,true);
        this.update();
    },

    // update CSS classes associated with repeated items.
    update : function() {
        var children = $(this.container).childElements().findAll(function (n) {
            return n.hasClassName("repeated-chunk");
        });

        if(children.length==0) {
            var addButtonElements = $(this.container).childElements().findAll(function (b) {
                return b.hasClassName("repeatable-add");
            });

            if (addButtonElements.length == 2) {
                var buttonElement = addButtonElements[0];
                var parentOfButton = buttonElement.parentNode;
                parentOfButton.removeChild(buttonElement);
            }
        } else {
            if (children.length == 1) {
                var addButtonElements = $(this.container).childElements().findAll(function (b) {
                    return b.hasClassName("repeatable-add");
                });

                if (addButtonElements.length == 1 && this.enableTopButton) {
                    var buttonElement = addButtonElements[0];
                    var parentOfButton = buttonElement.parentNode;
                    var addTopButton = document.createElement('input');
                    addTopButton.type = 'button';
                    addTopButton.value = buttonElement.textContent || buttonElement.innerText;
                    addTopButton.className = 'repeatable-add repeatable-add-top';
                    parentOfButton.insertBefore(addTopButton, parentOfButton.firstChild);
                    Behaviour.applySubtree(addTopButton, true);
                }
                children[0].className = "repeated-chunk first last only";
            } else {
                children[0].className = "repeated-chunk first";
                for (var i = 1; i < children.length - 1; i++)
                    children[i].className = "repeated-chunk middle";
                children[children.length - 1].className = "repeated-chunk last";
            }
        }
    },

    // these are static methods that don't rely on 'this'

    // called when 'delete' button is clicked
    onDelete : function(n) {
        n = findAncestorClass(n,"repeated-chunk");
        var a = new YAHOO.util.Anim(n, {
            opacity: { to:0 },
            height: {to:0 }
        }, 0.2, YAHOO.util.Easing.easeIn);
        a.onComplete.subscribe(function() {
            var p = n.parentNode;
            p.removeChild(n);
            if (p.tag)
                p.tag.update();
        });
        a.animate();
    },

    // called when 'add' button is clicked
    onAdd : function(n) {
        var addOnTop = false;
        while (n.tag == null) {
            n = n.parentNode;
            if (n.hasClassName("repeatable-add-top")) {
                addOnTop = true;
            }
        }
        n.tag.expand(addOnTop);
        // Hack to hide tool home when a new tool has some installers.
        var inputs = n.getElementsByTagName('INPUT');
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            if (input.name == 'hudson-tools-InstallSourceProperty') {
                updateOptionalBlock(input, false);
            }
        }
    }
};


// do the ones that extract innerHTML so that they can get their original HTML before
// other behavior rules change them (like YUI buttons.)
Behaviour.specify("DIV.repeated-container", 'repeatable', -100, function(e) {
        if(isInsideRemovable(e))    return;

        // compute the insertion point
        var ip = $(e.lastChild);
        while (!ip.hasClassName("repeatable-insertion-point"))
            ip = ip.previous();
        // set up the logic
        object(repeatableSupport).init(e, e.firstChild, ip);
});

    // button to add a new repeatable block
Behaviour.specify("INPUT.repeatable-add", 'repeatable', 0, function(e) {
        makeButton(e,function(e) {
            repeatableSupport.onAdd(e.target);
        });
        e = null; // avoid memory leak
    });

Behaviour.specify("INPUT.repeatable-delete", 'repeatable', 0, function(e) {
        var b = makeButton(e,function(e) {
            repeatableSupport.onDelete(e.target);
        });
        var be = $(b.get("element"));
        be.on("mouseover",function() {
            $(this).up(".repeated-chunk").addClassName("hover");
        });
        be.on("mouseout",function() {
            $(this).up(".repeated-chunk").removeClassName("hover");
        });

        e = be = null; // avoid memory leak
    });

    // radio buttons in repeatable content
// Needs to run before the radioBlock behavior so that names are already unique.
Behaviour.specify("DIV.repeated-chunk", 'repeatable', -200, function(d) {
        var inputs = d.getElementsByTagName('INPUT');
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].type == 'radio') {
                // Need to uniquify each set of radio buttons in repeatable content.
                // buildFormTree will remove the prefix before form submission.
                var prefix = d.getAttribute('radioPrefix');
                if (!prefix) {
                    prefix = 'removeme' + (iota++) + '_';
                    d.setAttribute('radioPrefix', prefix);
                }
                inputs[i].name = prefix + inputs[i].name;
                // Reselect anything unselected by browser before names uniquified:
                if (inputs[i].defaultChecked) inputs[i].checked = true;
            }
        }
});