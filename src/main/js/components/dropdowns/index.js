import Jumplists from "@/components/dropdowns/jumplists";
import InpageJumplist from "@/components/dropdowns/inpage-jumplist";
import OverflowButton from "@/components/dropdowns/overflow-button";
import HeteroLists from "@/components/dropdowns/hetero-list";
import ComboBox from "@/components/dropdowns/combo-box";
import Autocomplete from "@/components/dropdowns/autocomplete";

function init() {
  Jumplists.init();
  InpageJumplist.init();
  OverflowButton.init();
  HeteroLists.init();
  ComboBox.init();
  Autocomplete.init();
}

export default { init };
