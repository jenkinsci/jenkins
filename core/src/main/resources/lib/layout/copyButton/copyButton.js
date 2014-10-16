// @include org.kohsuke.stapler.zeroclipboard

Behaviour.specify("span.copy-button", 'copyButton', 0, function(e) {
        var btn = e.firstChild;
        var id = "copy-button"+(iota++);
        btn.id = id;

        var clip = new ZeroClipboard(e);
        makeButton(btn);
        clip.setHandCursor(true);

        var container = e.getAttribute("container");
        if (container) {
            container = $(e).up(container);
            container.style.position = "relative";
        }

        clip.on('datarequested',function() {
            clip.setText(e.getAttribute("text"));
        });
        clip.on('complete',function() {
            notificationBar.show(e.getAttribute("message"));
        });
        clip.on('mouseOver',function() {
          $(id).addClassName('yui-button-hover')
        });
        clip.on('mouseOut',function() {
            $(id).removeClassName('yui-button-hover')
        });
        clip.on('mouseDown',function() {
            $(id).addClassName('yui-button-active')
        });
        clip.on('mouseUp',function() {
            $(id).removeClassName('yui-button-active')
        });
});
