/*
    Drag&Drop implementation for heterogeneous/repeatable lists.
 */
function initContainerDD(e) {
    if (!Element.hasClassName(e,"with-drag-drop")) return false;

    $(e).childElements().each(function (e) {
        if (e.hasClassName("repeated-chunk"))
            prepareDD(e);
    });
    return true;
}
function prepareDD(e) {
    var h = $(e);
    // locate a handle
    while (h!=null && !h.hasClassName("dd-handle"))
        h = h.down() ? h.down() : h.next();
    if (h!=null) {
        var dd = new DragDrop(e);
        dd.setHandleElId(h);
    }
}

var DragDrop = function(id, sGroup, config) {
    DragDrop.superclass.constructor.apply(this, arguments);
};

(function() {
    var Dom = YAHOO.util.Dom;
    var Event = YAHOO.util.Event;
    var DDM = YAHOO.util.DragDropMgr;

    YAHOO.extend(DragDrop, YAHOO.util.DDProxy, {
        startDrag: function(x, y) {
            var el = this.getEl();

            this.resetConstraints();
            this.setXConstraint(0,0);    // D&D is for Y-axis only

            // set Y constraint to be within the container
            var totalHeight = el.parentNode.offsetHeight;
            var blockHeight = el.offsetHeight;
            this.setYConstraint(el.offsetTop, totalHeight-blockHeight-el.offsetTop);

            el.style.visibility = "hidden";

            this.goingUp = false;
            this.lastY = 0;
        },

        endDrag: function(e) {
            var srcEl = this.getEl();
            var proxy = this.getDragEl();

            // Show the proxy element and animate it to the src element's location
            Dom.setStyle(proxy, "visibility", "");
            var a = new YAHOO.util.Motion(
                proxy, {
                    points: {
                        to: Dom.getXY(srcEl)
                    }
                },
                0.2,
                YAHOO.util.Easing.easeOut
            )
            var proxyid = proxy.id;
            var thisid = this.id;

            // Hide the proxy and show the source element when finished with the animation
            a.onComplete.subscribe(function() {
                    Dom.setStyle(proxyid, "visibility", "hidden");
                    Dom.setStyle(thisid, "visibility", "");
                });
            a.animate();
        },

        onDrag: function(e) {

            // Keep track of the direction of the drag for use during onDragOver
            var y = Event.getPageY(e);

            if (y < this.lastY) {
                this.goingUp = true;
            } else if (y > this.lastY) {
                this.goingUp = false;
            }

            this.lastY = y;
        },

        onDragOver: function(e, id) {
            var srcEl = this.getEl();
            var destEl = Dom.get(id);

            // We are only concerned with list items, we ignore the dragover
            // notifications for the list.
            if (destEl.nodeName == "DIV" && Dom.hasClass(destEl,"repeated-chunk")
                    // Nested lists.. ensure we don't drag out of this list or into a nested one:
                    && destEl.parentNode==srcEl.parentNode) {
                var p = destEl.parentNode;

                // if going up, insert above the target element
                p.insertBefore(srcEl, this.goingUp?destEl:destEl.nextSibling);

                DDM.refreshCache();
            }
        }
    });
})();

