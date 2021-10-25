(function() {
    Behaviour.specify("BUTTON.reveal-expandable-detail", 'ExpandableDetailsNote', 0, function(e) {
            var detail = e.nextSibling;
            e.addEventListener('click', function() {
                detail.style.display = (detail.style.display=="block")?"none":"block";  
            });
    });
}());
