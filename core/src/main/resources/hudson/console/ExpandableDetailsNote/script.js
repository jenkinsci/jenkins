(function() {
    Behaviour.register({

        "INPUT.reveal-expandable-detail" : function(e) {
            var detail = e.nextSibling;
            makeButton(e,function() {
                detail.style.display = (detail.style.display=="block")?"none":"block";  
            });
        }
    });
}());
