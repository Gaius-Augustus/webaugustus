function toggle_visibility(id) {
    var e = document.getElementById(id);
    if (e.style.display === 'block')
        e.style.display = 'none';
    else
        e.style.display = 'block';
}

// Expandable content script from flooble.com.
// For more information please visit:
//   http://www.flooble.com/scripts/expand.php
// Copyright 2002 Animus Pactum Consulting Inc.
// Script was customized for this application by author of this HTML document!
//----------------------------------------------
var ie4 = false;
if (document.all) {
    ie4 = true;
}
function getObject(id) {
    if (ie4) {
        return document.all[id];
    } else {
        return document.getElementById(id);
    }
}
function toggle(link, divId) {
    var lText = link.innerHTML;
    var d = getObject(divId);
    if (lText === 'click to expand') {
        link.innerHTML = 'click to minimize';
        d.style.display = 'block';
    } else {
        link.innerHTML = 'click to expand';
        d.style.display = 'none';
    }
}
// flooble Expandable Content header end


