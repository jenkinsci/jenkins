/*
 * Copyright  2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package hudson.org.apache.tools.ant.taskdefs.cvslib;

import org.apache.tools.ant.BuildException;

/**
 * Represents a CVS user with a userID and a full name.
 *
 * @version $Revision$ $Date$
 */
public class CvsUser {
    /** The user's Id */
    private String m_userID;
    /** The user's full name */
    private String m_displayName;


    /**
     * Set the user's fullname
     *
     * @param displayName the user's full name
     */
    public void setDisplayname(final String displayName) {
        m_displayName = displayName;
    }


    /**
     * Set the user's id
     *
     * @param userID the user's new id value.
     */
    public void setUserid(final String userID) {
        m_userID = userID;
    }


    /**
     * Get the user's id.
     *
     * @return The userID value
     */
    String getUserID() {
        return m_userID;
    }


    /**
     * Get the user's full name
     *
     * @return the user's full name
     */
    String getDisplayname() {
        return m_displayName;
    }


    /**
     * validate that this object is configured.
     *
     * @exception BuildException if the instance has not be correctly
     *            configured.
     */
    void validate() throws BuildException {
        if (null == m_userID) {
            final String message = "Username attribute must be set.";

            throw new BuildException(message);
        }
        if (null == m_displayName) {
            final String message =
                "Displayname attribute must be set for userID " + m_userID;

            throw new BuildException(message);
        }
    }
}

