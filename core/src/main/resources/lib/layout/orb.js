(function() {

    var drawOrbs = function() {
        // Some orb <img> elements are hardcoded in plugins.  Manually transforming for now.  Maybe
        // there's a better way.
        transformImgElements();

        var orbs = document.getElementsByClassName('jenkins-orb');
        for (var i = 0; i < orbs.length; i++) {
            var orb = orbs[i];
            drawOrb(orb);
        }
    };

    function drawOrb(orb) {
        var dimension = Math.min(orb.offsetWidth, 36);
        var orbStatus = orb.getAttribute('orb-status');
        var imgMapEntry = imgNameMap[orbStatus.toLowerCase()];

        if (!imgMapEntry) {
            return;
        }

        storeOriginalClassSpecs(orb);
        restoreOriginalClassSpec(orb);
        removeChildElements(orb, 'canvas');

        if (imgMapEntry.animated) {
            drawAnimatedOrb(orb, dimension, imgMapEntry);
        } else {
            // Add some class info to trigger non-animated css styles...
            orb.className += ' NO_ANIME ' + orbStatus;
            applyStaticOrbStyle(orb, imgMapEntry);
        }
    }

    function drawAnimatedOrb(orb, dimension, imgMapEntry) {
        var canvas = document.createElement('canvas');
        var orbColor = imgMapEntry.color;

        canvas.className = 'orb-canvas';
        canvas.setAttribute('width', dimension);
        canvas.setAttribute('height', dimension);
        var circle = new ProgressCircle({
            canvas: canvas,
            minRadius: dimension * 3 / 8 - 2,
            arcWidth: dimension / 8 + 1
        });

        var x = 0;
        circle.addEntry({
            fillColor: orbColor,
            progressListener: function() {
                if (x >= 1) {
                    x = 0;
                }
                x = x + 0.005;
                return x; // between 0 and 1
            }
        });
        circle.start(24);
        orb.appendChild(canvas);
    }

    /**
     * We are applying the style here in JS (Vs CSS) because we need to maintain a color map anyway
     * (for the animated progressive orb) here in the JS, which means there's no point in also maintaining
     * color styles in the CSS.
     */
    function applyStaticOrbStyle(orb, imgMapEntry) {
        var style = STATIC_ORB_STYLE_TEMPLATE;

        style = style.replace(/@background@/g, imgMapEntry.color.stop2);
        style = style.replace(/@stop1@/g, imgMapEntry.color.stop1);
        style = style.replace(/@stop2@/g, imgMapEntry.color.stop2);

        orb.setAttribute('style', style);
    }

    /**
     * Some orb <img> elements are hardcoded in some/many plugins external to the core Jenkins codebase.
     * Manually transforming for now.  Maybe there's a better way.
     */
    function transformImgElements() {
        var createdOrbs = [];
        var imgsToRemove = [];

        function transformImgElement(img) {
            // Mark <img> element so as to avoid processing it again if there's a JS
            // triggered refresh.
            if (img.hasAttribute('orb-skip')) {
                return;
            }
            img.setAttribute('orb-skip', 'yes');

            var src = img.getAttribute('src');
            var tokens = src.split('/');

            if (tokens && tokens.length >= 2) {
                var imgSize = tokens[tokens.length - 2];

                if (imgSizeSet.indexOf(imgSize) !== -1) {
                    var imgName = tokens[tokens.length - 1];
                    var extIdx = imgName.lastIndexOf('.');

                    if (extIdx !== -1) {
                        var imgNameNormalized = imgName.substr(0, extIdx);
                        var imgNameMapping = imgNameMap[imgNameNormalized];
                        if (imgNameMapping) {
                            // This is a supported orb
                            var orbDiv = document.createElement('div');
                            var status = imgNameMapping.name;

                            if (!status) {
                                status = imgNameNormalized.toUpperCase();
                            }

                            // e.g. <div class="jenkins-orb orb-size-16x16" orb-status="RED"></div>
                            orbDiv.className = 'jenkins-orb orb-size-' + imgSize;
                            orbDiv.setAttribute('orb-status', status);

                            img.parentNode.insertBefore(orbDiv, img);
                            createdOrbs.push(orbDiv);
                            imgsToRemove.push(img);
                        }
                    }
                }
            }
        }

        var imgs = document.getElementsByTagName('img');
        for (var i = 0; i < imgs.length; i++) {
            var img = imgs[i];
            transformImgElement(img);
        }
        for (var i = 0; i < imgsToRemove.length; i++) {
            var img = imgsToRemove[i];
            img.parentNode.removeChild(img);
        }

        return createdOrbs;
    }

    function storeOriginalClassSpecs(element) {
        // Only store if it hasn't already been stored
        var originalClass = element.getAttribute('original-class');
        if (!originalClass || originalClass === '') {
            element.setAttribute('original-class', element.className);
        }
    }
    function restoreOriginalClassSpec(element) {
        // Only restore if it has already been stored
        var originalClass = element.getAttribute('original-class');
        if (originalClass && originalClass !== '') {
            element.className = originalClass;
        }
    }

    function removeChildElements(element, childElementName) {
        var childElements = element.getElementsByTagName(childElementName);
        if (childElements) {
            for (var i = 0; i < childElements.length; i++) {
                element.removeChild(childElements[i]);
            }
        }
    }

    /**
     * It's a fact of life (sad one) that we still need to perform a periodic screen-scrape to
     * make sure we are catching all possible situations where the old style orb <img>s are
     * in use (in plugins etc).  Would have been far easier if all icons were done via css,
     * but that's not the case.
     */
    function periodicImgTransform() {
        setTimeout(function() {
            var createdOrbs = transformImgElements();
            for (var i = 0; i < createdOrbs.length; i++) {
                var orb = createdOrbs[i];
                drawOrb(orb);
            }
            // Set it up again... keep doing it.
            periodicImgTransform();
        }, 100);
    }

    document.addEventListener("DOMContentLoaded", function() {
        drawOrbs();
        layoutUpdateCallback.add(drawOrbs);
        periodicImgTransform();
    });

    var imgSizeSet = ['16x16', '24x24', '32x32', '48x48'];
    var imgNameMap = {
        red: {
            animated: false,
            color: {
                stop1: '#F69E9E',
                stop2: '#9E1010'
            }
        },
        red_anime: {
            animated: true,
            color: '#9E1010'
        },
        yellow: {
            animated: false,
            color: {
                stop1: '#FCCC4F',
                stop2: '#D54214'
            }
        },
        yellow_anime: {
            animated: true,
            color: '#D54214'
        },
        blue: {
            animated: false,
            color: {
                stop1: '#79B1E9',
                stop2: '#3269A0'
            }
        },
        blue_anime: {
            animated: true,
            color: '#3269A0'
        },
        grey: {
            animated: false,
            color: {
                stop1: '#ECECEC',
                stop2: '#8B8B8B'
            }
        },
        grey_anime: {
            animated: true,
            color: '#8B8B8B'
        }
    };
    imgNameMap.disabled = imgNameMap.grey;
    imgNameMap.disabled_anime = imgNameMap.grey_anime;
    imgNameMap.aborted = imgNameMap.grey;
    imgNameMap.aborted_anime = imgNameMap.grey_anime;
    imgNameMap.nobuilt = imgNameMap.grey;
    imgNameMap.nobuilt_anime = imgNameMap.grey_anime;

    // Name mismatch in BallColor.java - enum is "NOTBUILT" while image name prefix is "nobuilt"
    imgNameMap.notbuilt = imgNameMap.nobuilt;
    imgNameMap.notbuilt_anime = imgNameMap.nobuilt_anime;

    var STATIC_ORB_STYLE_TEMPLATE = "background: @background@;" +
        " background-image: -moz-radial-gradient(3px 3px 45deg, circle cover, @stop1@ 0%, @stop2@ 100%);" +
        " background-image: -webkit-radial-gradient(3px 3px, circle cover, @stop1@, @stop2@);" +
        " background-image: radial-gradient(circle at 3px 3px, @stop1@ 0%, @stop2@ 100%);";

})();

