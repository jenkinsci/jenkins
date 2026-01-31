console.log("I am stopping a run")

const stopButton = document.querySelector('button[data-type="stop"]')
if (stopButton) {
  stopButton.addEventListener('click', event => {
    console.log("Cancel button clicked")
    event.preventDefault()
    const action = button.dataset.href
    fetch(action, {
      method: 'POST',
      headers: crumb.wrap({})
    })
  })
}

