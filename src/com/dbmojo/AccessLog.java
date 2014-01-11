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

/** Log all access attempts if enabled. <br><br>
 *  <i>if(AccessLog.enabled) {<br>
 *  &nbsp;&nbsp;AccessLog.add(ipAddress, method, gzip, uri, alias, 
 *  update, json);<br>
 *  }</i><br>
 */
public class AccessLog {
  private static String  logName = "AccessLog";
  private static Log     log;
  /** Enable the logger */
  public  static boolean enabled;
  
  /** Start the access logger at the specified path.
   *  The logger is set to "append" mode.
   */
  public static void start(String path) throws IOException {
    log     = new Log(logName,path,Level.ALL); 
    enabled = true;
  }
  
  /** Add an access attempt to the access log. The DBMojoServer method
   *  serve should have access to all the necessary data. Add the
   *  access data to the debug log if it is enabled.
   */
  public static void add(String ipAddress, String method, 
                         boolean gzip, String uri, String alias, 
                         boolean update, String json) {
    String accessMsg = ipAddress + "\t" + method + "\t" + gzip   + "\t" +
                       uri       + "\t" + alias  + "\t" + update + "\t" + json;
    
    log.info(accessMsg);             
             
    if(DebugLog.enabled) {
      DebugLog.add(accessMsg);
    }             
  }
}