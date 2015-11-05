package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

/**
 * Adds box rendered in the computer side panel.
 *
 * Add box.jelly to display box
 *
 * @author Lucie Votypkova
 * @since 1.434
 * @see hudson.model.Computer#getComputerPanelBoxs()
 */

public abstract class ComputerPanelBox implements ExtensionPoint{
    
    private Computer computer;
    
    
    public void setComputer(Computer computer){
        this.computer = computer;
    }
    
    public Computer getComputer(){
        return computer;
    }
    
    /**
     * Create boxes for the given computer in its page.
     *
     * @param computer
     *      The computer for which displays the boxes. Never null.
     * @return
     *      List of all the registered {@link ComputerPanelBox}s.
     */
    public static List<ComputerPanelBox> all(Computer computer) {
        List<ComputerPanelBox> boxs = new ArrayList<ComputerPanelBox>();
        for(ComputerPanelBox box:  ExtensionList.lookup(ComputerPanelBox.class)){
            box.setComputer(computer);
            boxs.add(box);
        }
        return boxs;
    }


}
