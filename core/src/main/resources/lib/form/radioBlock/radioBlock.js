// Prototype object to manage each radio button group securely
var radioBlockSupport = {
  buttons: [], // array of functions to update each radio block

  updateButtons: function () {
    this.buttons.forEach((button) => button());
  },

  // Update one block based on the status of the given radio button
  updateSingleButton: function (radio, blockStart, blockEnd) {
    const show = radio.checked;

    let n = blockStart.getAttribute("hasHelp") === "true" 
            ? blockStart.nextElementSibling 
            : blockStart;

    while ((n = n.nextElementSibling) !== blockEnd) {
      if (show) {
        n.classList.remove("form-container--hidden");
        n.style.position = "";
      } else {
        n.classList.add("form-container--hidden");
        n.style.position = "absolute";
      }
    }
    layoutUpdateCallback();
  },
};

// Function to initialize the behavior securely
function initializeRadioBlockControl(r) {
  r.id = `radio-block-${iota++}`;
  r.nextSibling.setAttribute("for", r.id);

  const form = r.form;
  const radios = form.radios || (form.radios = {});
  const group = radios[r.name] || (radios[r.name] = Object.create(radioBlockSupport));
  group.buttons = group.buttons || [];

  const blockStart = r.closest(".radio-block-start");
  blockStart.setAttribute("ref", r.id);

  // Find the end node securely
  const blockEnd = (() => {
    let e = blockStart;
    let cnt = 1;
    while (cnt > 0) {
      e = e.nextElementSibling;
      if (e.classList.contains("radio-block-start")) cnt++;
      if (e.classList.contains("radio-block-end")) cnt--;
    }
    return e;
  })();

  const updateFunction = () => group.updateSingleButton(r, blockStart, blockEnd);
  group.buttons.push(updateFunction);

  // Apply the initial visibility
  updateFunction();

  // Use event listeners for secure handling, instead of inline events
  r.addEventListener("click", group.updateButtons.bind(group));
  r.addEventListener("change", group.updateButtons.bind(group));
}

// Specifying behavior for each radio button with secure initialization
Behaviour.specify("INPUT.radio-block-control", "radioBlock", -100, initializeRadioBlockControl);
