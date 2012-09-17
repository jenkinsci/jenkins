(function() {
    Behaviour.specify("INPUT.reveal-expandable-detail", 'ExpandableDetailsNote', 0, function(e) {
            var detail = e.nextSibling;
            makeButton(e,function() {
                detail.style.display = (detail.style.display=="block")?"none":"block";  
            });
    });
}());
