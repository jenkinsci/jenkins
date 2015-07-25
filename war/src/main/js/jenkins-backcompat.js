/*
 * A backward compatibility hack to expose modularized functions that
 * existing JS code may look for in the global scope.
 * <p>
 * This module should have no external dependencies (i.e. that need to be loaded before it).
 * The functions need to be loaded immediately because the non-modularized .js code that
 * they are servicing (e.g. adjuncts) will be loaded immediately.
 */

function globalize(module, functions) {
    if (typeof window !== 'undefined') {
        function apply(funcNum) {
            var functionInfo = functions[funcNum];
            var globalFuncName;
            var moduleFuncName;

            if (typeof functionInfo === 'object') {
                moduleFuncName = functionInfo.from;
                globalFuncName = functionInfo.to;
            } else {
                moduleFuncName = functionInfo;
                globalFuncName = functionInfo;
            }

            window[globalFuncName] = function () {
                // TODO: Come up with a way of gather this info so we can track code using non modularized code.
                return module[moduleFuncName].apply(window, arguments);
            };
        }
        for (var i = 0; i < functions.length; i++) {
            apply(i);
        }
    }
};

// Need to globalize some functions from the 'find' module.
globalize(require('./find'), ['findAncestor', 'findAncestorClass', 'findFollowingTR',
        'findPreviousFormItem', 'findNextFormItem',
        'findFormParent', 'findNearBy']);

// Need to globalize some functions from the 'formsub' module.
globalize(require('./formsub'), ['buildFormTree']);
