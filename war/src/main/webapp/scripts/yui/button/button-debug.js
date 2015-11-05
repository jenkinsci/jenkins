/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
/**
* @module button
* @description <p>The Button Control enables the creation of rich, graphical 
* buttons that function like traditional HTML form buttons.  <em>Unlike</em> 
* traditional HTML form buttons, buttons created with the Button Control can have 
* a label that is different from its value.  With the inclusion of the optional 
* <a href="module_menu.html">Menu Control</a>, the Button Control can also be
* used to create menu buttons and split buttons, controls that are not 
* available natively in HTML.  The Button Control can also be thought of as a 
* way to create more visually engaging implementations of the browser's 
* default radio-button and check-box controls.</p>
* <p>The Button Control supports the following types:</p>
* <dl>
* <dt>push</dt>
* <dd>Basic push button that can execute a user-specified command when 
* pressed.</dd>
* <dt>link</dt>
* <dd>Navigates to a specified url when pressed.</dd>
* <dt>submit</dt>
* <dd>Submits the parent form when pressed.</dd>
* <dt>reset</dt>
* <dd>Resets the parent form when pressed.</dd>
* <dt>checkbox</dt>
* <dd>Maintains a "checked" state that can be toggled on and off.</dd>
* <dt>radio</dt>
* <dd>Maintains a "checked" state that can be toggled on and off.  Use with 
* the ButtonGroup class to create a set of controls that are mutually 
* exclusive; checking one button in the set will uncheck all others in 
* the group.</dd>
* <dt>menu</dt>
* <dd>When pressed will show/hide a menu.</dd>
* <dt>split</dt>
* <dd>Can execute a user-specified command or display a menu when pressed.</dd>
* </dl>
* @title Button
* @namespace YAHOO.widget
* @requires yahoo, dom, element, event
* @optional container, menu
*/


(function () {


    /**
    * The Button class creates a rich, graphical button.
    * @param {String} p_oElement String specifying the id attribute of the 
    * <code>&#60;input&#62;</code>, <code>&#60;button&#62;</code>,
    * <code>&#60;a&#62;</code>, or <code>&#60;span&#62;</code> element to 
    * be used to create the button.
    * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-6043025">HTMLInputElement</a>|<a href="http://www.w3.org
    * /TR/2000/WD-DOM-Level-1-20000929/level-one-html.html#ID-34812697">
    * HTMLButtonElement</a>|<a href="
    * http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-html.html#
    * ID-33759296">HTMLElement</a>} p_oElement Object reference for the 
    * <code>&#60;input&#62;</code>, <code>&#60;button&#62;</code>, 
    * <code>&#60;a&#62;</code>, or <code>&#60;span&#62;</code> element to be 
    * used to create the button.
    * @param {Object} p_oElement Object literal specifying a set of   
    * configuration attributes used to create the button.
    * @param {Object} p_oAttributes Optional. Object literal specifying a set  
    * of configuration attributes used to create the button.
    * @namespace YAHOO.widget
    * @class Button
    * @constructor
    * @extends YAHOO.util.Element
    */



    // Shorthard for utilities

    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event,
        Lang = YAHOO.lang,
        UA = YAHOO.env.ua,
        Overlay = YAHOO.widget.Overlay,
        Menu = YAHOO.widget.Menu,
    
    
        // Private member variables
    
        m_oButtons = {},    // Collection of all Button instances
        m_oOverlayManager = null,   // YAHOO.widget.OverlayManager instance
        m_oSubmitTrigger = null,    // The button that submitted the form 
        m_oFocusedButton = null;    // The button that has focus



    // Private methods

    
    
    /**
    * @method createInputElement
    * @description Creates an <code>&#60;input&#62;</code> element of the 
    * specified type.
    * @private
    * @param {String} p_sType String specifying the type of 
    * <code>&#60;input&#62;</code> element to create.
    * @param {String} p_sName String specifying the name of 
    * <code>&#60;input&#62;</code> element to create.
    * @param {String} p_sValue String specifying the value of 
    * <code>&#60;input&#62;</code> element to create.
    * @param {String} p_bChecked Boolean specifying if the  
    * <code>&#60;input&#62;</code> element is to be checked.
    * @return {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-6043025">HTMLInputElement</a>}
    */
    function createInputElement(p_sType, p_sName, p_sValue, p_bChecked) {
    
        var oInput,
            sInput;
    
        if (Lang.isString(p_sType) && Lang.isString(p_sName)) {
        
            if (UA.ie && (UA.ie < 9)) {
        
                /*
                    For IE it is necessary to create the element with the 
                    "type," "name," "value," and "checked" properties set all 
                    at once.
                */
            
                sInput = "<input type=\"" + p_sType + "\" name=\"" + 
                    p_sName + "\"";
        
                if (p_bChecked) {
        
                    sInput += " checked";
                
                }
                
                sInput += ">";
        
                oInput = document.createElement(sInput);
        
                oInput.value = p_sValue;

            } else {
            
                oInput = document.createElement("input");
                oInput.name = p_sName;
                oInput.type = p_sType;
                oInput.value = p_sValue;
        
                if (p_bChecked) {
        
                    oInput.checked = true;
                
                }
        
            }
        
        
        }

		return oInput;
    
    }
    
    
    /**
    * @method setAttributesFromSrcElement
    * @description Gets the values for all the attributes of the source element 
    * (either <code>&#60;input&#62;</code> or <code>&#60;a&#62;</code>) that 
    * map to Button configuration attributes and sets them into a collection 
    * that is passed to the Button constructor.
    * @private
    * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-6043025">HTMLInputElement</a>|<a href="http://www.w3.org/
    * TR/2000/WD-DOM-Level-1-20000929/level-one-html.html#ID-
    * 48250443">HTMLAnchorElement</a>} p_oElement Object reference to the HTML 
    * element (either <code>&#60;input&#62;</code> or <code>&#60;span&#62;
    * </code>) used to create the button.
    * @param {Object} p_oAttributes Object reference for the collection of 
    * configuration attributes used to create the button.
    */
    function setAttributesFromSrcElement(p_oElement, p_oAttributes) {
    
        var sSrcElementNodeName = p_oElement.nodeName.toUpperCase(),
			sClass = (this.CLASS_NAME_PREFIX + this.CSS_CLASS_NAME),
            me = this,
            oAttribute,
            oRootNode,
            sText;
            
    
        /**
        * @method setAttributeFromDOMAttribute
        * @description Gets the value of the specified DOM attribute and sets it 
        * into the collection of configuration attributes used to configure 
        * the button.
        * @private
        * @param {String} p_sAttribute String representing the name of the 
        * attribute to retrieve from the DOM element.
        */
        function setAttributeFromDOMAttribute(p_sAttribute) {
    
            if (!(p_sAttribute in p_oAttributes)) {
    
                /*
                    Need to use "getAttributeNode" instead of "getAttribute" 
                    because using "getAttribute," IE will return the innerText 
                    of a <code>&#60;button&#62;</code> for the value attribute  
                    rather than the value of the "value" attribute.
                */
        
                oAttribute = p_oElement.getAttributeNode(p_sAttribute);
        
    
                if (oAttribute && ("value" in oAttribute)) {
    
                    YAHOO.log("Setting attribute \"" + p_sAttribute + 
                        "\" using source element's attribute value of \"" + 
                        oAttribute.value + "\"", "info", me.toString());
    
                    p_oAttributes[p_sAttribute] = oAttribute.value;
    
                }
    
            }
        
        }
    
    
        /**
        * @method setFormElementProperties
        * @description Gets the value of the attributes from the form element  
        * and sets them into the collection of configuration attributes used to 
        * configure the button.
        * @private
        */
        function setFormElementProperties() {
    
            setAttributeFromDOMAttribute("type");
    
            if (p_oAttributes.type == "button") {
            
                p_oAttributes.type = "push";
            
            }
    
            if (!("disabled" in p_oAttributes)) {
    
                p_oAttributes.disabled = p_oElement.disabled;
    
            }
    
            setAttributeFromDOMAttribute("name");
            setAttributeFromDOMAttribute("value");
            setAttributeFromDOMAttribute("title");
    
        }

    
        switch (sSrcElementNodeName) {
        
        case "A":
            
            p_oAttributes.type = "link";
            
            setAttributeFromDOMAttribute("href");
            setAttributeFromDOMAttribute("target");
        
            break;
    
        case "INPUT":

            setFormElementProperties();

            if (!("checked" in p_oAttributes)) {
    
                p_oAttributes.checked = p_oElement.checked;
    
            }

            break;

        case "BUTTON":

            setFormElementProperties();

            oRootNode = p_oElement.parentNode.parentNode;

            if (Dom.hasClass(oRootNode, sClass + "-checked")) {
            
                p_oAttributes.checked = true;
            
            }

            if (Dom.hasClass(oRootNode, sClass + "-disabled")) {

                p_oAttributes.disabled = true;
            
            }

            p_oElement.removeAttribute("value");

            p_oElement.setAttribute("type", "button");

            break;
        
        }

        p_oElement.removeAttribute("id");
        p_oElement.removeAttribute("name");
        
        if (!("tabindex" in p_oAttributes)) {

            p_oAttributes.tabindex = p_oElement.tabIndex;

        }
    
        if (!("label" in p_oAttributes)) {
    
            // Set the "label" property
        
            sText = sSrcElementNodeName == "INPUT" ? 
                            p_oElement.value : p_oElement.innerHTML;
        
    
            if (sText && sText.length > 0) {
                
                p_oAttributes.label = sText;
                
            } 
    
        }
    
    }
    
    
    /**
    * @method initConfig
    * @description Initializes the set of configuration attributes that are 
    * used to instantiate the button.
    * @private
    * @param {Object} Object representing the button's set of 
    * configuration attributes.
    */
    function initConfig(p_oConfig) {
    
        var oAttributes = p_oConfig.attributes,
            oSrcElement = oAttributes.srcelement,
            sSrcElementNodeName = oSrcElement.nodeName.toUpperCase(),
            me = this;
    
    
        if (sSrcElementNodeName == this.NODE_NAME) {
    
            p_oConfig.element = oSrcElement;
            p_oConfig.id = oSrcElement.id;

            Dom.getElementsBy(function (p_oElement) {
            
                switch (p_oElement.nodeName.toUpperCase()) {
                
                case "BUTTON":
                case "A":
                case "INPUT":

                    setAttributesFromSrcElement.call(me, p_oElement, 
                        oAttributes);

                    break;                        
                
                }
            
            }, "*", oSrcElement);
        
        }
        else {
    
            switch (sSrcElementNodeName) {

            case "BUTTON":
            case "A":
            case "INPUT":

                setAttributesFromSrcElement.call(this, oSrcElement, 
                    oAttributes);

                break;

            }
        
        }
    
    }



    //  Constructor

    YAHOO.widget.Button = function (p_oElement, p_oAttributes) {
    
		if (!Overlay && YAHOO.widget.Overlay) {
		
			Overlay = YAHOO.widget.Overlay;
		
		}


		if (!Menu && YAHOO.widget.Menu) {
		
			Menu = YAHOO.widget.Menu;
		
		}


        var fnSuperClass = YAHOO.widget.Button.superclass.constructor,
            oConfig,
            oElement;
    

        if (arguments.length == 1 && !Lang.isString(p_oElement) && !p_oElement.nodeName) {
    
            if (!p_oElement.id) {
    
                p_oElement.id = Dom.generateId();
    
                YAHOO.log("No value specified for the button's \"id\" " + 
                    "attribute. Setting button id to \"" + p_oElement.id + 
                    "\".", "info", this.toString());
    
            }
    
            YAHOO.log("No source HTML element.  Building the button " +
                    "using the set of configuration attributes.", "info", this.toString());
    
            fnSuperClass.call(this, (this.createButtonElement(p_oElement.type)), p_oElement);
    
        }
        else {
    
            oConfig = { element: null, attributes: (p_oAttributes || {}) };
    
    
            if (Lang.isString(p_oElement)) {
    
                oElement = Dom.get(p_oElement);
    
                if (oElement) {

                    if (!oConfig.attributes.id) {
                    
                        oConfig.attributes.id = p_oElement;
                    
                    }
    
                    YAHOO.log("Building the button using an existing " + 
                            "HTML element as a source element.", "info", this.toString());
                
                
                    oConfig.attributes.srcelement = oElement;
                
                    initConfig.call(this, oConfig);
                
                
                    if (!oConfig.element) {
                
                        YAHOO.log("Source element could not be used " +
                                "as is.  Creating a new HTML element for " + 
                                "the button.", "info", this.toString());
                
                        oConfig.element = this.createButtonElement(oConfig.attributes.type);
                
                    }
                
                    fnSuperClass.call(this, oConfig.element, oConfig.attributes);
    
                }
    
            }
            else if (p_oElement.nodeName) {
    
                if (!oConfig.attributes.id) {
    
                    if (p_oElement.id) {
        
                        oConfig.attributes.id = p_oElement.id;
                    
                    }
                    else {
        
                        oConfig.attributes.id = Dom.generateId();
        
                        YAHOO.log("No value specified for the button's " +
                            "\"id\" attribute. Setting button id to \"" + 
                            oConfig.attributes.id + "\".", "info", this.toString());
        
                    }
    
                }
    
                YAHOO.log("Building the button using an existing HTML " + 
                    "element as a source element.", "info", this.toString());
    
    
                oConfig.attributes.srcelement = p_oElement;
        
                initConfig.call(this, oConfig);
        
        
                if (!oConfig.element) {
    
                    YAHOO.log("Source element could not be used as is." +
                            "  Creating a new HTML element for the button.", 
                            "info", this.toString());
            
                    oConfig.element = this.createButtonElement(oConfig.attributes.type);
            
                }
            
                fnSuperClass.call(this, oConfig.element, oConfig.attributes);
            
            }
    
        }
    
    };



    YAHOO.extend(YAHOO.widget.Button, YAHOO.util.Element, {
    
    
        // Protected properties
        
        
        /** 
        * @property _button
        * @description Object reference to the button's internal 
        * <code>&#60;a&#62;</code> or <code>&#60;button&#62;</code> element.
        * @default null
        * @protected
        * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-48250443">HTMLAnchorElement</a>|<a href="
        * http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-html.html
        * #ID-34812697">HTMLButtonElement</a>
        */
        _button: null,
        
        
        /** 
        * @property _menu
        * @description Object reference to the button's menu.
        * @default null
        * @protected
        * @type {<a href="YAHOO.widget.Overlay.html">YAHOO.widget.Overlay</a>|
        * <a href="YAHOO.widget.Menu.html">YAHOO.widget.Menu</a>}
        */
        _menu: null,
        
        
        /** 
        * @property _hiddenFields
        * @description Object reference to the <code>&#60;input&#62;</code>  
        * element, or array of HTML form elements used to represent the button
        *  when its parent form is submitted.
        * @default null
        * @protected
        * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-6043025">HTMLInputElement</a>|Array
        */
        _hiddenFields: null,
        
        
        /** 
        * @property _onclickAttributeValue
        * @description Object reference to the button's current value for the 
        * "onclick" configuration attribute.
        * @default null
        * @protected
        * @type Object
        */
        _onclickAttributeValue: null,
        
        
        /** 
        * @property _activationKeyPressed
        * @description Boolean indicating if the key(s) that toggle the button's 
        * "active" state have been pressed.
        * @default false
        * @protected
        * @type Boolean
        */
        _activationKeyPressed: false,
        
        
        /** 
        * @property _activationButtonPressed
        * @description Boolean indicating if the mouse button that toggles 
        * the button's "active" state has been pressed.
        * @default false
        * @protected
        * @type Boolean
        */
        _activationButtonPressed: false,
        
        
        /** 
        * @property _hasKeyEventHandlers
        * @description Boolean indicating if the button's "blur", "keydown" and 
        * "keyup" event handlers are assigned
        * @default false
        * @protected
        * @type Boolean
        */
        _hasKeyEventHandlers: false,
        
        
        /** 
        * @property _hasMouseEventHandlers
        * @description Boolean indicating if the button's "mouseout," 
        * "mousedown," and "mouseup" event handlers are assigned
        * @default false
        * @protected
        * @type Boolean
        */
        _hasMouseEventHandlers: false,


        /** 
        * @property _nOptionRegionX
        * @description Number representing the X coordinate of the leftmost edge of the Button's 
        * option region.  Applies only to Buttons of type "split".
        * @default 0
        * @protected
        * @type Number
        */        
        _nOptionRegionX: 0,
        


        // Constants

        /**
        * @property CLASS_NAME_PREFIX
        * @description Prefix used for all class names applied to a Button.
        * @default "yui-"
        * @final
        * @type String
        */
        CLASS_NAME_PREFIX: "yui-",
        
        
        /**
        * @property NODE_NAME
        * @description The name of the node to be used for the button's 
        * root element.
        * @default "SPAN"
        * @final
        * @type String
        */
        NODE_NAME: "SPAN",
        
        
        /**
        * @property CHECK_ACTIVATION_KEYS
        * @description Array of numbers representing keys that (when pressed) 
        * toggle the button's "checked" attribute.
        * @default [32]
        * @final
        * @type Array
        */
        CHECK_ACTIVATION_KEYS: [32],
        
        
        /**
        * @property ACTIVATION_KEYS
        * @description Array of numbers representing keys that (when presed) 
        * toggle the button's "active" state.
        * @default [13, 32]
        * @final
        * @type Array
        */
        ACTIVATION_KEYS: [13, 32],
        
        
        /**
        * @property OPTION_AREA_WIDTH
        * @description Width (in pixels) of the area of a split button that  
        * when pressed will display a menu.
        * @default 20
        * @final
        * @type Number
        */
        OPTION_AREA_WIDTH: 20,
        
        
        /**
        * @property CSS_CLASS_NAME
        * @description String representing the CSS class(es) to be applied to  
        * the button's root element.
        * @default "button"
        * @final
        * @type String
        */
        CSS_CLASS_NAME: "button",
        
        
        
        // Protected attribute setter methods
        
        
        /**
        * @method _setType
        * @description Sets the value of the button's "type" attribute.
        * @protected
        * @param {String} p_sType String indicating the value for the button's 
        * "type" attribute.
        */
        _setType: function (p_sType) {
        
            if (p_sType == "split") {
        
                this.on("option", this._onOption);
        
            }
        
        },
        
        
        /**
        * @method _setLabel
        * @description Sets the value of the button's "label" attribute.
        * @protected
        * @param {HTML} p_sLabel String indicating the value for the button's 
        * "label" attribute.
        */
        _setLabel: function (p_sLabel) {

            this._button.innerHTML = p_sLabel;

            
            /*
                Remove and add the default class name from the root element
                for Gecko to ensure that the button shrinkwraps to the label.
                Without this the button will not be rendered at the correct 
                width when the label changes.  The most likely cause for this 
                bug is button's use of the Gecko-specific CSS display type of 
                "-moz-inline-box" to simulate "inline-block" supported by IE, 
                Safari and Opera.
            */
            
            var sClass,
                nGeckoVersion = UA.gecko;
				
            
            if (nGeckoVersion && nGeckoVersion < 1.9 && Dom.inDocument(this.get("element"))) {
            
                sClass = (this.CLASS_NAME_PREFIX + this.CSS_CLASS_NAME);

                this.removeClass(sClass);
                
                Lang.later(0, this, this.addClass, sClass);

            }
        
        },
        
        
        /**
        * @method _setTabIndex
        * @description Sets the value of the button's "tabindex" attribute.
        * @protected
        * @param {Number} p_nTabIndex Number indicating the value for the 
        * button's "tabindex" attribute.
        */
        _setTabIndex: function (p_nTabIndex) {
        
            this._button.tabIndex = p_nTabIndex;
        
        },
        
        
        /**
        * @method _setTitle
        * @description Sets the value of the button's "title" attribute.
        * @protected
        * @param {String} p_nTabIndex Number indicating the value for 
        * the button's "title" attribute.
        */
        _setTitle: function (p_sTitle) {
        
            if (this.get("type") != "link") {
        
                this._button.title = p_sTitle;
        
            }
        
        },
        
        
        /**
        * @method _setDisabled
        * @description Sets the value of the button's "disabled" attribute.
        * @protected
        * @param {Boolean} p_bDisabled Boolean indicating the value for 
        * the button's "disabled" attribute.
        */
        _setDisabled: function (p_bDisabled) {
        
            if (this.get("type") != "link") {
        
                if (p_bDisabled) {
        
                    if (this._menu) {
        
                        this._menu.hide();
        
                    }
        
                    if (this.hasFocus()) {
                    
                        this.blur();
                    
                    }
        
                    this._button.setAttribute("disabled", "disabled");
        
                    this.addStateCSSClasses("disabled");

                    this.removeStateCSSClasses("hover");
                    this.removeStateCSSClasses("active");
                    this.removeStateCSSClasses("focus");
        
                }
                else {
        
                    this._button.removeAttribute("disabled");
        
                    this.removeStateCSSClasses("disabled");
                
                }
        
            }
        
        },

        
        /**
        * @method _setHref
        * @description Sets the value of the button's "href" attribute.
        * @protected
        * @param {String} p_sHref String indicating the value for the button's 
        * "href" attribute.
        */
        _setHref: function (p_sHref) {
        
            if (this.get("type") == "link") {
        
                this._button.href = p_sHref;
            
            }
        
        },
        
        
        /**
        * @method _setTarget
        * @description Sets the value of the button's "target" attribute.
        * @protected
        * @param {String} p_sTarget String indicating the value for the button's 
        * "target" attribute.
        */
        _setTarget: function (p_sTarget) {
        
            if (this.get("type") == "link") {
        
                this._button.setAttribute("target", p_sTarget);
            
            }
        
        },
        
        
        /**
        * @method _setChecked
        * @description Sets the value of the button's "target" attribute.
        * @protected
        * @param {Boolean} p_bChecked Boolean indicating the value for  
        * the button's "checked" attribute.
        */
        _setChecked: function (p_bChecked) {
        
            var sType = this.get("type");
        
            if (sType == "checkbox" || sType == "radio") {
        
                if (p_bChecked) {
                    this.addStateCSSClasses("checked");
                }
                else {
                    this.removeStateCSSClasses("checked");
                }
        
            }
        
        },

        
        /**
        * @method _setMenu
        * @description Sets the value of the button's "menu" attribute.
        * @protected
        * @param {Object} p_oMenu Object indicating the value for the button's 
        * "menu" attribute.
        */
        _setMenu: function (p_oMenu) {

            var bLazyLoad = this.get("lazyloadmenu"),
                oButtonElement = this.get("element"),
                sMenuCSSClassName,
        
                /*
                    Boolean indicating if the value of p_oMenu is an instance 
                    of YAHOO.widget.Menu or YAHOO.widget.Overlay.
                */
        
                bInstance = false,
                oMenu,
                oMenuElement,
                oSrcElement;
        

			function onAppendTo() {

				oMenu.render(oButtonElement.parentNode);
				
				this.removeListener("appendTo", onAppendTo);
			
			}
			
			
			function setMenuContainer() {

				oMenu.cfg.queueProperty("container", oButtonElement.parentNode);
				
				this.removeListener("appendTo", setMenuContainer);
			
			}


			function initMenu() {
		
				var oContainer;
		
				if (oMenu) {

					Dom.addClass(oMenu.element, this.get("menuclassname"));
					Dom.addClass(oMenu.element, this.CLASS_NAME_PREFIX + this.get("type") + "-button-menu");

					oMenu.showEvent.subscribe(this._onMenuShow, null, this);
					oMenu.hideEvent.subscribe(this._onMenuHide, null, this);
					oMenu.renderEvent.subscribe(this._onMenuRender, null, this);


					if (Menu && oMenu instanceof Menu) {

						if (bLazyLoad) {

							oContainer = this.get("container");

							if (oContainer) {

								oMenu.cfg.queueProperty("container", oContainer);

							}
							else {

								this.on("appendTo", setMenuContainer);

							}

						}

						oMenu.cfg.queueProperty("clicktohide", false);

						oMenu.keyDownEvent.subscribe(this._onMenuKeyDown, this, true);
						oMenu.subscribe("click", this._onMenuClick, this, true);

						this.on("selectedMenuItemChange", this._onSelectedMenuItemChange);
		
						oSrcElement = oMenu.srcElement;
		
						if (oSrcElement && oSrcElement.nodeName.toUpperCase() == "SELECT") {

							oSrcElement.style.display = "none";
							oSrcElement.parentNode.removeChild(oSrcElement);
		
						}
		
					}
					else if (Overlay && oMenu instanceof Overlay) {
		
						if (!m_oOverlayManager) {
		
							m_oOverlayManager = new YAHOO.widget.OverlayManager();
						
						}
						
						m_oOverlayManager.register(oMenu);
						
					}
		
		
					this._menu = oMenu;

		
					if (!bInstance && !bLazyLoad) {
		
						if (Dom.inDocument(oButtonElement)) {
	
							oMenu.render(oButtonElement.parentNode);
						
						}
						else {
		
							this.on("appendTo", onAppendTo);
						
						}
					
					}
		
				}
		
			}

        
            if (Overlay) {
        
				if (Menu) {
				
					sMenuCSSClassName = Menu.prototype.CSS_CLASS_NAME;
				
				}
			
				if (p_oMenu && Menu && (p_oMenu instanceof Menu)) {
			
					oMenu = p_oMenu;
					bInstance = true;
			
					initMenu.call(this);
			
				}
				else if (Overlay && p_oMenu && (p_oMenu instanceof Overlay)) {
			
					oMenu = p_oMenu;
					bInstance = true;
			
					oMenu.cfg.queueProperty("visible", false);
			
					initMenu.call(this);
			
				}
				else if (Menu && Lang.isArray(p_oMenu)) {

					oMenu = new Menu(Dom.generateId(), { lazyload: bLazyLoad, itemdata: p_oMenu });
						
					this._menu = oMenu;
			
					this.on("appendTo", initMenu);
			
				}
				else if (Lang.isString(p_oMenu)) {
			
					oMenuElement = Dom.get(p_oMenu);
			
					if (oMenuElement) {
			
						if (Menu && Dom.hasClass(oMenuElement, sMenuCSSClassName) || 
							oMenuElement.nodeName.toUpperCase() == "SELECT") {
				
							oMenu = new Menu(p_oMenu, { lazyload: bLazyLoad });
				
							initMenu.call(this);
				
						}
						else if (Overlay) {
			
							oMenu = new Overlay(p_oMenu, { visible: false });
				
							initMenu.call(this);
				
						}
			
					}
			
				}
				else if (p_oMenu && p_oMenu.nodeName) {
			
					if (Menu && Dom.hasClass(p_oMenu, sMenuCSSClassName) || 
							p_oMenu.nodeName.toUpperCase() == "SELECT") {
			
						oMenu = new Menu(p_oMenu, { lazyload: bLazyLoad });
					
						initMenu.call(this);
			
					}
					else if (Overlay) {
			
						if (!p_oMenu.id) {
						
							Dom.generateId(p_oMenu);
						
						}
			
						oMenu = new Overlay(p_oMenu, { visible: false });
			
						initMenu.call(this);
					
					}
				
				}
            
            }
        
        },
        
        
        /**
        * @method _setOnClick
        * @description Sets the value of the button's "onclick" attribute.
        * @protected
        * @param {Object} p_oObject Object indicating the value for the button's 
        * "onclick" attribute.
        */
        _setOnClick: function (p_oObject) {
        
            /*
                Remove any existing listeners if a "click" event handler 
                has already been specified.
            */
        
            if (this._onclickAttributeValue && 
                (this._onclickAttributeValue != p_oObject)) {
        
                this.removeListener("click", this._onclickAttributeValue.fn);
        
                this._onclickAttributeValue = null;
        
            }
        
        
            if (!this._onclickAttributeValue && 
                Lang.isObject(p_oObject) && 
                Lang.isFunction(p_oObject.fn)) {
        
                this.on("click", p_oObject.fn, p_oObject.obj, p_oObject.scope);
        
                this._onclickAttributeValue = p_oObject;
        
            }
        
        },

        
        
        // Protected methods

        
        
        /**
        * @method _isActivationKey
        * @description Determines if the specified keycode is one that toggles  
        * the button's "active" state.
        * @protected
        * @param {Number} p_nKeyCode Number representing the keycode to 
        * be evaluated.
        * @return {Boolean}
        */
        _isActivationKey: function (p_nKeyCode) {
        
            var sType = this.get("type"),
                aKeyCodes = (sType == "checkbox" || sType == "radio") ? 
                    this.CHECK_ACTIVATION_KEYS : this.ACTIVATION_KEYS,
        
                nKeyCodes = aKeyCodes.length,
                bReturnVal = false,
                i;
        

            if (nKeyCodes > 0) {
        
                i = nKeyCodes - 1;
        
                do {
        
                    if (p_nKeyCode == aKeyCodes[i]) {
        
                        bReturnVal = true;
                        break;
        
                    }
        
                }
                while (i--);
            
            }
            
            return bReturnVal;
        
        },
        
        
        /**
        * @method _isSplitButtonOptionKey
        * @description Determines if the specified keycode is one that toggles  
        * the display of the split button's menu.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        * @return {Boolean}
        */
        _isSplitButtonOptionKey: function (p_oEvent) {

			var bShowMenu = (Event.getCharCode(p_oEvent) == 40);


			var onKeyPress = function (p_oEvent) {

				Event.preventDefault(p_oEvent);

				this.removeListener("keypress", onKeyPress);
			
			};


			// Prevent the browser from scrolling the window
			if (bShowMenu) {

				if (UA.opera) {
	
					this.on("keypress", onKeyPress);
	
				}

				Event.preventDefault(p_oEvent);
			}

            return bShowMenu;
        
        },
        
        
        /**
        * @method _addListenersToForm
        * @description Adds event handlers to the button's form.
        * @protected
        */
        _addListenersToForm: function () {
        
            var oForm = this.getForm(),
                onFormKeyPress = YAHOO.widget.Button.onFormKeyPress,
                bHasKeyPressListener,
                oSrcElement,
                aListeners,
                nListeners,
                i;
        
        
            if (oForm) {
        
                Event.on(oForm, "reset", this._onFormReset, null, this);
                Event.on(oForm, "submit", this._onFormSubmit, null, this);
        
                oSrcElement = this.get("srcelement");
        
        
                if (this.get("type") == "submit" || 
                    (oSrcElement && oSrcElement.type == "submit")) 
                {
                
                    aListeners = Event.getListeners(oForm, "keypress");
                    bHasKeyPressListener = false;
            
                    if (aListeners) {
            
                        nListeners = aListeners.length;
        
                        if (nListeners > 0) {
            
                            i = nListeners - 1;
                            
                            do {
               
                                if (aListeners[i].fn == onFormKeyPress) {
                
                                    bHasKeyPressListener = true;
                                    break;
                                
                                }
                
                            }
                            while (i--);
                        
                        }
                    
                    }
            
            
                    if (!bHasKeyPressListener) {
               
                        Event.on(oForm, "keypress", onFormKeyPress);
            
                    }
        
                }
            
            }
        
        },
        
        
        
        /**
        * @method _showMenu
        * @description Shows the button's menu.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object 
        * passed back by the event utility (YAHOO.util.Event) that triggered 
        * the display of the menu.
        */
        _showMenu: function (p_oEvent) {

            if (YAHOO.widget.MenuManager) {
                YAHOO.widget.MenuManager.hideVisible();
            }

        
            if (m_oOverlayManager) {
                m_oOverlayManager.hideAll();
            }


            var oMenu = this._menu,
            	aMenuAlignment = this.get("menualignment"),
            	bFocusMenu = this.get("focusmenu"),
				fnFocusMethod;


			if (this._renderedMenu) {

				oMenu.cfg.setProperty("context", 
								[this.get("element"), aMenuAlignment[0], aMenuAlignment[1]]);
	
				oMenu.cfg.setProperty("preventcontextoverlap", true);
				oMenu.cfg.setProperty("constraintoviewport", true);

			}
			else {

				oMenu.cfg.queueProperty("context", 
								[this.get("element"), aMenuAlignment[0], aMenuAlignment[1]]);
	
				oMenu.cfg.queueProperty("preventcontextoverlap", true);
				oMenu.cfg.queueProperty("constraintoviewport", true);
			
			}


			/*
				 Refocus the Button before showing its Menu in case the call to 
				 YAHOO.widget.MenuManager.hideVisible() resulted in another element in the 
				 DOM being focused after another Menu was hidden.
			*/
			
			this.focus();


            if (Menu && oMenu && (oMenu instanceof Menu)) {

				// Since Menus automatically focus themselves when made visible, temporarily 
				// replace the Menu focus method so that the value of the Button's "focusmenu"
				// attribute determines if the Menu should be focus when made visible.

				fnFocusMethod = oMenu.focus;

				oMenu.focus = function () {};

				if (this._renderedMenu) {

					oMenu.cfg.setProperty("minscrollheight", this.get("menuminscrollheight"));
					oMenu.cfg.setProperty("maxheight", this.get("menumaxheight"));
				
				}
				else {

					oMenu.cfg.queueProperty("minscrollheight", this.get("menuminscrollheight"));
					oMenu.cfg.queueProperty("maxheight", this.get("menumaxheight"));
				
				}


                oMenu.show();

        		oMenu.focus = fnFocusMethod;

				oMenu.align();
        

                /*
                    Stop the propagation of the event so that the MenuManager 
                    doesn't blur the menu after it gets focus.
                */
        
                if (p_oEvent.type == "mousedown") {
                    Event.stopPropagation(p_oEvent);
                }

        
                if (bFocusMenu) { 
                    oMenu.focus();
                }

            }
            else if (Overlay && oMenu && (oMenu instanceof Overlay)) {

				if (!this._renderedMenu) {
		            oMenu.render(this.get("element").parentNode);
				}

                oMenu.show();
				oMenu.align();

            }
        
        },
        
        
        /**
        * @method _hideMenu
        * @description Hides the button's menu.
        * @protected
        */
        _hideMenu: function () {
        
            var oMenu = this._menu;
        
            if (oMenu) {
        
                oMenu.hide();
        
            }
        
        },
        
        
        
        
        // Protected event handlers
        
        
        /**
        * @method _onMouseOver
        * @description "mouseover" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onMouseOver: function (p_oEvent) {
        
        	var sType = this.get("type"),
        		oElement,
				nOptionRegionX;


			if (sType === "split") {

				oElement = this.get("element");
				nOptionRegionX = 
					(Dom.getX(oElement) + (oElement.offsetWidth - this.OPTION_AREA_WIDTH));
					
				this._nOptionRegionX = nOptionRegionX;
			
			}
        

            if (!this._hasMouseEventHandlers) {
        
				if (sType === "split") {
        
	        		this.on("mousemove", this._onMouseMove);

        		}

                this.on("mouseout", this._onMouseOut);
        
                this._hasMouseEventHandlers = true;
        
            }
        

            this.addStateCSSClasses("hover");


			if (sType === "split" && (Event.getPageX(p_oEvent) > nOptionRegionX)) {
	
				this.addStateCSSClasses("hoveroption");
	
			}

        
            if (this._activationButtonPressed) {
        
                this.addStateCSSClasses("active");
        
            }
        
        
            if (this._bOptionPressed) {
        
                this.addStateCSSClasses("activeoption");
            
            }


            if (this.isActive() || this._bOptionPressed) {
        
                Event.removeListener(document, "mouseup", this._onDocumentMouseUp);
        
            }

        },


        /**
        * @method _onMouseMove
        * @description "mousemove" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */        
        _onMouseMove: function (p_oEvent) {
        
        	var nOptionRegionX = this._nOptionRegionX;
        
        	if (nOptionRegionX) {

				if (Event.getPageX(p_oEvent) > nOptionRegionX) {
					
					this.addStateCSSClasses("hoveroption");
	
				}
				else {

					this.removeStateCSSClasses("hoveroption");
				
				}
				
        	}
        
        },
        
        /**
        * @method _onMouseOut
        * @description "mouseout" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onMouseOut: function (p_oEvent) {

			var sType = this.get("type");
        
            this.removeStateCSSClasses("hover");
        

            if (sType != "menu") {
        
                this.removeStateCSSClasses("active");
        
            }
        

            if (this.isActive() || this._bOptionPressed) {
        
                Event.on(document, "mouseup", this._onDocumentMouseUp, null, this);
        
            }


			if (sType === "split" && (Event.getPageX(p_oEvent) > this._nOptionRegionX)) {
			
				this.removeStateCSSClasses("hoveroption");
	
			}
            
        },
        
        
        /**
        * @method _onDocumentMouseUp
        * @description "mouseup" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onDocumentMouseUp: function (p_oEvent) {
        
            this._activationButtonPressed = false;
            this._bOptionPressed = false;
        
            var sType = this.get("type"),
                oTarget,
                oMenuElement;
        
            if (sType == "menu" || sType == "split") {

                oTarget = Event.getTarget(p_oEvent);
                oMenuElement = this._menu.element;
        
                if (oTarget != oMenuElement && 
                    !Dom.isAncestor(oMenuElement, oTarget)) {

                    this.removeStateCSSClasses((sType == "menu" ? 
                        "active" : "activeoption"));
            
                    this._hideMenu();

                }
        
            }
        
            Event.removeListener(document, "mouseup", this._onDocumentMouseUp);
        
        },
        
        
        /**
        * @method _onMouseDown
        * @description "mousedown" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onMouseDown: function (p_oEvent) {

            var sType,
            	bReturnVal = true;
        
        
            function onMouseUp() {
            
                this._hideMenu();
                this.removeListener("mouseup", onMouseUp);
            
            }
        
        
            if ((p_oEvent.which || p_oEvent.button) == 1) {
        
        
                if (!this.hasFocus()) {
                    Lang.later(0, this, this.focus);
                    //this.focus();
                }
        
        
                sType = this.get("type");
        
        
                if (sType == "split") {
                
                    if (Event.getPageX(p_oEvent) > this._nOptionRegionX) {
                        
                        this.fireEvent("option", p_oEvent);
						bReturnVal = false;
        
                    }
                    else {
        
                        this.addStateCSSClasses("active");
        
                        this._activationButtonPressed = true;
        
                    }
        
                }
                else if (sType == "menu") {
        
                    if (this.isActive()) {
        
                        this._hideMenu();
        
                        this._activationButtonPressed = false;
        
                    }
                    else {
        
                        this._showMenu(p_oEvent);
        
                        this._activationButtonPressed = true;
                    
                    }
        
                }
                else {
        
                    this.addStateCSSClasses("active");
        
                    this._activationButtonPressed = true;
                
                }
        
        
        
                if (sType == "split" || sType == "menu") {

                    this._hideMenuTimer = Lang.later(250, this, this.on, ["mouseup", onMouseUp]);
        
                }
        
            }
            
            return bReturnVal;
            
        },
        
        
        /**
        * @method _onMouseUp
        * @description "mouseup" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onMouseUp: function (p_oEvent) {
            this.inMouseDown = false;
        
            var sType = this.get("type"),
            	oHideMenuTimer = this._hideMenuTimer,
            	bReturnVal = true;
        
        
            if (oHideMenuTimer) {
  
  				oHideMenuTimer.cancel();
        
            }
        
        
            if (sType == "checkbox" || sType == "radio") {
                if ((p_oEvent.which || p_oEvent.button) != 1) {
                    return;
                }

                this.set("checked", !(this.get("checked")));
            
            }
        
        
            this._activationButtonPressed = false;
            
        
            if (sType != "menu") {
        
                this.removeStateCSSClasses("active");
            
            }

                
			if (sType == "split" && Event.getPageX(p_oEvent) > this._nOptionRegionX) {
				
				bReturnVal = false;

			}

			return bReturnVal;
            
        },
        
        
        /**
        * @method _onFocus
        * @description "focus" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onFocus: function (p_oEvent) {
        
            var oElement;
        
            this.addStateCSSClasses("focus");
        
            if (this._activationKeyPressed) {
        
                this.addStateCSSClasses("active");
           
            }
        
            m_oFocusedButton = this;
        
        
            if (!this._hasKeyEventHandlers) {
        
                oElement = this._button;
        
                Event.on(oElement, "blur", this._onBlur, null, this);
                Event.on(oElement, "keydown", this._onKeyDown, null, this);
                Event.on(oElement, "keyup", this._onKeyUp, null, this);
        
                this._hasKeyEventHandlers = true;
        
            }
        
        
            this.fireEvent("focus", p_oEvent);
        
        },
        
        
        /**
        * @method _onBlur
        * @description "blur" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onBlur: function (p_oEvent) {
        
            this.removeStateCSSClasses("focus");
        
            if (this.get("type") != "menu") {
        
                this.removeStateCSSClasses("active");

            }    
        
            if (this._activationKeyPressed) {
        
                Event.on(document, "keyup", this._onDocumentKeyUp, null, this);
        
            }
        
        
            m_oFocusedButton = null;
        
            this.fireEvent("blur", p_oEvent);
           
        },
        
        
        /**
        * @method _onDocumentKeyUp
        * @description "keyup" event handler for the document.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onDocumentKeyUp: function (p_oEvent) {
        
            if (this._isActivationKey(Event.getCharCode(p_oEvent))) {
        
                this._activationKeyPressed = false;
                
                Event.removeListener(document, "keyup", this._onDocumentKeyUp);
            
            }
        
        },
        
        
        /**
        * @method _onKeyDown
        * @description "keydown" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onKeyDown: function (p_oEvent) {
        
            var oMenu = this._menu;
        
        
            if (this.get("type") == "split" && 
                this._isSplitButtonOptionKey(p_oEvent)) {
        
                this.fireEvent("option", p_oEvent);
        
            }
            else if (this._isActivationKey(Event.getCharCode(p_oEvent))) {
        
                if (this.get("type") == "menu") {
        
                    this._showMenu(p_oEvent);
        
                }
                else {
        
                    this._activationKeyPressed = true;
                    
                    this.addStateCSSClasses("active");
                
                }
            
            }
        
        
            if (oMenu && oMenu.cfg.getProperty("visible") && 
                Event.getCharCode(p_oEvent) == 27) {
            
                oMenu.hide();
                this.focus();
            
            }
        
        },
        
        
        /**
        * @method _onKeyUp
        * @description "keyup" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onKeyUp: function (p_oEvent) {
        
            var sType;
        
            if (this._isActivationKey(Event.getCharCode(p_oEvent))) {
        
                sType = this.get("type");
        
                if (sType == "checkbox" || sType == "radio") {
        
                    this.set("checked", !(this.get("checked")));
                
                }
        
                this._activationKeyPressed = false;
        
                if (this.get("type") != "menu") {
        
                    this.removeStateCSSClasses("active");
        
                }
        
            }
        
        },
        
        
        /**
        * @method _onClick
        * @description "click" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onClick: function (p_oEvent) {
        
            var sType = this.get("type"),
                oForm,
                oSrcElement,
                bReturnVal;
        

			switch (sType) {

			case "submit":

				if (p_oEvent.returnValue !== false) {

					this.submitForm();

				}

				break;

			case "reset":

				oForm = this.getForm();

				if (oForm) {

					oForm.reset();

				}

				break;


			case "split":

				if (this._nOptionRegionX > 0 && 
						(Event.getPageX(p_oEvent) > this._nOptionRegionX)) {

					bReturnVal = false;

				}
				else {

					this._hideMenu();

					oSrcElement = this.get("srcelement");

					if (oSrcElement && oSrcElement.type == "submit" && 
							p_oEvent.returnValue !== false) {

						this.submitForm();

					}

				}

				break;

			}

			return bReturnVal;
        
        },
        
        
        /**
        * @method _onDblClick
        * @description "dblclick" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onDblClick: function (p_oEvent) {
        
            var bReturnVal = true;
    
			if (this.get("type") == "split" && Event.getPageX(p_oEvent) > this._nOptionRegionX) {

				bReturnVal = false;
			
			}
        
        	return bReturnVal;
        
        },        
        
        
        /**
        * @method _onAppendTo
        * @description "appendTo" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onAppendTo: function (p_oEvent) {
        
            /*
                It is necessary to call "_addListenersToForm" using 
                "setTimeout" to make sure that the button's "form" property 
                returns a node reference.  Sometimes, if you try to get the 
                reference immediately after appending the field, it is null.
            */
        
            Lang.later(0, this, this._addListenersToForm);
        
        },
        
        
        /**
        * @method _onFormReset
        * @description "reset" event handler for the button's form.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event 
        * object passed back by the event utility (YAHOO.util.Event).
        */
        _onFormReset: function (p_oEvent) {
        
            var sType = this.get("type"),
                oMenu = this._menu;
        
            if (sType == "checkbox" || sType == "radio") {
        
                this.resetValue("checked");
        
            }
        
        
            if (Menu && oMenu && (oMenu instanceof Menu)) {
        
                this.resetValue("selectedMenuItem");
        
            }
        
        },


        /**
        * @method _onFormSubmit
        * @description "submit" event handler for the button's form.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event 
        * object passed back by the event utility (YAHOO.util.Event).
        */        
        _onFormSubmit: function (p_oEvent) {
        
        	this.createHiddenFields();
        
        },
        
        
        /**
        * @method _onDocumentMouseDown
        * @description "mousedown" event handler for the document.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onDocumentMouseDown: function (p_oEvent) {

            var oTarget = Event.getTarget(p_oEvent),
                oButtonElement = this.get("element"),
                oMenuElement = this._menu.element;
           
            function findTargetInSubmenus(aSubmenus) {
                var i, iMax, oSubmenuElement;
                if (!aSubmenus) {
                    return true;
                }
                for (i = 0, iMax = aSubmenus.length; i < iMax; i++) {
                    oSubmenuElement = aSubmenus[i].element;
                    if (oTarget == oSubmenuElement || Dom.isAncestor(oSubmenuElement, oTarget)) {
                        return true;
                    }
                    if (aSubmenus[i] && aSubmenus[i].getSubmenus) {
                        if (findTargetInSubmenus(aSubmenus[i].getSubmenus())) {
                            return true;
                        }
                    }
                }
        
                return false;
            }

            if (oTarget != oButtonElement && 
                !Dom.isAncestor(oButtonElement, oTarget) && 
                oTarget != oMenuElement && 
                !Dom.isAncestor(oMenuElement, oTarget)) {
                
                
                if (this._menu  && this._menu.getSubmenus) {
                    if (!findTargetInSubmenus(this._menu.getSubmenus())) {
                        return;
                    }
                }
                

                this._hideMenu();

				//	In IE when the user mouses down on a focusable element
				//	that element will be focused and become the "activeElement".
				//	(http://msdn.microsoft.com/en-us/library/ms533065(VS.85).aspx)
				//	However, there is a bug in IE where if there is a  
				//	positioned element with a focused descendant that is 
				//	hidden in response to the mousedown event, the target of 
				//	the mousedown event will appear to have focus, but will 
				//	not be set as the activeElement.  This will result 
				//	in the element not firing key events, even though it
				//	appears to have focus.	The following call to "setActive"
				//	fixes this bug.

                if (UA.ie && (UA.ie < 9) && oTarget.focus) {
					oTarget.setActive();
				}
        
                Event.removeListener(document, "mousedown", 
                    this._onDocumentMouseDown);    
            
            }
        
        },
        
        
        /**
        * @method _onOption
        * @description "option" event handler for the button.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onOption: function (p_oEvent) {
        
            if (this.hasClass(this.CLASS_NAME_PREFIX + "split-button-activeoption")) {
        
                this._hideMenu();
        
                this._bOptionPressed = false;
        
            }
            else {
        
                this._showMenu(p_oEvent);    
        
                this._bOptionPressed = true;
        
            }
        
        },
        
        
        /**
        * @method _onMenuShow
        * @description "show" event handler for the button's menu.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        */
        _onMenuShow: function (p_sType) {
        
            Event.on(document, "mousedown", this._onDocumentMouseDown, 
                null, this);
        
            var sState = (this.get("type") == "split") ? "activeoption" : "active";
        
            this.addStateCSSClasses(sState);
        
        },
        
        
        /**
        * @method _onMenuHide
        * @description "hide" event handler for the button's menu.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        */
        _onMenuHide: function (p_sType) {
            
            var sState = (this.get("type") == "split") ? "activeoption" : "active";
        
            this.removeStateCSSClasses(sState);
        
        
            if (this.get("type") == "split") {
        
                this._bOptionPressed = false;
            
            }
        
        },
        
        
        /**
        * @method _onMenuKeyDown
        * @description "keydown" event handler for the button's menu.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        */
        _onMenuKeyDown: function (p_sType, p_aArgs) {
        
            var oEvent = p_aArgs[0];
        
            if (Event.getCharCode(oEvent) == 27) {
        
                this.focus();
        
                if (this.get("type") == "split") {
                
                    this._bOptionPressed = false;
                
                }
        
            }
        
        },
        
        
        /**
        * @method _onMenuRender
        * @description "render" event handler for the button's menu.
        * @private
        * @param {String} p_sType String representing the name of the  
        * event thatwas fired.
        */
        _onMenuRender: function (p_sType) {
        
            var oButtonElement = this.get("element"),
                oButtonParent = oButtonElement.parentNode,
				oMenu = this._menu,
                oMenuElement = oMenu.element,
				oSrcElement = oMenu.srcElement,
				oItem;
        
        
            if (oButtonParent != oMenuElement.parentNode) {
        
                oButtonParent.appendChild(oMenuElement);
            
            }

			this._renderedMenu = true;

			//	If the user has designated an <option> of the Menu's source 
			//	<select> element to be selected, sync the selectedIndex with 
			//	the "selectedMenuItem" Attribute.

			if (oSrcElement && 
					oSrcElement.nodeName.toLowerCase() === "select" && 
					oSrcElement.value) {
				
				
				oItem = oMenu.getItem(oSrcElement.selectedIndex);
				
				//	Set the value of the "selectedMenuItem" attribute
				//	silently since this is the initial set--synchronizing 
				//	the value of the source <SELECT> element in the DOM with 
				//	its corresponding Menu instance.

				this.set("selectedMenuItem", oItem, true);
				
				//	Call the "_onSelectedMenuItemChange" method since the 
				//	attribute was set silently.

				this._onSelectedMenuItemChange({ newValue: oItem });
				
			}

        },

        
        
        /**
        * @method _onMenuClick
        * @description "click" event handler for the button's menu.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        */
        _onMenuClick: function (p_sType, p_aArgs) {

            var oItem = p_aArgs[1],
                oSrcElement;
        
            if (oItem) {
        
				this.set("selectedMenuItem", oItem);

                oSrcElement = this.get("srcelement");
            
                if (oSrcElement && oSrcElement.type == "submit") {
        
                    this.submitForm();
            
                }
            
                this._hideMenu();
            
            }
        
        },


        /**
        * @method _onSelectedMenuItemChange
        * @description "selectedMenuItemChange" event handler for the Button's
		* "selectedMenuItem" attribute.
        * @param {Event} event Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
		_onSelectedMenuItemChange: function (event) {
		
			var oSelected = event.prevValue,
				oItem = event.newValue,
				sPrefix = this.CLASS_NAME_PREFIX;

			if (oSelected) {
				Dom.removeClass(oSelected.element, (sPrefix + "button-selectedmenuitem"));
			}
			
			if (oItem) {
				Dom.addClass(oItem.element, (sPrefix + "button-selectedmenuitem"));
			}
			
		},        
        

        /**
        * @method _onLabelClick
        * @description "click" event handler for the Button's
		* <code>&#60;label&#62;</code> element.
        * @param {Event} event Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
		_onLabelClick: function (event) {

			this.focus();

			var sType = this.get("type");

			if (sType == "radio" || sType == "checkbox") {
				this.set("checked", (!this.get("checked")));						
			}
			
		},

        
        // Public methods
        
        
        /**
        * @method createButtonElement
        * @description Creates the button's HTML elements.
        * @param {String} p_sType String indicating the type of element 
        * to create.
        * @return {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-58190037">HTMLElement</a>}
        */
        createButtonElement: function (p_sType) {
        
            var sNodeName = this.NODE_NAME,
                oElement = document.createElement(sNodeName);
        
            oElement.innerHTML =  "<" + sNodeName + " class=\"first-child\">" + 
                (p_sType == "link" ? "<a></a>" : 
                "<button type=\"button\"></button>") + "</" + sNodeName + ">";
        
            return oElement;
        
        },

        
        /**
        * @method addStateCSSClasses
        * @description Appends state-specific CSS classes to the button's root 
        * DOM element.
        */
        addStateCSSClasses: function (p_sState) {
        
            var sType = this.get("type"),
				sPrefix = this.CLASS_NAME_PREFIX;
        
            if (Lang.isString(p_sState)) {
        
                if (p_sState != "activeoption" && p_sState != "hoveroption") {
        
                    this.addClass(sPrefix + this.CSS_CLASS_NAME + ("-" + p_sState));
        
                }
        
                this.addClass(sPrefix + sType + ("-button-" + p_sState));
            
            }
        
        },
        
        
        /**
        * @method removeStateCSSClasses
        * @description Removes state-specific CSS classes to the button's root 
        * DOM element.
        */
        removeStateCSSClasses: function (p_sState) {
        
            var sType = this.get("type"),
				sPrefix = this.CLASS_NAME_PREFIX;
        
            if (Lang.isString(p_sState)) {
        
                this.removeClass(sPrefix + this.CSS_CLASS_NAME + ("-" + p_sState));
                this.removeClass(sPrefix + sType + ("-button-" + p_sState));
            
            }
        
        },
        
        
        /**
        * @method createHiddenFields
        * @description Creates the button's hidden form field and appends it 
        * to its parent form.
        * @return {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-6043025">HTMLInputElement</a>|Array}
        */
        createHiddenFields: function () {
        
            this.removeHiddenFields();
        
            var oForm = this.getForm(),
                oButtonField,
                sType,
                bCheckable,
                oMenu,
                oMenuItem,
                sButtonName,
                oValue,
                oMenuField,
                oReturnVal,
				sMenuFieldName,
				oMenuSrcElement,
				bMenuSrcElementIsSelect = false;
        
        
            if (oForm && !this.get("disabled")) {
        
                sType = this.get("type");
                bCheckable = (sType == "checkbox" || sType == "radio");
        
        
                if ((bCheckable && this.get("checked")) || (m_oSubmitTrigger == this)) {
                
                    YAHOO.log("Creating hidden field.", "info", this.toString());
        
                    oButtonField = createInputElement((bCheckable ? sType : "hidden"),
                                    this.get("name"), this.get("value"), this.get("checked"));
            
            
                    if (oButtonField) {
            
                        if (bCheckable) {
            
                            oButtonField.style.display = "none";
            
                        }
            
                        oForm.appendChild(oButtonField);
            
                    }
        
                }
                    
        
                oMenu = this._menu;
            
            
                if (Menu && oMenu && (oMenu instanceof Menu)) {
        
                    YAHOO.log("Creating hidden field for menu.", "info", this.toString());
        
                    oMenuItem = this.get("selectedMenuItem");
					oMenuSrcElement = oMenu.srcElement;
					bMenuSrcElementIsSelect = (oMenuSrcElement && 
												oMenuSrcElement.nodeName.toUpperCase() == "SELECT");

                    if (oMenuItem) {

						oValue = (oMenuItem.value === null || oMenuItem.value === "") ? 
									oMenuItem.cfg.getProperty("text") : oMenuItem.value;

						sButtonName = this.get("name");


						if (bMenuSrcElementIsSelect) {
						
							sMenuFieldName = oMenuSrcElement.name;
						
						}
						else if (sButtonName) {

							sMenuFieldName = (sButtonName + "_options");
						
						}
						

						if (oValue && sMenuFieldName) {
		
							oMenuField = createInputElement("hidden", sMenuFieldName, oValue);
							oForm.appendChild(oMenuField);
		
						}
                    
                    }
                    else if (bMenuSrcElementIsSelect) {
					
						oMenuField = oForm.appendChild(oMenuSrcElement);
                    
                    }
        
                }
            
            
                if (oButtonField && oMenuField) {
        
                    this._hiddenFields = [oButtonField, oMenuField];
        
                }
                else if (!oButtonField && oMenuField) {
        
                    this._hiddenFields = oMenuField;
                
                }
                else if (oButtonField && !oMenuField) {
        
                    this._hiddenFields = oButtonField;
                
                }
        
        		oReturnVal = this._hiddenFields;
        
            }

			return oReturnVal;
        
        },
        
        
        /**
        * @method removeHiddenFields
        * @description Removes the button's hidden form field(s) from its 
        * parent form.
        */
        removeHiddenFields: function () {
        
            var oField = this._hiddenFields,
                nFields,
                i;
        
            function removeChild(p_oElement) {
        
                if (Dom.inDocument(p_oElement)) {
        
                    p_oElement.parentNode.removeChild(p_oElement);

                }
                
            }
            
        
            if (oField) {
        
                if (Lang.isArray(oField)) {
        
                    nFields = oField.length;
                    
                    if (nFields > 0) {
                    
                        i = nFields - 1;
                        
                        do {
        
                            removeChild(oField[i]);
        
                        }
                        while (i--);
                    
                    }
                
                }
                else {
        
                    removeChild(oField);
        
                }
        
                this._hiddenFields = null;
            
            }
        
        },
        
        
        /**
        * @method submitForm
        * @description Submits the form to which the button belongs.  Returns  
        * true if the form was submitted successfully, false if the submission 
        * was cancelled.
        * @protected
        * @return {Boolean}
        */
        submitForm: function () {
        
            var oForm = this.getForm(),
        
                oSrcElement = this.get("srcelement"),
        
                /*
                    Boolean indicating if the event fired successfully 
                    (was not cancelled by any handlers)
                */
        
                bSubmitForm = false,
                
                oEvent;
        
        
            if (oForm) {
        
                if (this.get("type") == "submit" || (oSrcElement && oSrcElement.type == "submit")) {
        
                    m_oSubmitTrigger = this;
                    
                }
        
        
                if (UA.ie && (UA.ie < 9)) {
        
                    bSubmitForm = oForm.fireEvent("onsubmit");
        
                }
                else {  // Gecko, Opera, and Safari
        
                    oEvent = document.createEvent("HTMLEvents");
                    oEvent.initEvent("submit", true, true);
        
                    bSubmitForm = oForm.dispatchEvent(oEvent);
        
                }
        
        
                /*
                    In IE and Safari, dispatching a "submit" event to a form 
                    WILL cause the form's "submit" event to fire, but WILL NOT 
                    submit the form.  Therefore, we need to call the "submit" 
                    method as well.
                */
              
                if ((UA.ie || UA.webkit) && bSubmitForm) {
        
                    oForm.submit();
                
                }
            
            }
        
            return bSubmitForm;
            
        },
        
        
        /**
        * @method init
        * @description The Button class's initialization method.
        * @param {String} p_oElement String specifying the id attribute of the 
        * <code>&#60;input&#62;</code>, <code>&#60;button&#62;</code>,
        * <code>&#60;a&#62;</code>, or <code>&#60;span&#62;</code> element to 
        * be used to create the button.
        * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-6043025">HTMLInputElement</a>|<a href="http://
        * www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-html.html
        * #ID-34812697">HTMLButtonElement</a>|<a href="http://www.w3.org/TR
        * /2000/WD-DOM-Level-1-20000929/level-one-html.html#ID-33759296">
        * HTMLElement</a>} p_oElement Object reference for the 
        * <code>&#60;input&#62;</code>, <code>&#60;button&#62;</code>, 
        * <code>&#60;a&#62;</code>, or <code>&#60;span&#62;</code> element to be 
        * used to create the button.
        * @param {Object} p_oElement Object literal specifying a set of 
        * configuration attributes used to create the button.
        * @param {Object} p_oAttributes Optional. Object literal specifying a 
        * set of configuration attributes used to create the button.
        */
        init: function (p_oElement, p_oAttributes) {
        
            var sNodeName = p_oAttributes.type == "link" ? "a" : "button",
                oSrcElement = p_oAttributes.srcelement,
                oButton = p_oElement.getElementsByTagName(sNodeName)[0],
                oInput;


            if (!oButton) {

                oInput = p_oElement.getElementsByTagName("input")[0];


                if (oInput) {

                    oButton = document.createElement("button");
                    oButton.setAttribute("type", "button");

                    oInput.parentNode.replaceChild(oButton, oInput);
                
                }

            }

            this._button = oButton;


            YAHOO.widget.Button.superclass.init.call(this, p_oElement, p_oAttributes);


			var sId = this.get("id"),
				sButtonId = sId + "-button";


        	oButton.id = sButtonId;


			var aLabels,
				oLabel;


        	var hasLabel = function (element) {
        	
				return (element.htmlFor === sId);

        	};


			var setLabel = function () {

				oLabel.setAttribute((UA.ie ? "htmlFor" : "for"), sButtonId);
			
			};


			if (oSrcElement && this.get("type") != "link") {

				aLabels = Dom.getElementsBy(hasLabel, "label");

				if (Lang.isArray(aLabels) && aLabels.length > 0) {
				
					oLabel = aLabels[0];
				
				}

			}
        

            m_oButtons[sId] = this;

        	var sPrefix = this.CLASS_NAME_PREFIX;

            this.addClass(sPrefix + this.CSS_CLASS_NAME);
            this.addClass(sPrefix + this.get("type") + "-button");
        
            Event.on(this._button, "focus", this._onFocus, null, this);
            this.on("mouseover", this._onMouseOver);
			this.on("mousedown", this._onMouseDown);
			this.on("mouseup", this._onMouseUp);
            this.on("click", this._onClick);

			//	Need to reset the value of the "onclick" Attribute so that any
			//	handlers registered via the "onclick" Attribute are fired after 
			//	Button's default "_onClick" listener.

			var fnOnClick = this.get("onclick");

			this.set("onclick", null);
			this.set("onclick", fnOnClick);

            this.on("dblclick", this._onDblClick);


			var oParentNode;

            if (oLabel) {
            
				if (this.get("replaceLabel")) {

					this.set("label", oLabel.innerHTML);
					
					oParentNode = oLabel.parentNode;
					
					oParentNode.removeChild(oLabel);
					
				}
				else {

					this.on("appendTo", setLabel); 

					Event.on(oLabel, "click", this._onLabelClick, null, this);

					this._label = oLabel;
					
				}
            
            }
            
            this.on("appendTo", this._onAppendTo);
       
        

            var oContainer = this.get("container"),
                oElement = this.get("element"),
                bElInDoc = Dom.inDocument(oElement);


            if (oContainer) {
        
                if (oSrcElement && oSrcElement != oElement) {
                
                    oParentNode = oSrcElement.parentNode;

                    if (oParentNode) {
                    
                        oParentNode.removeChild(oSrcElement);
                    
                    }

                }
        
                if (Lang.isString(oContainer)) {
        
                    Event.onContentReady(oContainer, this.appendTo, oContainer, this);
        
                }
                else {
        
        			this.on("init", function () {
        			
        				Lang.later(0, this, this.appendTo, oContainer);
        			
        			});
        
                }
        
            }
            else if (!bElInDoc && oSrcElement && oSrcElement != oElement) {

                oParentNode = oSrcElement.parentNode;
        
                if (oParentNode) {
        
                    this.fireEvent("beforeAppendTo", {
                        type: "beforeAppendTo",
                        target: oParentNode
                    });
            
                    oParentNode.replaceChild(oElement, oSrcElement);
            
                    this.fireEvent("appendTo", {
                        type: "appendTo",
                        target: oParentNode
                    });
                
                }
        
            }
            else if (this.get("type") != "link" && bElInDoc && oSrcElement && 
                oSrcElement == oElement) {
        
                this._addListenersToForm();
        
            }
        
            YAHOO.log("Initialization completed.", "info", this.toString());
        

			this.fireEvent("init", {
				type: "init",
				target: this
			});        
        
        },
        
        
        /**
        * @method initAttributes
        * @description Initializes all of the configuration attributes used to  
        * create the button.
        * @param {Object} p_oAttributes Object literal specifying a set of 
        * configuration attributes used to create the button.
        */
        initAttributes: function (p_oAttributes) {
        
            var oAttributes = p_oAttributes || {};
        
            YAHOO.widget.Button.superclass.initAttributes.call(this, 
                oAttributes);
        
        
            /**
            * @attribute type
            * @description String specifying the button's type.  Possible 
            * values are: "push," "link," "submit," "reset," "checkbox," 
            * "radio," "menu," and "split."
            * @default "push"
            * @type String
			* @writeonce
            */
            this.setAttributeConfig("type", {
        
                value: (oAttributes.type || "push"),
                validator: Lang.isString,
                writeOnce: true,
                method: this._setType

            });
        
        
            /**
            * @attribute label
            * @description {HTML} specifying the button's text label 
            * or innerHTML.
            * @default null
            * @type String
            */
            this.setAttributeConfig("label", {
        
                value: oAttributes.label,
                validator: Lang.isString,
                method: this._setLabel
        
            });
        
        
            /**
            * @attribute value
            * @description Object specifying the value for the button.
            * @default null
            * @type Object
            */
            this.setAttributeConfig("value", {
        
                value: oAttributes.value
        
            });
        
        
            /**
            * @attribute name
            * @description String specifying the name for the button.
            * @default null
            * @type String
            */
            this.setAttributeConfig("name", {
        
                value: oAttributes.name,
                validator: Lang.isString
        
            });
        
        
            /**
            * @attribute tabindex
            * @description Number specifying the tabindex for the button.
            * @default null
            * @type Number
            */
            this.setAttributeConfig("tabindex", {
        
                value: oAttributes.tabindex,
                validator: Lang.isNumber,
                method: this._setTabIndex
        
            });
        
        
            /**
            * @attribute title
            * @description String specifying the title for the button.
            * @default null
            * @type String
            */
            this.configureAttribute("title", {
        
                value: oAttributes.title,
                validator: Lang.isString,
                method: this._setTitle
        
            });
        
        
            /**
            * @attribute disabled
            * @description Boolean indicating if the button should be disabled.  
            * (Disabled buttons are dimmed and will not respond to user input 
            * or fire events.  Does not apply to button's of type "link.")
            * @default false
            * @type Boolean
            */
            this.setAttributeConfig("disabled", {
        
                value: (oAttributes.disabled || false),
                validator: Lang.isBoolean,
                method: this._setDisabled
        
            });
        
        
            /**
            * @attribute href
            * @description String specifying the href for the button.  Applies
            * only to buttons of type "link."
            * @type String
            */
            this.setAttributeConfig("href", {
        
                value: oAttributes.href,
                validator: Lang.isString,
                method: this._setHref
        
            });
        
        
            /**
            * @attribute target
            * @description String specifying the target for the button.  
            * Applies only to buttons of type "link."
            * @type String
            */
            this.setAttributeConfig("target", {
        
                value: oAttributes.target,
                validator: Lang.isString,
                method: this._setTarget
        
            });
        
        
            /**
            * @attribute checked
            * @description Boolean indicating if the button is checked. 
            * Applies only to buttons of type "radio" and "checkbox."
            * @default false
            * @type Boolean
            */
            this.setAttributeConfig("checked", {
        
                value: (oAttributes.checked || false),
                validator: Lang.isBoolean,
                method: this._setChecked
        
            });
        
        
            /**
            * @attribute container
            * @description HTML element reference or string specifying the id 
            * attribute of the HTML element that the button's markup should be 
            * rendered into.
            * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
            * level-one-html.html#ID-58190037">HTMLElement</a>|String
            * @default null
			* @writeonce
            */
            this.setAttributeConfig("container", {
        
                value: oAttributes.container,
                writeOnce: true
        
            });
        
        
            /**
            * @attribute srcelement
            * @description Object reference to the HTML element (either 
            * <code>&#60;input&#62;</code> or <code>&#60;span&#62;</code>) 
            * used to create the button.
            * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
            * level-one-html.html#ID-58190037">HTMLElement</a>|String
            * @default null
			* @writeonce
            */
            this.setAttributeConfig("srcelement", {
        
                value: oAttributes.srcelement,
                writeOnce: true
        
            });
        
        
            /**
            * @attribute menu
            * @description Object specifying the menu for the button.  
            * The value can be one of the following:
            * <ul>
            * <li>Object specifying a rendered <a href="YAHOO.widget.Menu.html">
            * YAHOO.widget.Menu</a> instance.</li>
            * <li>Object specifying a rendered <a href="YAHOO.widget.Overlay.html">
            * YAHOO.widget.Overlay</a> instance.</li>
            * <li>String specifying the id attribute of the <code>&#60;div&#62;
            * </code> element used to create the menu.  By default the menu 
            * will be created as an instance of 
            * <a href="YAHOO.widget.Overlay.html">YAHOO.widget.Overlay</a>.  
            * If the <a href="YAHOO.widget.Menu.html#CSS_CLASS_NAME">
            * default CSS class name for YAHOO.widget.Menu</a> is applied to 
            * the <code>&#60;div&#62;</code> element, it will be created as an
            * instance of <a href="YAHOO.widget.Menu.html">YAHOO.widget.Menu
            * </a>.</li><li>String specifying the id attribute of the 
            * <code>&#60;select&#62;</code> element used to create the menu.
            * </li><li>Object specifying the <code>&#60;div&#62;</code> element
            * used to create the menu.</li>
            * <li>Object specifying the <code>&#60;select&#62;</code> element
            * used to create the menu.</li>
            * <li>Array of object literals, each representing a set of 
            * <a href="YAHOO.widget.MenuItem.html">YAHOO.widget.MenuItem</a> 
            * configuration attributes.</li>
            * <li>Array of strings representing the text labels for each menu 
            * item in the menu.</li>
            * </ul>
            * @type <a href="YAHOO.widget.Menu.html">YAHOO.widget.Menu</a>|<a 
            * href="YAHOO.widget.Overlay.html">YAHOO.widget.Overlay</a>|<a 
            * href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
            * one-html.html#ID-58190037">HTMLElement</a>|String|Array
            * @default null
			* @writeonce
            */
            this.setAttributeConfig("menu", {
        
                value: null,
                method: this._setMenu,
                writeOnce: true
            
            });
        
        
            /**
            * @attribute lazyloadmenu
            * @description Boolean indicating the value to set for the 
            * <a href="YAHOO.widget.Menu.html#lazyLoad">"lazyload"</a>
            * configuration property of the button's menu.  Setting 
            * "lazyloadmenu" to <code>true </code> will defer rendering of 
            * the button's menu until the first time it is made visible.  
            * If "lazyloadmenu" is set to <code>false</code>, the button's 
            * menu will be rendered immediately if the button is in the 
            * document, or in response to the button's "appendTo" event if 
            * the button is not yet in the document.  In either case, the 
            * menu is rendered into the button's parent HTML element.  
            * <em>This attribute does not apply if a 
            * <a href="YAHOO.widget.Menu.html">YAHOO.widget.Menu</a> or 
            * <a href="YAHOO.widget.Overlay.html">YAHOO.widget.Overlay</a> 
            * instance is passed as the value of the button's "menu" 
            * configuration attribute. <a href="YAHOO.widget.Menu.html">
            * YAHOO.widget.Menu</a> or <a href="YAHOO.widget.Overlay.html">
            * YAHOO.widget.Overlay</a> instances should be rendered before 
            * being set as the value for the "menu" configuration 
            * attribute.</em>
            * @default true
            * @type Boolean
			* @writeonce
            */
            this.setAttributeConfig("lazyloadmenu", {
        
                value: (oAttributes.lazyloadmenu === false ? false : true),
                validator: Lang.isBoolean,
                writeOnce: true
        
            });


            /**
            * @attribute menuclassname
            * @description String representing the CSS class name to be 
            * applied to the root element of the button's menu.
            * @type String
            * @default "yui-button-menu"
			* @writeonce
            */
            this.setAttributeConfig("menuclassname", {
        
                value: (oAttributes.menuclassname || (this.CLASS_NAME_PREFIX + "button-menu")),
                validator: Lang.isString,
                method: this._setMenuClassName,
                writeOnce: true
        
            });        


			/**
			* @attribute menuminscrollheight
			* @description Number defining the minimum threshold for the "menumaxheight" 
			* configuration attribute.  When set this attribute is automatically applied 
			* to all submenus.
			* @default 90
			* @type Number
			*/
            this.setAttributeConfig("menuminscrollheight", {
        
                value: (oAttributes.menuminscrollheight || 90),
                validator: Lang.isNumber
        
            });


            /**
            * @attribute menumaxheight
			* @description Number defining the maximum height (in pixels) for a menu's 
			* body element (<code>&#60;div class="bd"&#60;</code>).  Once a menu's body 
			* exceeds this height, the contents of the body are scrolled to maintain 
			* this value.  This value cannot be set lower than the value of the 
			* "minscrollheight" configuration property.
            * @type Number
            * @default 0
            */
            this.setAttributeConfig("menumaxheight", {
        
                value: (oAttributes.menumaxheight || 0),
                validator: Lang.isNumber
        
            });


            /**
            * @attribute menualignment
			* @description Array defining how the Button's Menu is aligned to the Button.  
            * The default value of ["tl", "bl"] aligns the Menu's top left corner to the Button's 
            * bottom left corner.
            * @type Array
            * @default ["tl", "bl"]
            */
            this.setAttributeConfig("menualignment", {
        
                value: (oAttributes.menualignment || ["tl", "bl"]),
                validator: Lang.isArray
        
            });
            

            /**
            * @attribute selectedMenuItem
            * @description Object representing the item in the button's menu 
            * that is currently selected.
            * @type YAHOO.widget.MenuItem
            * @default null
            */
            this.setAttributeConfig("selectedMenuItem", {
        
                value: null
        
            });
        
        
            /**
            * @attribute onclick
            * @description Object literal representing the code to be executed  
            * when the button is clicked.  Format:<br> <code> {<br> 
            * <strong>fn:</strong> Function,   &#47;&#47; The handler to call 
            * when the event fires.<br> <strong>obj:</strong> Object, 
            * &#47;&#47; An object to pass back to the handler.<br> 
            * <strong>scope:</strong> Object &#47;&#47;  The object to use 
            * for the scope of the handler.<br> } </code>
            * @type Object
            * @default null
            */
            this.setAttributeConfig("onclick", {
        
                value: oAttributes.onclick,
                method: this._setOnClick
            
            });


            /**
            * @attribute focusmenu
            * @description Boolean indicating whether or not the button's menu 
            * should be focused when it is made visible.
            * @type Boolean
            * @default true
            */
            this.setAttributeConfig("focusmenu", {
        
                value: (oAttributes.focusmenu === false ? false : true),
                validator: Lang.isBoolean
        
            });


            /**
            * @attribute replaceLabel
            * @description Boolean indicating whether or not the text of the 
			* button's <code>&#60;label&#62;</code> element should be used as
			* the source for the button's label configuration attribute and 
			* removed from the DOM.
            * @type Boolean
            * @default false
            */
            this.setAttributeConfig("replaceLabel", {
        
                value: false,
                validator: Lang.isBoolean,
                writeOnce: true
        
            });

        },
        
        
        /**
        * @method focus
        * @description Causes the button to receive the focus and fires the 
        * button's "focus" event.
        */
        focus: function () {
        
            if (!this.get("disabled")) {
                //Adding a try/catch in case the element is not
                //  visible by the time it's focus is being called.
                //  for example, on a dialog that closes on button click
                try {
                    this._button.focus();
                } catch (e) {}
            
            }
        
        },
        
        
        /**
        * @method blur
        * @description Causes the button to lose focus and fires the button's
        * "blur" event.
        */
        blur: function () {
        
            if (!this.get("disabled")) {
                //Adding a try/catch in case the element is not
                //  visible by the time it's focus is being called.
                //  for example, on a dialog that closes on button click
                try {
                    this._button.blur();
                } catch (e) {}
        
            }
        
        },
        
        
        /**
        * @method hasFocus
        * @description Returns a boolean indicating whether or not the button 
        * has focus.
        * @return {Boolean}
        */
        hasFocus: function () {
        
            return (m_oFocusedButton == this);
        
        },
        
        
        /**
        * @method isActive
        * @description Returns a boolean indicating whether or not the button 
        * is active.
        * @return {Boolean}
        */
        isActive: function () {
        
            return this.hasClass(this.CLASS_NAME_PREFIX + this.CSS_CLASS_NAME + "-active");
        
        },
        
        
        /**
        * @method getMenu
        * @description Returns a reference to the button's menu.
        * @return {<a href="YAHOO.widget.Overlay.html">
        * YAHOO.widget.Overlay</a>|<a 
        * href="YAHOO.widget.Menu.html">YAHOO.widget.Menu</a>}
        */
        getMenu: function () {
        
            return this._menu;
        
        },
        
        
        /**
        * @method getForm
        * @description Returns a reference to the button's parent form.
        * @return {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-
        * 20000929/level-one-html.html#ID-40002357">HTMLFormElement</a>}
        */
        getForm: function () {
        
        	var oButton = this._button,
        		oForm;
        
            if (oButton) {
            
            	oForm = oButton.form;
            
            }
        
        	return oForm;
        
        },
        
        
        /** 
        * @method getHiddenFields
        * @description Returns an <code>&#60;input&#62;</code> element or 
        * array of form elements used to represent the button when its parent 
        * form is submitted.  
        * @return {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-6043025">HTMLInputElement</a>|Array}
        */
        getHiddenFields: function () {
        
            return this._hiddenFields;
        
        },
        
        
        /**
        * @method destroy
        * @description Removes the button's element from its parent element and 
        * removes all event handlers.
        */
        destroy: function () {
        
            YAHOO.log("Destroying ...", "info", this.toString());
        
            var oElement = this.get("element"),
                oMenu = this._menu,
				oLabel = this._label,
                oParentNode,
                aButtons;
        
            if (oMenu) {
        
                YAHOO.log("Destroying menu.", "info", this.toString());

                if (m_oOverlayManager && m_oOverlayManager.find(oMenu)) {

                    m_oOverlayManager.remove(oMenu);

                }
        
                oMenu.destroy();
        
            }
        
            YAHOO.log("Removing DOM event listeners.", "info", this.toString());
        
            Event.purgeElement(oElement);
            Event.purgeElement(this._button);
            Event.removeListener(document, "mouseup", this._onDocumentMouseUp);
            Event.removeListener(document, "keyup", this._onDocumentKeyUp);
            Event.removeListener(document, "mousedown", this._onDocumentMouseDown);


			if (oLabel) {

            	Event.removeListener(oLabel, "click", this._onLabelClick);
				
				oParentNode = oLabel.parentNode;
				oParentNode.removeChild(oLabel);
				
			}
        
        
            var oForm = this.getForm();
            
            if (oForm) {
        
                Event.removeListener(oForm, "reset", this._onFormReset);
                Event.removeListener(oForm, "submit", this._onFormSubmit);
        
            }

            YAHOO.log("Removing CustomEvent listeners.", "info", this.toString());

            this.unsubscribeAll();

			oParentNode = oElement.parentNode;

            if (oParentNode) {

                oParentNode.removeChild(oElement);
            
            }
        
            YAHOO.log("Removing from document.", "info", this.toString());
        
            delete m_oButtons[this.get("id")];

			var sClass = (this.CLASS_NAME_PREFIX + this.CSS_CLASS_NAME);

            aButtons = Dom.getElementsByClassName(sClass, 
                                this.NODE_NAME, oForm); 

            if (Lang.isArray(aButtons) && aButtons.length === 0) {

                Event.removeListener(oForm, "keypress", 
                        YAHOO.widget.Button.onFormKeyPress);

            }

            YAHOO.log("Destroyed.", "info", this.toString());
        
        },
        
        
        fireEvent: function (p_sType , p_aArgs) {
        
			var sType = arguments[0];
		
			//  Disabled buttons should not respond to DOM events
		
			if (this.DOM_EVENTS[sType] && this.get("disabled")) {
		
				return false;
		
			}
		
			return YAHOO.widget.Button.superclass.fireEvent.apply(this, arguments);
        
        },
        
        
        /**
        * @method toString
        * @description Returns a string representing the button.
        * @return {String}
        */
        toString: function () {
        
            return ("Button " + this.get("id"));
        
        }
    
    });
    
    
    /**
    * @method YAHOO.widget.Button.onFormKeyPress
    * @description "keypress" event handler for the button's form.
    * @param {Event} p_oEvent Object representing the DOM event object passed 
    * back by the event utility (YAHOO.util.Event).
    */
    YAHOO.widget.Button.onFormKeyPress = function (p_oEvent) {
    
        var oTarget = Event.getTarget(p_oEvent),
            nCharCode = Event.getCharCode(p_oEvent),
            sNodeName = oTarget.nodeName && oTarget.nodeName.toUpperCase(),
            sType = oTarget.type,
    
            /*
                Boolean indicating if the form contains any enabled or 
                disabled YUI submit buttons
            */
    
            bFormContainsYUIButtons = false,
    
            oButton,
    
            oYUISubmitButton,   // The form's first, enabled YUI submit button
    
            /*
                 The form's first, enabled HTML submit button that precedes any 
                 YUI submit button
            */
    
            oPrecedingSubmitButton,
            
            oEvent; 
    
    
        function isSubmitButton(p_oElement) {
    
            var sId,
                oSrcElement;
    
            switch (p_oElement.nodeName.toUpperCase()) {
    
            case "INPUT":
            case "BUTTON":
            
                if (p_oElement.type == "submit" && !p_oElement.disabled) {
                    
                    if (!bFormContainsYUIButtons && !oPrecedingSubmitButton) {

                        oPrecedingSubmitButton = p_oElement;

                    }
                
                }

                break;
            

            default:
            
                sId = p_oElement.id;
    
                if (sId) {
    
                    oButton = m_oButtons[sId];
        
                    if (oButton) {

                        bFormContainsYUIButtons = true;
        
                        if (!oButton.get("disabled")) {

                            oSrcElement = oButton.get("srcelement");
    
                            if (!oYUISubmitButton && (oButton.get("type") == "submit" || 
                                (oSrcElement && oSrcElement.type == "submit"))) {

                                oYUISubmitButton = oButton;
                            
                            }
                        
                        }
                        
                    }
                
                }

                break;
    
            }
    
        }
    
    
        if (nCharCode == 13 && ((sNodeName == "INPUT" && (sType == "text" || 
            sType == "password" || sType == "checkbox" || sType == "radio" || 
            sType == "file")) || sNodeName == "SELECT")) {
    
            Dom.getElementsBy(isSubmitButton, "*", this);
    
    
            if (oPrecedingSubmitButton) {
    
                /*
                     Need to set focus to the first enabled submit button
                     to make sure that IE includes its name and value 
                     in the form's data set.
                */
    
                oPrecedingSubmitButton.focus();
            
            }
            else if (!oPrecedingSubmitButton && oYUISubmitButton) {
    
				/*
					Need to call "preventDefault" to ensure that the form doesn't end up getting
					submitted twice.
				*/
    
    			Event.preventDefault(p_oEvent);


				if (UA.ie) {

                    if (UA.ie < 9) {
                        oYUISubmitButton.get("element").fireEvent("onclick");
                    } else {
                        oEvent = document.createEvent("HTMLEvents");
                        oEvent.initEvent("click", true, true);
                        oYUISubmitButton.get("element").dispatchEvent(oEvent);
                    }
				
				}
				else {

					oEvent = document.createEvent("HTMLEvents");
					oEvent.initEvent("click", true, true);
			

					if (UA.gecko < 1.9) {
					
						oYUISubmitButton.fireEvent("click", oEvent);
					
					}
					else {

						oYUISubmitButton.get("element").dispatchEvent(oEvent);
					
					}
  
                }

            }
            
        }
    
    };
    
    
    /**
    * @method YAHOO.widget.Button.addHiddenFieldsToForm
    * @description Searches the specified form and adds hidden fields for  
    * instances of YAHOO.widget.Button that are of type "radio," "checkbox," 
    * "menu," and "split."
    * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-40002357">HTMLFormElement</a>} p_oForm Object reference 
    * for the form to search.
    */
    YAHOO.widget.Button.addHiddenFieldsToForm = function (p_oForm) {
    
        var proto = YAHOO.widget.Button.prototype,
			aButtons = Dom.getElementsByClassName(
							(proto.CLASS_NAME_PREFIX + proto.CSS_CLASS_NAME), 
                            "*", 
                            p_oForm),
    
            nButtons = aButtons.length,
            oButton,
            sId,
            i;
    
        if (nButtons > 0) {
    
            YAHOO.log("Form contains " + nButtons + " YUI buttons.", "info", this.toString());
    
            for (i = 0; i < nButtons; i++) {
    
                sId = aButtons[i].id;
    
                if (sId) {
    
                    oButton = m_oButtons[sId];
        
                    if (oButton) {
           
                        oButton.createHiddenFields();
                        
                    }
                
                }
            
            }
    
        }
    
    };
    

    /**
    * @method YAHOO.widget.Button.getButton
    * @description Returns a button with the specified id.
    * @param {String} p_sId String specifying the id of the root node of the 
    * HTML element representing the button to be retrieved.
    * @return {YAHOO.widget.Button}
    */
    YAHOO.widget.Button.getButton = function (p_sId) {

		return m_oButtons[p_sId];

    };
    
    
    // Events
    
    
    /**
    * @event focus
    * @description Fires when the menu item receives focus.  Passes back a  
    * single object representing the original DOM event object passed back by 
    * the event utility (YAHOO.util.Event) when the event was fired.  See 
    * <a href="YAHOO.util.Element.html#addListener">Element.addListener</a> 
    * for more information on listening for this event.
    * @type YAHOO.util.CustomEvent
    */
    
    
    /**
    * @event blur
    * @description Fires when the menu item loses the input focus.  Passes back  
    * a single object representing the original DOM event object passed back by 
    * the event utility (YAHOO.util.Event) when the event was fired.  See 
    * <a href="YAHOO.util.Element.html#addListener">Element.addListener</a> for  
    * more information on listening for this event.
    * @type YAHOO.util.CustomEvent
    */
    
    
    /**
    * @event option
    * @description Fires when the user invokes the button's option.  Passes 
    * back a single object representing the original DOM event (either 
    * "mousedown" or "keydown") that caused the "option" event to fire.  See 
    * <a href="YAHOO.util.Element.html#addListener">Element.addListener</a> 
    * for more information on listening for this event.
    * @type YAHOO.util.CustomEvent
    */

})();
(function () {

    // Shorthard for utilities
    
    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event,
        Lang = YAHOO.lang,
        Button = YAHOO.widget.Button,  
    
        // Private collection of radio buttons
    
        m_oButtons = {};



    /**
    * The ButtonGroup class creates a set of buttons that are mutually 
    * exclusive; checking one button in the set will uncheck all others in the 
    * button group.
    * @param {String} p_oElement String specifying the id attribute of the 
    * <code>&#60;div&#62;</code> element of the button group.
    * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
    * level-one-html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object 
    * specifying the <code>&#60;div&#62;</code> element of the button group.
    * @param {Object} p_oElement Object literal specifying a set of 
    * configuration attributes used to create the button group.
    * @param {Object} p_oAttributes Optional. Object literal specifying a set 
    * of configuration attributes used to create the button group.
    * @namespace YAHOO.widget
    * @class ButtonGroup
    * @constructor
    * @extends YAHOO.util.Element
    */
    YAHOO.widget.ButtonGroup = function (p_oElement, p_oAttributes) {
    
        var fnSuperClass = YAHOO.widget.ButtonGroup.superclass.constructor,
            sNodeName,
            oElement,
            sId;
    
        if (arguments.length == 1 && !Lang.isString(p_oElement) && 
            !p_oElement.nodeName) {
    
            if (!p_oElement.id) {
    
                sId = Dom.generateId();
    
                p_oElement.id = sId;
    
                YAHOO.log("No value specified for the button group's \"id\"" +
                    " attribute. Setting button group id to \"" + sId + "\".",
                    "info");
    
            }
    
            this.logger = new YAHOO.widget.LogWriter("ButtonGroup " + sId);
    
            this.logger.log("No source HTML element.  Building the button " +
                    "group using the set of configuration attributes.");
    
            fnSuperClass.call(this, (this._createGroupElement()), p_oElement);
    
        }
        else if (Lang.isString(p_oElement)) {
    
            oElement = Dom.get(p_oElement);
    
            if (oElement) {
            
                if (oElement.nodeName.toUpperCase() == this.NODE_NAME) {
    
                    this.logger = 
                        new YAHOO.widget.LogWriter("ButtonGroup " + p_oElement);
            
                    fnSuperClass.call(this, oElement, p_oAttributes);
    
                }
    
            }
        
        }
        else {
    
            sNodeName = p_oElement.nodeName.toUpperCase();
    
            if (sNodeName && sNodeName == this.NODE_NAME) {
        
                if (!p_oElement.id) {
        
                    p_oElement.id = Dom.generateId();
        
                    YAHOO.log("No value specified for the button group's" +
                        " \"id\" attribute. Setting button group id " +
                        "to \"" + p_oElement.id + "\".", "warn");
        
                }
        
                this.logger = 
                    new YAHOO.widget.LogWriter("ButtonGroup " + p_oElement.id);
        
                fnSuperClass.call(this, p_oElement, p_oAttributes);
    
            }
    
        }
    
    };
    
    
    YAHOO.extend(YAHOO.widget.ButtonGroup, YAHOO.util.Element, {
    
    
        // Protected properties
        
        
        /** 
        * @property _buttons
        * @description Array of buttons in the button group.
        * @default null
        * @protected
        * @type Array
        */
        _buttons: null,
        
        
        
        // Constants
        
        
        /**
        * @property NODE_NAME
        * @description The name of the tag to be used for the button 
        * group's element. 
        * @default "DIV"
        * @final
        * @type String
        */
        NODE_NAME: "DIV",


        /**
        * @property CLASS_NAME_PREFIX
        * @description Prefix used for all class names applied to a ButtonGroup.
        * @default "yui-"
        * @final
        * @type String
        */
        CLASS_NAME_PREFIX: "yui-",
        
        
        /**
        * @property CSS_CLASS_NAME
        * @description String representing the CSS class(es) to be applied  
        * to the button group's element.
        * @default "buttongroup"
        * @final
        * @type String
        */
        CSS_CLASS_NAME: "buttongroup",
    
    
    
        // Protected methods
        
        
        /**
        * @method _createGroupElement
        * @description Creates the button group's element.
        * @protected
        * @return {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-22445964">HTMLDivElement</a>}
        */
        _createGroupElement: function () {
        
            var oElement = document.createElement(this.NODE_NAME);
        
            return oElement;
        
        },
        
        
        
        // Protected attribute setter methods
        
        
        /**
        * @method _setDisabled
        * @description Sets the value of the button groups's 
        * "disabled" attribute.
        * @protected
        * @param {Boolean} p_bDisabled Boolean indicating the value for
        * the button group's "disabled" attribute.
        */
        _setDisabled: function (p_bDisabled) {
        
            var nButtons = this.getCount(),
                i;
        
            if (nButtons > 0) {
        
                i = nButtons - 1;
                
                do {
        
                    this._buttons[i].set("disabled", p_bDisabled);
                
                }
                while (i--);
        
            }
        
        },
        
        
        
        // Protected event handlers
        
        
        /**
        * @method _onKeyDown
        * @description "keydown" event handler for the button group.
        * @protected
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        _onKeyDown: function (p_oEvent) {
        
            var oTarget = Event.getTarget(p_oEvent),
                nCharCode = Event.getCharCode(p_oEvent),
                sId = oTarget.parentNode.parentNode.id,
                oButton = m_oButtons[sId],
                nIndex = -1;
        
        
            if (nCharCode == 37 || nCharCode == 38) {
        
                nIndex = (oButton.index === 0) ? 
                            (this._buttons.length - 1) : (oButton.index - 1);
            
            }
            else if (nCharCode == 39 || nCharCode == 40) {
        
                nIndex = (oButton.index === (this._buttons.length - 1)) ? 
                            0 : (oButton.index + 1);
        
            }
        
        
            if (nIndex > -1) {
        
                this.check(nIndex);
                this.getButton(nIndex).focus();
            
            }        
        
        },
        
        
        /**
        * @method _onAppendTo
        * @description "appendTo" event handler for the button group.
        * @protected
        * @param {Event} p_oEvent Object representing the event that was fired.
        */
        _onAppendTo: function (p_oEvent) {
        
            var aButtons = this._buttons,
                nButtons = aButtons.length,
                i;
        
            for (i = 0; i < nButtons; i++) {
        
                aButtons[i].appendTo(this.get("element"));
        
            }
        
        },
        
        
        /**
        * @method _onButtonCheckedChange
        * @description "checkedChange" event handler for each button in the 
        * button group.
        * @protected
        * @param {Event} p_oEvent Object representing the event that was fired.
        * @param {<a href="YAHOO.widget.Button.html">YAHOO.widget.Button</a>}  
        * p_oButton Object representing the button that fired the event.
        */
        _onButtonCheckedChange: function (p_oEvent, p_oButton) {
        
            var bChecked = p_oEvent.newValue,
                oCheckedButton = this.get("checkedButton");
        
            if (bChecked && oCheckedButton != p_oButton) {
        
                if (oCheckedButton) {
        
                    oCheckedButton.set("checked", false, true);
        
                }
        
                this.set("checkedButton", p_oButton);
                this.set("value", p_oButton.get("value"));
        
            }
            else if (oCheckedButton && !oCheckedButton.set("checked")) {
        
                oCheckedButton.set("checked", true, true);
        
            }
           
        },
        
        
        
        // Public methods
        
        
        /**
        * @method init
        * @description The ButtonGroup class's initialization method.
        * @param {String} p_oElement String specifying the id attribute of the 
        * <code>&#60;div&#62;</code> element of the button group.
        * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object 
        * specifying the <code>&#60;div&#62;</code> element of the button group.
        * @param {Object} p_oElement Object literal specifying a set of  
        * configuration attributes used to create the button group.
        * @param {Object} p_oAttributes Optional. Object literal specifying a
        * set of configuration attributes used to create the button group.
        */
        init: function (p_oElement, p_oAttributes) {
        
            this._buttons = [];
        
            YAHOO.widget.ButtonGroup.superclass.init.call(this, p_oElement, 
                    p_oAttributes);
        
            this.addClass(this.CLASS_NAME_PREFIX + this.CSS_CLASS_NAME);

        
            var sClass = (YAHOO.widget.Button.prototype.CLASS_NAME_PREFIX + "radio-button"),
				aButtons = this.getElementsByClassName(sClass);

            this.logger.log("Searching for child nodes with the class name " +
                sClass + " to add to the button group.");
        
        
            if (aButtons.length > 0) {
        
                this.logger.log("Found " + aButtons.length + 
                    " child nodes with the class name " + sClass + 
                    "  Attempting to add to button group.");
        
                this.addButtons(aButtons);
        
            }
        
        
            this.logger.log("Searching for child nodes with the type of " +
                " \"radio\" to add to the button group.");
        
            function isRadioButton(p_oElement) {
        
                return (p_oElement.type == "radio");
        
            }
        
            aButtons = 
                Dom.getElementsBy(isRadioButton, "input", this.get("element"));
        
        
            if (aButtons.length > 0) {
        
                this.logger.log("Found " + aButtons.length + " child nodes" +
                    " with the type of \"radio.\"  Attempting to add to" +
                    " button group.");
        
                this.addButtons(aButtons);
        
            }
        
            this.on("keydown", this._onKeyDown);
            this.on("appendTo", this._onAppendTo);
        

            var oContainer = this.get("container");

            if (oContainer) {
        
                if (Lang.isString(oContainer)) {
        
                    Event.onContentReady(oContainer, function () {
        
                        this.appendTo(oContainer);            
                    
                    }, null, this);
        
                }
                else {
        
                    this.appendTo(oContainer);
        
                }
        
            }
        
        
            this.logger.log("Initialization completed.");
        
        },
        
        
        /**
        * @method initAttributes
        * @description Initializes all of the configuration attributes used to  
        * create the button group.
        * @param {Object} p_oAttributes Object literal specifying a set of 
        * configuration attributes used to create the button group.
        */
        initAttributes: function (p_oAttributes) {
        
            var oAttributes = p_oAttributes || {};
        
            YAHOO.widget.ButtonGroup.superclass.initAttributes.call(
                this, oAttributes);
        
        
            /**
            * @attribute name
            * @description String specifying the name for the button group.  
            * This name will be applied to each button in the button group.
            * @default null
            * @type String
            */
            this.setAttributeConfig("name", {
        
                value: oAttributes.name,
                validator: Lang.isString
        
            });
        
        
            /**
            * @attribute disabled
            * @description Boolean indicating if the button group should be 
            * disabled.  Disabling the button group will disable each button 
            * in the button group.  Disabled buttons are dimmed and will not 
            * respond to user input or fire events.
            * @default false
            * @type Boolean
            */
            this.setAttributeConfig("disabled", {
        
                value: (oAttributes.disabled || false),
                validator: Lang.isBoolean,
                method: this._setDisabled
        
            });
        
        
            /**
            * @attribute value
            * @description Object specifying the value for the button group.
            * @default null
            * @type Object
            */
            this.setAttributeConfig("value", {
        
                value: oAttributes.value
        
            });
        
        
            /**
            * @attribute container
            * @description HTML element reference or string specifying the id 
            * attribute of the HTML element that the button group's markup
            * should be rendered into.
            * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
            * level-one-html.html#ID-58190037">HTMLElement</a>|String
            * @default null
			* @writeonce
            */
            this.setAttributeConfig("container", {
        
                value: oAttributes.container,
                writeOnce: true
        
            });
        
        
            /**
            * @attribute checkedButton
            * @description Reference for the button in the button group that 
            * is checked.
            * @type {<a href="YAHOO.widget.Button.html">YAHOO.widget.Button</a>}
            * @default null
            */
            this.setAttributeConfig("checkedButton", {
        
                value: null
        
            });
        
        },
        
        
        /**
        * @method addButton
        * @description Adds the button to the button group.
        * @param {<a href="YAHOO.widget.Button.html">YAHOO.widget.Button</a>}  
        * p_oButton Object reference for the <a href="YAHOO.widget.Button.html">
        * YAHOO.widget.Button</a> instance to be added to the button group.
        * @param {String} p_oButton String specifying the id attribute of the 
        * <code>&#60;input&#62;</code> or <code>&#60;span&#62;</code> element 
        * to be used to create the button to be added to the button group.
        * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-6043025">HTMLInputElement</a>|<a href="
        * http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-html.html#
        * ID-33759296">HTMLElement</a>} p_oButton Object reference for the 
        * <code>&#60;input&#62;</code> or <code>&#60;span&#62;</code> element 
        * to be used to create the button to be added to the button group.
        * @param {Object} p_oButton Object literal specifying a set of 
        * <a href="YAHOO.widget.Button.html">YAHOO.widget.Button</a> 
        * configuration attributes used to configure the button to be added to 
        * the button group.
        * @return {<a href="YAHOO.widget.Button.html">YAHOO.widget.Button</a>} 
        */
        addButton: function (p_oButton) {
        
            var oButton,
                oButtonElement,
                oGroupElement,
                nIndex,
                sButtonName,
                sGroupName;
        
        
            if (p_oButton instanceof Button && 
                p_oButton.get("type") == "radio") {
        
                oButton = p_oButton;
        
            }
            else if (!Lang.isString(p_oButton) && !p_oButton.nodeName) {
        
                p_oButton.type = "radio";
        
                oButton = new Button(p_oButton);

            }
            else {
        
                oButton = new Button(p_oButton, { type: "radio" });
        
            }
        
        
            if (oButton) {
        
                nIndex = this._buttons.length;
                sButtonName = oButton.get("name");
                sGroupName = this.get("name");
        
                oButton.index = nIndex;
        
                this._buttons[nIndex] = oButton;
                m_oButtons[oButton.get("id")] = oButton;
        
        
                if (sButtonName != sGroupName) {
        
                    oButton.set("name", sGroupName);
                
                }
        
        
                if (this.get("disabled")) {
        
                    oButton.set("disabled", true);
        
                }
        
        
                if (oButton.get("checked")) {
        
                    this.set("checkedButton", oButton);
        
                }

                
                oButtonElement = oButton.get("element");
                oGroupElement = this.get("element");
                
                if (oButtonElement.parentNode != oGroupElement) {
                
                    oGroupElement.appendChild(oButtonElement);
                
                }
        
                
                oButton.on("checkedChange", 
                    this._onButtonCheckedChange, oButton, this);
        
                this.logger.log("Button " + oButton.get("id") + " added.");
        
            }

			return oButton;
        
        },
        
        
        /**
        * @method addButtons
        * @description Adds the array of buttons to the button group.
        * @param {Array} p_aButtons Array of <a href="YAHOO.widget.Button.html">
        * YAHOO.widget.Button</a> instances to be added 
        * to the button group.
        * @param {Array} p_aButtons Array of strings specifying the id 
        * attribute of the <code>&#60;input&#62;</code> or <code>&#60;span&#62;
        * </code> elements to be used to create the buttons to be added to the 
        * button group.
        * @param {Array} p_aButtons Array of object references for the 
        * <code>&#60;input&#62;</code> or <code>&#60;span&#62;</code> elements 
        * to be used to create the buttons to be added to the button group.
        * @param {Array} p_aButtons Array of object literals, each containing
        * a set of <a href="YAHOO.widget.Button.html">YAHOO.widget.Button</a>  
        * configuration attributes used to configure each button to be added 
        * to the button group.
        * @return {Array}
        */
        addButtons: function (p_aButtons) {
    
            var nButtons,
                oButton,
                aButtons,
                i;
        
            if (Lang.isArray(p_aButtons)) {
            
                nButtons = p_aButtons.length;
                aButtons = [];
        
                if (nButtons > 0) {
        
                    for (i = 0; i < nButtons; i++) {
        
                        oButton = this.addButton(p_aButtons[i]);
                        
                        if (oButton) {
        
                            aButtons[aButtons.length] = oButton;
        
                        }
                    
                    }
                
                }
        
            }

			return aButtons;
        
        },
        
        
        /**
        * @method removeButton
        * @description Removes the button at the specified index from the 
        * button group.
        * @param {Number} p_nIndex Number specifying the index of the button 
        * to be removed from the button group.
        */
        removeButton: function (p_nIndex) {
        
            var oButton = this.getButton(p_nIndex),
                nButtons,
                i;
            
            if (oButton) {
        
                this.logger.log("Removing button " + oButton.get("id") + ".");
        
                this._buttons.splice(p_nIndex, 1);
                delete m_oButtons[oButton.get("id")];
        
                oButton.removeListener("checkedChange", 
                    this._onButtonCheckedChange);

                oButton.destroy();
        
        
                nButtons = this._buttons.length;
                
                if (nButtons > 0) {
        
                    i = this._buttons.length - 1;
                    
                    do {
        
                        this._buttons[i].index = i;
        
                    }
                    while (i--);
                
                }
        
                this.logger.log("Button " + oButton.get("id") + " removed.");
        
            }
        
        },
        
        
        /**
        * @method getButton
        * @description Returns the button at the specified index.
        * @param {Number} p_nIndex The index of the button to retrieve from the 
        * button group.
        * @return {<a href="YAHOO.widget.Button.html">YAHOO.widget.Button</a>}
        */
        getButton: function (p_nIndex) {
        
            return this._buttons[p_nIndex];
        
        },
        
        
        /**
        * @method getButtons
        * @description Returns an array of the buttons in the button group.
        * @return {Array}
        */
        getButtons: function () {
        
            return this._buttons;
        
        },
        
        
        /**
        * @method getCount
        * @description Returns the number of buttons in the button group.
        * @return {Number}
        */
        getCount: function () {
        
            return this._buttons.length;
        
        },
        
        
        /**
        * @method focus
        * @description Sets focus to the button at the specified index.
        * @param {Number} p_nIndex Number indicating the index of the button 
        * to focus. 
        */
        focus: function (p_nIndex) {
        
            var oButton,
                nButtons,
                i;
        
            if (Lang.isNumber(p_nIndex)) {
        
                oButton = this._buttons[p_nIndex];
                
                if (oButton) {
        
                    oButton.focus();
        
                }
            
            }
            else {
        
                nButtons = this.getCount();
        
                for (i = 0; i < nButtons; i++) {
        
                    oButton = this._buttons[i];
        
                    if (!oButton.get("disabled")) {
        
                        oButton.focus();
                        break;
        
                    }
        
                }
        
            }
        
        },
        
        
        /**
        * @method check
        * @description Checks the button at the specified index.
        * @param {Number} p_nIndex Number indicating the index of the button 
        * to check. 
        */
        check: function (p_nIndex) {
        
            var oButton = this.getButton(p_nIndex);
            
            if (oButton) {
        
                oButton.set("checked", true);
            
            }
        
        },
        
        
        /**
        * @method destroy
        * @description Removes the button group's element from its parent 
        * element and removes all event handlers.
        */
        destroy: function () {
        
            this.logger.log("Destroying...");
        
            var nButtons = this._buttons.length,
                oElement = this.get("element"),
                oParentNode = oElement.parentNode,
                i;
            
            if (nButtons > 0) {
        
                i = this._buttons.length - 1;
        
                do {
        
                    this._buttons[i].destroy();
        
                }
                while (i--);
            
            }
        
            this.logger.log("Removing DOM event handlers.");
        
            Event.purgeElement(oElement);
            
            this.logger.log("Removing from document.");
        
            oParentNode.removeChild(oElement);
        
        },
        
        
        /**
        * @method toString
        * @description Returns a string representing the button group.
        * @return {String}
        */
        toString: function () {
        
            return ("ButtonGroup " + this.get("id"));
        
        }
    
    });

})();
YAHOO.register("button", YAHOO.widget.Button, {version: "2.9.0", build: "2800"});
