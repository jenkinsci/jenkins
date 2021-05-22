Behaviour.specify('.Behaviour-healthReport', 'Behaviour-healthReport', 0, function(element) {
    element.onmouseover = function() {
        this.className='healthReport Behaviour-healthReport hover';
        return true;
    };
    element.onmouseout = function() {
        this.className='healthReport Behaviour-healthReport';
        return true;
    };
});
