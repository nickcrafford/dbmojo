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

/** Log all errors if enabled. <br><br>
 *  <i>if(ErrorLog.enabled) {<br>
 *  &nbsp;&nbsp;ErrorLog.add(someObject, "Crap something broke", true/false);<br>
 *  }</i><br>
 */
public class ErrorLog {
  private static String  logName = "ErrorLog";
  private static Log     log;
  /** Enable the logger */
  public  static boolean enabled;
  
  /** Start the error logger at the specified path. The
   *  logger is set to "append" mode.
   */
  public static void start(String path) throws IOException {
    log     = new Log(logName,path,Level.ALL); 
    enabled = true;
  }
  
  /** Add a Error message to the log. Pass the object in question
   *  to user the class name as a prefix. The critical flag will
   *  be used to determine the log level.
   */
  public static void add(Object obj, String msg, boolean critical) {
    add(obj.getClass().getName() + ": " + msg, critical);   
  }
  
  /** Add an Error message to the log. The entire message, prefix and all
   *  must be passed. If debugging is enabled then log to Debug as well.
   *  Always print the Error message to screen.
   */
  public static void add(String msg, boolean critical) {
      
    if(DebugLog.enabled) {
      DebugLog.add(msg);
    }
    
    System.out.println(msg);
      
    if(critical) {
      log.warn(msg);
    } else {
      log.critical(msg);
    }
  }
}