// @include lib.form.dragdrop.dragdrop

// do the ones that extract innerHTML so that they can get their original HTML before
// other behavior rules change them (like YUI buttons.)
Behaviour.specify("DIV.hetero-list-container", 'hetero-list', -100, function(e) {
        e=$(e);
        if(isInsideRemovable(e))    return;

        // components for the add button
        var menu = document.createElement("SELECT");
        var btns = findElementsBySelector(e,"INPUT.hetero-list-add"),
            btn = btns[btns.length-1]; // In case nested content also uses hetero-list
        YAHOO.util.Dom.insertAfter(menu,btn);

        var prototypes = $(e.lastChild);
        while(!prototypes.hasClassName("prototypes"))
            prototypes = prototypes.previous();
        var insertionPoint = prototypes.previous();    // this is where the new item is inserted.

        // extract templates
        var templates = []; var i=0;
        $(prototypes).childElements().each(function (n) {
            var name = n.getAttribute("name");
            var tooltip = n.getAttribute("tooltip");
            var descriptorId = n.getAttribute("descriptorId");
            menu.options[i] = new Option(n.getAttribute("title"),""+i);
            templates.push({html:n.innerHTML, name:name, tooltip:tooltip,descriptorId:descriptorId});
            i++;
        });
        Element.remove(prototypes);

        var withDragDrop = initContainerDD(e);
        if(!btn) return;
        var menuAlign = "tl-bl";
        if(btn) menuAlign = (btn.getAttribute("menualign")||"tl-bl");

        var menuButton = new YAHOO.widget.Button(btn, { type: "menu", menu: menu, menualignment: menuAlign.split("-"), menuminscrollheight: 250 });
        $(menuButton._button).addClassName(btn.className);    // copy class names
        $(menuButton._button).setAttribute("suffix",btn.getAttribute("suffix"));
        menuButton.getMenu().clickEvent.subscribe(function(type,args,value) {
            var item = args[1];
            if (item.cfg.getProperty("disabled"))   return;
            var t = templates[parseInt(item.value)];

            var nc = document.createElement("div");
            nc.className = "repeated-chunk";
            nc.setAttribute("name",t.name);
            nc.setAttribute("descriptorId",t.descriptorId);
            nc.innerHTML = t.html;
            $(nc).setOpacity(0);

            var scroll = document.body.scrollTop;
            var targetElem = findElementsBySelector(nc,"TR.config-page")[0];
            if(!targetElem) targetElem = findElementsBySelector(nc,"DIV.config-page")[0];
            renderOnDemand(targetElem,function() {
                function findInsertionPoint() {
                    // given the element to be inserted 'prospect',
                    // and the array of existing items 'current',
                    // and preferred ordering function, return the position in the array
                    // the prospect should be inserted.
                    // (for example 0 if it should be the first item)
                    function findBestPosition(prospect,current,order) {
                        function desirability(pos) {
                            var count=0;
                            for (var i=0; i<current.length; i++) {
                                if ((i<pos) == (order(current[i])<=order(prospect)))
                                    count++;
                            }
                            return count;
                        }

                        var bestScore = -1;
                        var bestPos = 0;
                        for (var i=0; i<=current.length; i++) {
                            var d = desirability(i);
                            if (bestScore<=d) {// prefer to insert them toward the end
                                bestScore = d;
                                bestPos = i;
                            }
                        }
                        return bestPos;
                    }

                    var current = e.childElements().findAll(function(e) {return e.match("DIV.repeated-chunk")});

                    function o(did) {
                        if (Object.isElement(did))
                            did = did.getAttribute("descriptorId");
                        for (var i=0; i<templates.length; i++)
                            if (templates[i].descriptorId==did)
                                return i;
                        return 0; // can't happen
                    }

                    var bestPos = findBestPosition(t.descriptorId, current, o);
                    if (bestPos<current.length)
                        return current[bestPos];
                    else
                        return insertionPoint;
                }
                (e.hasClassName("honor-order") ? findInsertionPoint() : insertionPoint).insert({before:nc});

                if(withDragDrop)    prepareDD(nc);

                new YAHOO.util.Anim(nc, {
                    opacity: { to:1 }
                }, 0.2, YAHOO.util.Easing.easeIn).animate();

                Behaviour.applySubtree(nc,true);
                ensureVisible(nc);
                layoutUpdateCallback.call();
            },true);
        });

        menuButton.getMenu().renderEvent.subscribe(function() {
            // hook up tooltip for menu items
            var items = menuButton.getMenu().getItems();
            for(i=0; i<items.length; i++) {
                var t = templates[i].tooltip;
                if(t!=null)
                    applyTooltip(items[i].element,t);
            }
        });

        if (e.hasClassName("one-each")) {
            // does this container already has a ocnfigured instance of the specified descriptor ID?
            function has(id) {
                return Prototype.Selector.find(e.childElements(),"DIV.repeated-chunk[descriptorId=\""+id+"\"]")!=null;
            }

            menuButton.getMenu().showEvent.subscribe(function() {
                var items = menuButton.getMenu().getItems();
                for(i=0; i<items.length; i++) {
                    items[i].cfg.setProperty("disabled",has(templates[i].descriptorId));
                }
            });
        }
    });

Behaviour.specify("DIV.dd-handle", 'hetero-list', -100, function(e) {
        e=$(e);
        e.on("mouseover",function() {
            $(this).up(".repeated-chunk").addClassName("hover");
        });
        e.on("mouseout",function() {
            $(this).up(".repeated-chunk").removeClassName("hover");
        });
});
