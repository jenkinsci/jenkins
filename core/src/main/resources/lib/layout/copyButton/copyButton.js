Behaviour.specify("span.copy-button", 'copyButton', 0, function(e) {
    var btn = e.firstChild;
    var id = "copy-button"+(iota++);
    btn.id = id;

    makeButton(btn, function() {
        //make an invisible textarea element containing the text
        var el = document.createElement('textarea');
        el.value = e.getAttribute("text");
        el.style.width = "1px";
        el.style.height = "1px";
        el.style.border = "none";
        el.style.padding = "0px";
        document.body.appendChild(el);

        //select the text and copy it to the clipboard
        el.select();
        document.execCommand('copy');

        //remove the textarea element
        document.body.removeChild(el);

        //show the notification
        notificationBar.show(e.getAttribute("message"));
    });
});
