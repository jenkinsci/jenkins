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

import static com.sun.jna.Library.*;
import com.sun.jna.win32.W32APITypeMapper;
import com.sun.jna.win32.W32APIFunctionMapper;

import java.util.Map;
import java.util.HashMap;

/**
 *
 * @author TB
 */
public interface Options {
  Map<String, Object> UNICODE_OPTIONS = new HashMap<String, Object>() {
    {
      put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
      put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
    }
  };
}