import Notifications from "@/components/notifications";
import SearchBar from "@/components/search-bar";
import Tooltips from "@/components/tooltips";

Notifications.init();
SearchBar.init();
Tooltips.init();

const currentLanguage = document.querySelector("html").getAttribute("lang");
const formatter = new Intl.RelativeTimeFormat(currentLanguage, {
  style: "narrow"
})

const DIVISIONS = [
  { amount: 60, name: 'seconds' },
  { amount: 60, name: 'minutes' },
  { amount: 24, name: 'hours' },
  { amount: 7, name: 'days' },
  { amount: 4.34524, name: 'weeks' },
  { amount: 12, name: 'months' },
  { amount: Number.POSITIVE_INFINITY, name: 'years' }
]

function formatTimeAgo(date) {
  let duration = (date - new Date()) / 1000

  for (let i = 0; i <= DIVISIONS.length; i++) {
    const division = DIVISIONS[i]
    if (Math.abs(duration) < division.amount) {
      return formatter.format(Math.round(duration), division.name)
    }
    duration /= division.amount
  }
}

document.querySelectorAll("[data-update-timestamp]").forEach(element => {
  function updateFunction() {
    console.log("Updating")
    element.textContent = formatTimeAgo(Date.parse(element.dataset.updateTimestamp))

    setTimeout(() => {
      updateFunction()
    }, 1000);
  }

  updateFunction();
})
