const rowSelectionControllers = document.querySelectorAll(".jenkins-table__checkbox")

rowSelectionControllers.forEach(select => {
  const owner = select.closest(".jenkins-table");
  const ownerCheckboxes = owner.querySelectorAll("input[type='checkbox']");
  const moreOptionsButton = owner.querySelector(".jenkins-table__checkbox-options")
  const allButton = owner.querySelector("[data-select='all']")
  const noneButton = owner.querySelector("[data-select='none']")
  const dropdown = owner.querySelector(".jenkins-table__checkbox-dropdown")
  const dropdownButtons = owner.querySelectorAll(".jenkins-table__checkbox-dropdown button")

  const allCheckboxesSelected = () => {
    return ownerCheckboxes.length === [...ownerCheckboxes].filter(e => e.checked).length
  }

  const anyCheckboxesSelected = () => {
    return [...ownerCheckboxes].filter(e => e.checked).length > 0
  }

  ownerCheckboxes.forEach(checkbox => {
    checkbox.addEventListener("change", () => {
      updateIcon();
    })
  })

  select.addEventListener("click", () => {
    const newValue = !allCheckboxesSelected()
    ownerCheckboxes.forEach(e => e.checked = newValue)
    updateIcon()
  })

  allButton?.addEventListener("click", () => {
    ownerCheckboxes.forEach(e => e.checked = true)
  })

  noneButton?.addEventListener("click", () => {
    ownerCheckboxes.forEach(e => e.checked = false)
  })

  function updateIcon() {
    select.classList.remove("jenkins-table__checkbox--all")
    select.classList.remove("jenkins-table__checkbox--indeterminate")

    if (allCheckboxesSelected()) {
      select.classList.add("jenkins-table__checkbox--all")
      return;
    }

    if (anyCheckboxesSelected()) {
      select.classList.add("jenkins-table__checkbox--indeterminate")
    }
  }

  document.addEventListener("click", event => {
    if (dropdown?.contains(event.target) || event.target === moreOptionsButton) {
      return
    }

    dropdown?.classList.remove("jenkins-table__checkbox-dropdown--visible")
  })

  moreOptionsButton?.addEventListener("click", () => {
    dropdown.classList.toggle("jenkins-table__checkbox-dropdown--visible")
  })

  dropdownButtons?.forEach(button => {
    button.addEventListener("click", () => {
      updateIcon()
      dropdown.classList.remove("jenkins-table__checkbox-dropdown--visible")
    })
  })
})
