const rowSelectionControllers = document.querySelectorAll(".jenkins-table__checkbox")

rowSelectionControllers.forEach(headerCheckbox => {
  const table = headerCheckbox.closest(".jenkins-table")
  const tableCheckboxes = table.querySelectorAll("input[type='checkbox']")
  const moreOptionsButton = table.querySelector(".jenkins-table__checkbox-options")
  const moreOptionsDropdown = table.querySelector(".jenkins-table__checkbox-dropdown")
  const moreOptionsAllButton = table.querySelector("[data-select='all']")
  const moreOptionsNoneButton = table.querySelector("[data-select='none']")

  if (tableCheckboxes.length === 0) {
    headerCheckbox.disabled = true;
    moreOptionsButton.disabled = true;
  }

  const allCheckboxesSelected = () => {
    return tableCheckboxes.length === [...tableCheckboxes].filter(e => e.checked).length
  }

  const anyCheckboxesSelected = () => {
    return [...tableCheckboxes].filter(e => e.checked).length > 0
  }

  tableCheckboxes.forEach(checkbox => {
    checkbox.addEventListener("change", () => {
      updateIcon()
    })
  })

  headerCheckbox.addEventListener("click", () => {
    const newValue = !allCheckboxesSelected()
    tableCheckboxes.forEach(e => e.checked = newValue)
    updateIcon()
  })

  moreOptionsAllButton?.addEventListener("click", () => {
    tableCheckboxes.forEach(e => e.checked = true)
    updateIcon()
  })

  moreOptionsNoneButton?.addEventListener("click", () => {
    tableCheckboxes.forEach(e => e.checked = false)
    updateIcon()
  })

  function updateIcon() {
    headerCheckbox.classList.remove("jenkins-table__checkbox--all")
    headerCheckbox.classList.remove("jenkins-table__checkbox--indeterminate")
    moreOptionsDropdown.classList.remove("jenkins-table__checkbox-dropdown--visible")

    if (allCheckboxesSelected()) {
      headerCheckbox.classList.add("jenkins-table__checkbox--all")
      return
    }

    if (anyCheckboxesSelected()) {
      headerCheckbox.classList.add("jenkins-table__checkbox--indeterminate")
    }
  }

  document.addEventListener("click", event => {
    if (moreOptionsDropdown?.contains(event.target) || event.target === moreOptionsButton) {
      return
    }

    moreOptionsDropdown?.classList.remove("jenkins-table__checkbox-dropdown--visible")
  })

  moreOptionsButton?.addEventListener("click", () => {
    moreOptionsDropdown.classList.toggle("jenkins-table__checkbox-dropdown--visible")
  })

  window.updateTableHeaderCheckbox = updateIcon
})
