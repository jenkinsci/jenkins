import { getStyle } from "@/util/dom";

function init() {
  const input = document.getElementById("search-box");
  const sizer = document.getElementById("search-box-sizer");
  const comp = document.getElementById("search-box-completion");
  if (!comp) {
    return;
  }

  window.addEventListener("load", () => {
    // copy font style of box to sizer
    var ds = sizer.style;
    ds.fontFamily = getStyle(input, "fontFamily");
    ds.fontSize = getStyle(input, "fontSize");
    ds.fontStyle = getStyle(input, "fontStyle");
    ds.fontWeight = getStyle(input, "fontWeight");
  });

  const searchURL = comp.getAttribute("data-search-url");

  const debounce = 300;
  const maxResults = 25;
  const highlightClass = "ac-highlight";
  let req,
    suggestions,
    selected = -1,
    visible = false,
    currentValue;

  comp.style.top = input.offsetHeight + 2 + "px";

  input.autocomplete = "off";

  ["input", "keydown", "blur", "focus"].forEach((ev) =>
    input.addEventListener(ev, handleEvent),
  );

  const ul = document.createElement("ul");
  ul.classList.add("jenkins-hidden");
  comp.appendChild(ul);

  async function dataSrc(val) {
    const params = new URLSearchParams({ query: val });
    let url = searchURL + "suggest?" + params;
    const result = await fetch(url);
    const data = await result.json();
    const list = [];
    data.suggestions.forEach((item, i) => {
      if (i < maxResults) {
        list.push(item.name);
      }
    });

    return list;
  }

  function handleEvent(event) {
    if (event.type === "focus") {
      return;
    }

    if (event.type === "keydown" && handleKey(event)) {
      return;
    }

    if (input.value === "") {
      hide();
      currentValue = null;
      return;
    }

    if (event.type === "blur") {
      hide();
      return;
    }

    if (input.value === currentValue && visible) {
      return;
    }

    currentValue = input.value;

    clearTimeout(req);
    req = setTimeout(search, debounce);
  }

  function handleKey(event) {
    if (!visible) {
      if (event.code === "Enter" && !input.value) {
        event.preventDefault();
      }
      return ["Enter", "Escape", "Tab"].includes(event.code);
    }

    switch (event.code) {
      case "ArrowUp":
        return nav(-1, event);
      case "ArrowDown":
        return nav(1, event);
      case "Tab":
        if (selected >= 0) {
          event.preventDefault();
          select(selected);
        }
        hide();
        return true;
      case "Enter":
        if (selected >= 0) {
          event.preventDefault();
          select(selected);
        }
        hide();
        return true;
      case "Escape":
        hide();
        return true;
    }
    return false;
  }

  async function search() {
    if (!input.value) {
      return;
    }
    suggestions = await dataSrc(input.value);
    if (!suggestions.length) {
      hide();
      return;
    }

    showSuggestions();
  }

  function nav(direction, event) {
    event.preventDefault();
    const lastSelected = ul.querySelector(`.${highlightClass}`);
    if (lastSelected) {
      lastSelected.classList.remove(highlightClass);
    }
    selected = (selected + direction + suggestions.length) % suggestions.length;
    ul.querySelector(`:nth-child(${selected + 1})`).classList.add(
      highlightClass,
    );
    return true;
  }

  function select(i) {
    input.value = currentValue = suggestions[i];
  }

  function showSuggestions() {
    ul.innerHTML = "";
    suggestions.forEach((item, i) => {
      const li = document.createElement("li");
      li.innerText = item;
      if (i == selected) {
        li.classList.add(highlightClass);
      }
      li.addEventListener("mousedown", () => select(i));
      li.addEventListener("mouseover", () => {
        ul.querySelectorAll("li").forEach((item) => {
          item.classList.remove(highlightClass);
        });
        li.classList.add(highlightClass);
        selected = i;
      });
      li.addEventListener("mouseout", () => {
        li.classList.remove(highlightClass);
      });
      ul.appendChild(li);
    });
    ul.classList.remove("jenkins-hidden");
    visible = true;
  }

  function hide() {
    suggestions = [];
    selected = -1;
    if (visible) {
      ul.classList.add("jenkins-hidden");
      visible = false;
      updatePos();
    }
  }

  function updatePos() {
    sizer.innerHTML = escapeHTML(input.value);
    var cssWidth,
      offsetWidth = sizer.offsetWidth;
    if (offsetWidth > 0) {
      cssWidth = offsetWidth + "px";
    } else {
      // sizer hidden on small screen, make sure resizing looks OK
      cssWidth = getStyle(sizer, "minWidth");
    }
    input.style.width = comp.style.width = "calc(85px + " + cssWidth + ")";
  }

  input.addEventListener("input", updatePos);
}

export default { init };
