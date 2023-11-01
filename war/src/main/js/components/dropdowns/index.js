import Jumplists from "@/components/dropdowns/jumplists";
import InpageJumplist from "@/components/dropdowns/inpage-jumplist";
import OverflowButton from "@/components/dropdowns/overflow-button";
import HeteroLists from "@/components/dropdowns/hetero-list";

function init() {
  Jumplists.init();
  InpageJumplist.init();
  OverflowButton.init();
  HeteroLists.init();
}

export default { init };
