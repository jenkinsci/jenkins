import Jumplists from "@/components/dropdowns/jumplists";
import InpageJumplist from "@/components/dropdowns/inpage-jumplist";
import OverflowButton from "@/components/dropdowns/overflow-button";

function init() {
  Jumplists.init();
  InpageJumplist.init();
  OverflowButton.init();
}

export default { init };
