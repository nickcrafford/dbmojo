package com.dbmojo;

/*
Copyright (C) 2010 Nick Crafford <nickcrafford@gmail.com>

This file is part of dbmojo

dbmojo is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

dbmojo is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with dbmojo.  If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.logging.*;
import java.io.IOException;

/** Log debugging statements if enabled. <br><br>
 *  <i>if(DebugLog.enabled) {<br>
 *  &nbsp;&nbsp;DebugLog.add(someObject, "Executing query");<br>
 *  }</i><br>
 */
public class DebugLog {
  private static String  logName = "DebugLog";
  private static Log     log;
  /** Enable the logger */
  public  static boolean enabled;
  
  /** Start the debug logger at the specified path. The logger is
   *  set to append mode.
   */
  public static void start(String path) throws IOException {
    log     = new Log(logName,path,Level.ALL);
    enabled = true;
  }
  
  /** Add a Debug message to the log. The passed object will be
   *  used as a prefix. 
   */
  public static void add(Object obj, String debug) {
    add(obj.getClass().getName() +": " + debug);
  }

  /** Add a Debug message to the log. */  
  public static void add(String debug) {
    log.info(debug);
  }
}