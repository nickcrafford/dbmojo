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

import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.IOException;


/** Store a map of macros representing frequently used queries and updates for
 *  easy retrieval&#46; Usually the MacroCache will be initialized at startup via
 *  the populate(--somePath--) method&#46; The flushCache URI, if passed to the,
 *  server will re-run the populate method&#46; 
 */
public class MacroCache {
    
  private static ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<String, String>();

  /** Cache all files ending in the &#46;macro extension under the provided 
   *  <b>macroPath</b> directory as macros&#46; 
   *  <br><br>For example, given a file named query.macro located within the provided 
   *  <b>macroPath</b>, $query will be cached as macro key while the contents of 
   *  query.macro will be used as the macro itself&#46; <br><br>The cached macros 
   *  can  be substituted for queries in any subsequent request to execute&#46;
   */
  public static void populate(String macroPath) {
    _populate(macroPath,"");
  }
  
  private static void _populate(String iMacroPath, String macroPath) {
    if(macroPath.equals("")) macroPath = iMacroPath;
    try {
      final File macroDir = new File(macroPath);
      if(macroDir != null) {
        final File[] macros = macroDir.listFiles();
        if(macros != null) {            
          for(File macro : macros) {
            if(macro.isDirectory()) {
              _populate(iMacroPath, macro.getPath()+"/");
            } else {              
              final String macroFilename = macro.getName();
              if(macroFilename.lastIndexOf(".macro") < 0) continue;
              final String tQuery = Util.fileToString(macroPath+macroFilename);
              final String tKey   = macroPath.replace(iMacroPath,"").replaceAll("/",".") + macroFilename.replaceAll("\\.macro",""); 
              put("$"+tKey, tQuery);
            }
          }
        }
      }
    } catch(IOException macroEx) {
      if(ErrorLog.enabled) {
        System.out.println(macroEx.toString());
        ErrorLog.add(new MacroCache(),"Warning, not all macros loaded",false); 
      }
    }    
  }

  /** Cache a specific <b>macroKey</b> and <b>macroQuery</b>&#46;<br><br>The macroKey must start
   *  with a $ symbol&#46; If an empty <b>macroQuery</b> is passed it will not
   *  be cached&#46;
   */  
  public static synchronized void put(String macroKey, String macroQuery) {
      
    if(macroKey.indexOf("$") != 0 || macroQuery.equals("")) {
      return;
    }
    
    MacroCache.cache.put(macroKey, macroQuery);
    
    if(DebugLog.enabled) 
      DebugLog.add(new QueryExecutor(null),"Macro '"+macroKey+"' added to cache");
  }
  
  /** Return the query/statement associated with the passed macro name.key */
  public static synchronized String get(String macroKey) {
    return MacroCache.cache.get(macroKey);
  }
  
  /** Return the entire MacroCache */
  public static ConcurrentHashMap<String,String> getAll() {
    return MacroCache.cache;
  }
  
  /** Clear all macros and their query/statements associated with them. */
  public static void clear() {
    MacroCache.cache.clear();
  }
}