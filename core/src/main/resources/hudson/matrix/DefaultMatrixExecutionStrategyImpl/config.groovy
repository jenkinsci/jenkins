package hudson.matrix.DefaultMatrixExecutionStrategyImpl;

import hudson.matrix.MatrixConfigurationSorterDescriptor
import hudson.model.Result;

def f = namespace(lib.FormTagLib)

f.optionalBlock (field:"runSequentially", title:_("Run each configuration sequentially"), inline:true) {
    if (MatrixConfigurationSorterDescriptor.all().size()>1) {
        f.dropdownDescriptorSelector(title:_("Execution order of builds"), field:"sorter")
    }
}

f.optionalBlock (field:"hasTouchStoneCombinationFilter", title:_("Execute touchstone builds first"),
        inline:true, checked:my.touchStoneCombinationFilter!=null) {
    // TODO: help="/help/matrix/touchstone.html">
    // TODO: move l10n from MatrixProject/configEntries.jelly

    f.entry(title:_("Filter"), field:"touchStoneCombinationFilter") {
        f.textbox()
    }

    f.entry(title:_("Required result"), field:"touchStoneResultCondition", description:_("required.result.description")) {
        select(name:"touchStoneResultCondition") {
            f.option(value:"SUCCESS",  selected:my.touchStoneResultCondition==Result.SUCCESS,  _("Stable"))
            f.option(value:"UNSTABLE", selected:my.touchStoneResultCondition==Result.UNSTABLE, _("Unstable"))
        }
    }
}
