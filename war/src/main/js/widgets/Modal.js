var $ = require('bootstrap-detached').getBootstrap();
var template = require('../templates/Modal.hbs');
var id = 0;

function Modal(title) {
    this.id = 'modal_' + nextId();
    this.title = title;
    this.bodyConetnt = '<div>No body content set.</div>';
    this.yesLabel = 'Yes';
    this.noLabel = 'No';
    this.wrapper = $('<div class="bootstrap-3"></div>');
}

Modal.prototype = {
    yes: function (handler, label) {
        this.yesHandler = handler;
        if (label) {
            this.yesLabel = label;
        }
    },
    no: function (handler, label) {
        this.noHandler = handler;
        if (label) {
            this.noLabel = label;
        }
    },
    body: function (body) {
        this.bodyConetnt = body;
    },
    render: function () {
        var self = this;
        var modalContent = template({
            id: this.id,
            title: this.title,
            yesLabel: this.yesLabel,
            noLabel: this.noLabel
        });

        this.wrapper.empty();
        this.wrapper.append(modalContent);

        $('body').append(this.wrapper);
        var theModal = $('#' + this.id);

        $('.modal-body', theModal).append(this.bodyConetnt);

        theModal.modal();
        theModal.on('hidden.bs.modal', function () {
            self.wrapper.remove();
        });

        if (this.yesHandler) {
            $('.yesBtn', theModal).click(this.yesHandler);
        }
        if (this.noHandler) {
            $('.noBtn', theModal).click(this.noHandler);
        }
    }
};

function nextId() {
    return ++id;
}

module.exports = Modal;
