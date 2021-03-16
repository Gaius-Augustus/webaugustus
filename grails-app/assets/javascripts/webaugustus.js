function toggle_visibility(id) {
    var e = document.getElementById(id);
    if (e.style.display === 'block')
        e.style.display = 'none';
    else
        e.style.display = 'block';
}

function setElementVisibility(id, visible) {
    var e = document.getElementById(id);
    if (e) {
        if (visible) {
            e.style.display = 'block';
        }
        else {
            e.style.display = 'none';
        }
    }    
}

// Check that the uploaded files don't exceed te size limit of 200 MB
window.onload = function checkUploadFileSize() {
    if (document.forms && document.forms.length > 0) {
        if (!window.FileReader) {
            console.log("The file API isn't supported on this browser yet.");
            return;
        }
        document.forms[0].addEventListener('submit', function( evt ) {
            const fileInput = document.querySelectorAll('input[type="file"]');
            for (var i = 0; i < fileInput.length; i++) {
                if (fileInput[i].files.length > 0 && fileInput[i].files[0]) {
                    if (fileInput[i].files[0].size > 200 * 1024 * 1024) { // 200MB
                        evt.preventDefault();
                        setElementVisibility('commit-button', true);
                        setElementVisibility('aug-spinner', false);
                        alert("The selected file \r\n\"" + fileInput[i].files[0].name 
                                + "\" \r\nexceed our maximal size for file upload from local harddrives via web browser. \r\n"
                                + "Please select a smaller file or use the ftp/http web link file upload option.");
                        return;
                    }
                }
            }
        }, false);
    }
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


