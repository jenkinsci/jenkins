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
        this.update();
        this.withDragDrop = initContainerDD(container);
    },

    // insert one more block at the insertion position
    expand : function() {
        // importNode isn't supported in IE.
        // nc = document.importNode(node,true);
        var nc = $(document.createElement("div"));
        nc.className = "repeated-chunk";
        nc.setOpacity(0);
        nc.setAttribute("name",this.name);
        nc.innerHTML = this.blockHTML;
        this.insertionPoint.parentNode.insertBefore(nc, this.insertionPoint);
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
            // noop
        } else
        if(children.length==1) {
            children[0].className = "repeated-chunk first last only";
        } else {
            children[0].className = "repeated-chunk first";
            for(var i=1; i<children.length-1; i++)
                children[i].className = "repeated-chunk middle";
            children[children.length-1].className = "repeated-chunk last";
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
        while(n.tag==null)
            n = n.parentNode;
        n.tag.expand();
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
Behaviour.specify("DIV.repeated-chunk", 'repeatable', 0, function(d) {
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