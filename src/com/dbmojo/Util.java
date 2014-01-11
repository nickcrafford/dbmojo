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

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.Security;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 *  Utility and helper methods. 
 */
public class Util {
  
  /** Read a file into a String */
  public static String fileToString(String file) throws IOException {
    StringBuilder  lines          = new StringBuilder();         
    FileReader     fileReader     = null;     
    BufferedReader bufferedReader = null;
    String         line           = null;
    
    try {    
      fileReader     = new FileReader(file);
      bufferedReader = new BufferedReader(fileReader);
      while ((line = bufferedReader.readLine()) != null) {
        lines.append(line);
      }
    } finally {
      if(bufferedReader != null) {
        bufferedReader.close();
      }
      if(fileReader != null) {
        fileReader.close();
      }
    }
    return lines.toString();
  }
  
  /** GZIP encode a String */
  public static String gzipString(String inStr) throws Exception {
    byte[]                strBytes = inStr.getBytes();
    ByteArrayOutputStream bout     = new ByteArrayOutputStream();
    GZIPOutputStream      gout     = new GZIPOutputStream(bout);
    
    try {
      gout.write(strBytes,0,strBytes.length);
    } finally {
      gout.close();
    }    
    return bout.toString();
  }
  
  /** Take a String as input. If the String is a Y or a y then true else false. 
   *  This is usefull for HTTP request parameters.
   */
  public static boolean getBoolean(String val) {
    return val != null && (val.equalsIgnoreCase("Y")    || 
                           val.equalsIgnoreCase("true") || 
                           val.equalsIgnoreCase("1"));
  }  

  /** Take a String as input. If the String is null or
   *  doesnt not have a valid integer like number
   *  format then return 0. Otherwise return the int value. 
   */  
  public static int getInt(String val) {
    if(val == null) return 0;
    try {
      return Integer.parseInt(val);
    } catch(Exception e) {
      return 0;
    }
  }
  
  /** Check if the passed path exists. If parameter create is true
   *  then create the path is it is non existent.
   */
  public static boolean pathExists(String path, boolean create) {
    File tFile = new File(path);
    if(create) {
      try {
        tFile.createNewFile();
      } catch(Exception e) {
        System.out.println(e);
      }
    }
    return tFile.exists();
  }

  /** Given an error meesage return a JSONObject formatted
  appropriately. */  
  public static HashMap getError(String message) {
    HashMap jObj = new HashMap();
    try {
      jObj.put("status", "error");
      jObj.put("message", message);
      jObj.put("rows",    new ArrayList());
      jObj.put("types",   new ArrayList());
      jObj.put("cols",    new ArrayList());      
    } catch(Exception e) {
      System.out.println(e.toString());
    }
    return jObj;
  }
  
  public static JSONArray getErrorJson(String message) {
    JSONObject jObj = new JSONObject();
    try {
      jObj.put("status", "error");
      jObj.put("message", message);
      jObj.put("rows",    new JSONArray());
      jObj.put("types",   new JSONArray());
      jObj.put("cols",    new JSONArray());
    } catch(Exception e) {
      System.out.println(e.toString());
    }
    
    JSONArray jArr = new JSONArray();
    jArr.put(jObj);
    
    return jArr;      
  }
}