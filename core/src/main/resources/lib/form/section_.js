var section = (function (){

    var SectionNode = function(e) {
        this.section = e;
        this.children = [];
    };
    SectionNode.prototype = {
        /**
         * Points to the DIV node of the section header.
         * @type {HTMLElement}
         */
        section : null,

        /**
         * Child sections.
         *
         * @type {Array<SectionNode>}
         */
        children : null,

        getHTML : function() {
            return this.section.innerHTML;
        }
    };

    return {
        "SectionNode" : SectionNode,
        /**
         * Builds the tree of SectionNode that represents the section hierarchy.
         *
         * @param {HTMLElement|string} root
         *      The root DOM node or its ID from which we build the tree model.
         * @return {SectionNode}
         *      Tree structure that represents the nesting of sections.
         *      For root node, the 'section' property refers to null.
         */
        "buildTree" : function(root) {
            root = $(root||document.body);

            /**
             * Recursively visit elements and find all section headers.
             *
             * @param {HTMLElement} dom
             *      Parent element
             * @param {SectionNode} parent
             *      Function that returns the array to which discovered section headers and child elements are added.
             */
            function visitor(dom,parent) {
                for (var e=dom.firstChild; e!=null; e=e.nextSibling) {
                    if (e.nodeType==1) {
                        if (e.className=="section-header") {
                            var child = new SectionNode(e);
                            parent.children.push(child);
                            visitor(e,child);
                        } else {
                            visitor(e,parent);
                        }
                    }
                }
            }

            var top = new SectionNode(null);
            visitor(root,top);
            return top;
        }
    };
})();