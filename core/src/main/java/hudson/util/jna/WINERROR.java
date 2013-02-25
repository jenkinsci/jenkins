/*
Copyright (c) 2007 Thomas Boerkel, All Rights Reserved

Disclaimer:
===========
This code is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.
 */
package hudson.util.jna;

/**
 *
 * @author TB
 */
public interface WINERROR {
    int ERROR_SUCCESS = 0;
    int NO_ERROR = 0;
    int ERROR_FILE_NOT_FOUND = 2;
    int ERROR_MORE_DATA = 234;
    int ERROR_NO_MORE_ITEMS = 259;
}
