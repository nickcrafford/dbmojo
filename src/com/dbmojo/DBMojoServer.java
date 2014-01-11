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

import java.io.IOException;
import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.lang.ClassLoader;

/** A threaded HTTP 1.0 server implemented on top of NanoHTTPD. <br><br>
 *  The default config file location is <i>config.json</i>
 */

public class DBMojoServer extends NanoHTTPD {
  
  private static String defaultConfigPath = "config.json";
  
  private boolean                                   useGzip;
  private short                                     serverPort;
  private short                                     maxConcurrentRequests;
  private ConcurrentHashMap<String, ConnectionPool> dbPools;
  
  private DBMojoServer(boolean useGzip, short serverPort, short maxConcReq,
                       ConcurrentHashMap<String, ConnectionPool> dbPools) {
    this.useGzip               = useGzip;
    this.serverPort            = serverPort;
    this.dbPools               = dbPools;   
    this.maxConcurrentRequests = maxConcReq; 
  }
  
  private void start() throws IOException {
    super.start(this.serverPort, this.maxConcurrentRequests);  
  }
  
  /** Consume the HTTP request, delegate to the QueryExecutor, and 
   *  return the HTTP response. <br<br>
   *  Each request POST/GET request should contain the following params:<br>
   *  <ul>
   *    <li><strong>alias</strong> - The database to connect to</li>
   *    <li><strong>json</strong> - The json containing the query/update set to 
   *    execute</li>   
   * </ul>
   * Optional params:
   * <ul>
   *    <li><strong>update</strong> - Does the query set contain updates? (Y/N).
   *        If not specified the query set will be executed as queries, 
   *        not updates.</li>
   *    <li><strong>cache</strong> - The time (in seconds) to set the 
   *    'Cache-Control' header to. 
   *        If not specified the header will be set to 'no-cache'</li>
   * </ul>   
   */
  public Response serve(String clientIp, String uri, String method, 
                        Properties header, Properties parms) {
    
    final String  json           = parms.getProperty("json");
    final boolean update         = Util.getBoolean(parms.getProperty("update"));
    final boolean documentFormat = false;
    final int     cache          = update ? 
                                   0 : Util.getInt(parms.getProperty("cache"));
    String        alias          = parms.getProperty("alias");
            
    //Log each access attempt
    if(AccessLog.enabled) {
      AccessLog.add(clientIp, method, useGzip, uri, alias, update, json);
    }
  
    //Catch malformed request errors
    if(json == null || alias == null) {
      final String err = "Malformed request";
      if(DebugLog.enabled) DebugLog.add(this,err);
      return respond(Util.getErrorJson(err).toString(),0);
    }
    
    try {       
      return respond(executeStatement(update,alias,json),cache); 
    } catch(Exception e) {
      final String err = e.toString(); 
      if(DebugLog.enabled) DebugLog.add(this, err);
      return respond(Util.getErrorJson(err).toString(),0);
    }
  }
  
  private String executeStatement(boolean update, String alias, 
                                  String json) throws Exception {
      
    ConnectionPool pool = dbPools.get(alias);
    
    //Catch incorrect aliases
    if(pool == null) {
      throw new DBMojoServerException("Alias '" + alias + "' is missing");
    }
    
    QueryExecutor ex      = new QueryExecutor(pool);
    String        results = ex.execute(json,update);
    
    if(useGzip) {
      results = Util.gzipString(results);
    }
    
    return results;
  }
  
  private Response respond(String text, int cache) {
    NanoHTTPD.Response resp = new NanoHTTPD.Response(HTTP_OK, MIME_PLAINTEXT, 
                                                     text);
    
    if(useGzip) {
      resp.addHeader("Content-Encoding","gzip");
    }
    
    //Cache time is usefull for the web accelerators that might front 
    //DBMojo such as Varnish, Squid, or Oracle WebCache
    if(cache > 0) {
      resp.addHeader("Cache-Control", "public, max-age="+cache);
    } else {
      resp.addHeader("Cache-Control", "no-cache");
    }
    
    resp.addHeader("Content-Length", text.length()+"");
    
    return resp;
  }  
  
  //Parse the provided config file json and return a ready to start 
  //DBMojoServer object
  private static DBMojoServer getMojoServerFromConfig(String[] args) {

    DBMojoServer server = null;
    
    try { 
      String     configFilePath = null;
      String     json           = null;
      JSONObject jObj           = null;
      
      parseJson: {
        //If a command line argument is passed then assume it is the config file.
        //Otherwise use the default location
        if(args.length > 0) {
          configFilePath = args[0];
        } else {
          configFilePath = DBMojoServer.defaultConfigPath;
        }

        try {
          json = Util.fileToString(configFilePath);
        } catch(Exception fileEx) { 
          throw new Exception("the specified config file, '"+configFilePath+
                              "', could not be found and/or read");	      
        }

        if(json == null || json.equals("")) {
          throw new Exception("the specified config file, '"+configFilePath+
                              "', is empty");
        }
      
        try {
          jObj = new JSONObject(json);
        } catch(Exception je) {
          throw new Exception("the specified config file, '"+configFilePath+
                              "', does not contain valid JSON");
        }     
      }
      
      //Load basic config data
      short   serverPort        = (short)jObj.optInt("serverPort");      
      boolean useGzip           = jObj.optBoolean("useGzip"); 
      short   maxConcReq        = (short)jObj.optInt("maxConcurrentRequests");
      String  accessLogPath     = jObj.optString("accessLogPath");
      String  errorLogPath      = jObj.optString("errorLogPath");
      String  debugLogPath      = jObj.optString("debugLogPath");      
      
      checkMaxConcurrentReqeusts: {
        if(maxConcReq <= 0) {
          throw new Exception("please set the max concurrent requests to " +
                              "a resonable number");
        }
      }
      
      checkServerPort: {
        //Make sure serverPort was specified
        if(serverPort <= 0) {
          throw new Exception("the server port was not specified");
        }

        //Make sure serverPort is not in use
        ServerSocket tSocket = null;
        try {
          tSocket = new ServerSocket(serverPort);
        } catch(Exception se) {
          tSocket = null;
          throw new Exception("the server port specified is already in use");
        } finally {
          if(tSocket != null) {
            tSocket.close();
          }
          tSocket = null;
        }      
      }
      
      startLogs: {      
        if(!accessLogPath.equals("")) {
          //Make sure accessLogPath exists
          Util.pathExists(accessLogPath, true);      
          //Start logging
          AccessLog.start(accessLogPath);
        }
      
        if(!errorLogPath.equals("")) {      
          //Make sure errorLogPath exists
          Util.pathExists(errorLogPath, true);
          //Start logging
          ErrorLog.start(errorLogPath);        
        }
      
        if(!debugLogPath.equals("")) {
          //Make sure debugLogPath exists
          Util.pathExists(debugLogPath,true);
          //Start logging
          DebugLog.start(debugLogPath);        
        }
      }
                  
      ConcurrentHashMap<String,ConnectionPool> dbPools = 
        new ConcurrentHashMap<String, ConnectionPool>();
      loadDbAlaises: {        
        ClassLoader     classLoader = ClassLoader.getSystemClassLoader();
        final JSONArray dbAliases   = jObj.getJSONArray("dbAliases");
      
        for(int i=0; i < dbAliases.length(); i++) {
          final JSONObject tObj            = dbAliases.getJSONObject(i);
          final String     tAlias          = tObj.getString("alias");
          final String     tDriver         = tObj.getString("driver");
          final String     tDsn            = tObj.getString("dsn");
          final String     tUsername       = tObj.getString("username");
          final String     tPassword       = tObj.getString("password");
          int              tMaxConnections = tObj.getInt("maxConnections");
          //Seconds
          int              tExpirationTime = tObj.getInt("expirationTime")*1000;
          //Seconds
          int              tConnectTimeout = tObj.getInt("connectTimeout");
        
          //Make sure each alias is named
          if(tAlias.equals("")) {
            throw new Exception("alias #"+i+" is missing a name");
          }
        
          //Attempt to load each JDBC driver to ensure they are on the class path
          try {
            Class aClass = classLoader.loadClass(tDriver);
          } catch(ClassNotFoundException cnf) {
            throw new Exception("JDBC Driver '"+tDriver+
                                "' is not on the class path");
          }        
        
          //Make sure each alias has a JDBC connection string
          if(tDsn.equals("")) {
            throw new Exception("JDBC URL, 'dsn', is missing for alias '"+
                                tAlias+"'");
          }
          
          //Attempt to create a JDBC Connection
          ConnectionPool tPool;
          try {
             tPool = new JDBCConnectionPool(tDriver, tDsn, tUsername, 
                                            tPassword, 1, 1, 1,tAlias);
             tPool.checkOut(false);
          } catch(Exception e) {
            throw new Exception("JDBC Connection cannot be established " +
                                "for database '"+tAlias+"'");            
          } finally {
            tPool = null;
          }
        
          //If the max connections option is not set for this alias 
          //then set it to 25
          if(tMaxConnections <= 0) {
            tMaxConnections = 25;
            System.out.println("DBMojoServer: Warning, 'maxConnections' " +
                               "not set for alias '"+tAlias+"' using 25");
          }

          //If the connection expiration time is not set for this alias then 
          //set it to 30 seconds
          if(tExpirationTime <= 0) {
            tExpirationTime = 30;
            System.out.println("DBMojoServer: Warning, 'expirationTime' not " +
                               "set for alias '"+tAlias+"' using 30 seconds");
          }
        
          //If the connection timeout is not set for this alias then 
          //set it to 10 seconds
          if(tConnectTimeout <= 0) {
            tConnectTimeout = 10;
            System.out.println("DBMojoServer Warning, 'connectTimeout' not " +
                               "set for alias '"+tAlias+"' using 10 seconds");
          }
        
          //Make sure another alias with the same name is not already 
          //defined in the config
          if(dbPools.containsKey(tAlias)) {
            throw new Exception("the alias '"+tAlias+"' is already defined in " +
                                " the provided config file");
          }
        
          //Everything is nicely set! Lets add a connection pool to the 
          //dbPool Hashtable keyed by this alias name
          dbPools.put(tAlias, new JDBCConnectionPool(tDriver, tDsn, tUsername, 
                                                     tPassword, tMaxConnections,
                                                     tExpirationTime, 
                                                     tConnectTimeout,
                                                     tAlias));
        }
      }
            
      loadClusters: {
        final JSONArray tClusters = jObj.optJSONArray("clusters");
        
        if(tClusters != null) {     
          for(int c=0; c < tClusters.length(); c++) {
            final JSONObject tObj      = tClusters.getJSONObject(c);
            final String     tAlias    = tObj.getString("alias");
            final String     tWriteTo  = tObj.getString("writeTo");
          
            if(dbPools.containsKey(tAlias)) {
              throw new Exception("the alias '"+tAlias+"' is already defined.");
            }
          
            if(!dbPools.containsKey(tWriteTo)) {
              throw new Exception("the alias '"+tWriteTo+
                                  "' is not present in the valid dbAliases. "+
                                  "This alias cannot be used for a cluster.");
            }
          
            //Add the dbAlias to the cluster writeTo list
            ConnectionPool writeTo = dbPools.get(tWriteTo);
          
            final JSONArray  tReadFrom = tObj.getJSONArray("readFrom");
            ArrayList<ConnectionPool> readFromList = 
              new ArrayList<ConnectionPool>(); 
            for(int r=0; r < tReadFrom.length(); r++) {
              final String tRead = tReadFrom.getString(r);            
              if(!dbPools.containsKey(tRead)) {
                throw new Exception("the alias '"+tRead+
                                    "' is not present in the valid dbAliases. "+
                                    "This alias cannot be used for a cluster.");
              }
              //Add the dbAlias to the cluster readFrom list
              readFromList.add(dbPools.get(tRead));
            }
          
            dbPools.put(tAlias, new JDBCClusteredConnectionPool(tAlias, 
                                                                writeTo, 
                                                                readFromList));
          }
        }
      }
      
      server = new DBMojoServer(useGzip, serverPort, maxConcReq, dbPools);
      
    } catch(Exception jsonEx) {
      System.out.println("DBMojoServer: Config error, " + jsonEx);      
      System.exit(-1);	      
    }
    
    return server;
  }
  
  /** Read the config file and spawn the DBMojo server instance. */
  public static void main( String[] args ) {

    //Create DBMojoServer object from config file
    //This also starts all the necessary loggers
    DBMojoServer mojo = getMojoServerFromConfig(args);
        
    //Attempt to start the server
    try {      
      mojo.start();
    } catch(Exception e) {
      if(ErrorLog.enabled) {
        ErrorLog.add(mojo, "Service could not be started: " + e, true);
      }
      System.exit(-1);
    }

    //Welcome message
    System.out.println("\nDBMojoServer: Now listening on port: " + 
                       mojo.serverPort + "\nPress 'q + Enter' to exit\n");
  
    //Wait until the user enters: q + Enter to stop the server
    //113 is the ASCII code for q
    try {
      while(true) {
        if(System.in.read() == 113) {
          mojo.stop();
          if(DebugLog.enabled) {
            DebugLog.add(mojo, "Service has exitted normally");
          }
          System.exit(0);
        }
      }
    } catch(Exception se) {
      if(ErrorLog.enabled) {
        ErrorLog.add(mojo, "Service has exited abnormally", true);
      }
    }
  }
}
