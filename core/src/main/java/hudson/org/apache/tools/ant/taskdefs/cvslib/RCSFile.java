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

/**
 * Represents a RCS File change.
 *
 * @version $Revision$ $Date$
 */
class RCSFile {
    private String m_name;
    private String m_fullName;
    private String m_revision;
    private String m_previousRevision;
    private boolean m_dead;
    private String m_branch;


    RCSFile(final String name,
            final String fullName,
                  final String revision,
                  final String previousRevision,
                  final String branch,
                  final boolean dead) {
        m_name = name;
        m_fullName = fullName;
        m_revision = revision;
        if (!revision.equals(previousRevision)) {
            m_previousRevision = previousRevision;
        }
        m_branch = branch;
        m_dead = dead;
    }


    String getName() {
        return m_name;
    }

    public String getFullName() {
        return m_fullName;
    }

    String getRevision() {
        return m_revision;
    }

    String getPreviousRevision() {
        return m_previousRevision;
    }

    boolean isDead() {
        return m_dead;
    }

    /**
     * Gets the name of this branch, if available.
     */
    String getBranch() {
        return m_branch;
    }
}

