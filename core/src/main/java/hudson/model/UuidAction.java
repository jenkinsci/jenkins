/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Seiji Sogabe, Tom Huybrechts
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.Util;

import java.util.List;
import java.util.UUID;

/**
 * Records a UUID to designate a specific build.
 *
 */
@ExportedBean
public class UuidAction implements Action {
    
    private UUID uuid;
    
    public UuidAction(UUID uuid) {
        this.uuid = uuid;
    }
    
    public static UuidAction generate() {
        UUID newUuid = UUID.randomUUID();
        return new UuidAction(newUuid);
    }
    
    public static UUID getUuidInActions(List<Action> actions) {
        List<UuidAction> lAction = Util.filter(actions, UuidAction.class);
        
        if(lAction.isEmpty()) {
            return null;
        }
        
        return lAction.get(0).getUuid();
    }

    public String getDisplayName() {
        return "UUID";
    }

    public String getIconFileName() {
        return "document-properties.png";
    }

    public String getUrlName() {
        return "uuid";
    }
    
    public UUID getUuid() {
        return uuid;
    }
    
    @Exported(name="uuid")
    public String getUuidStr() {
        return uuid.toString();
    }
}
