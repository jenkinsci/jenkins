// @include org.kohsuke.stapler.zeroclipboard

Behaviour.specify("span.copy-button", 'copyButton', 0, function(e) {
        var btn = e.firstChild;
        var id = "copy-button"+(iota++);
        btn.id = id;

        var clip = new ZeroClipboard.Client();
        clip.setText(e.getAttribute("text"));
        makeButton(btn);
        clip.setHandCursor(true);

        var container = e.getAttribute("container");
        if (container) {
            container = $(e).up(container);
            container.style.position = "relative";
            clip.glue(id,container);
        } else {
            clip.glue(id);
        }

        clip.addEventListener('onComplete',function() {
            notificationBar.show(e.getAttribute("message"));
        });
        clip.addEventListener('onMouseOver',function() {
          $(id).addClassName('yui-button-hover')
        });
        clip.addEventListener('onMouseOut',function() {
            $(id).removeClassName('yui-button-hover')
        });
        clip.addEventListener('onMouseDown',function() {
            $(id).addClassName('yui-button-active')
        });
        clip.addEventListener('onMouseUp',function() {
            $(id).removeClassName('yui-button-active')
        });
});
