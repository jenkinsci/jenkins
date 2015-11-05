Event.observe(window, "load", function () {
    /** @type section.SectionNode */
    var outline = section.buildTree();
    var menu = new breadcrumbs.ContextMenu();
    $A(outline.children).each(function (e) {
        var id = "section" + (iota++); // TODO: use human-readable ID
        var caption = e.getHTML();
        var cur = $(e.section).down("A.section-anchor");
        if (cur != null) {
            id = cur.id;
            caption = caption.substring(caption.indexOf("&lt;/a>") + 4);
        } else
            $(e.section).insert({top:"<a id=" + id + " class='section-anchor'>#</a>"});
        menu.add('#' + id, null, caption);
    });
    breadcrumbs.attachMenu('inpage-nav', menu);
});
