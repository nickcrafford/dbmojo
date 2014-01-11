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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Enumeration;

/**
 *  A generic JDBC connection pool. These pools are normally created 
 *  via the aliases defined in the provided config file&#46;
 */
public class JDBCConnectionPool implements ConnectionPool {
  private int                                 expirationTime, maxObjects;
  private ConcurrentHashMap<Connection, Long> locked, unlocked;
  private String                              driver, dsn, usr, pwd, alias;

  /**
  * Example pool vars:<br><br>
  * <b>driver</b>         = oracle.jdbc.driver.OracleDriver<br>
  * <b>dsn</b>            = jdbc:oracle:thin:@localhost:1521:xe<br>
  * <b>usr</b>            = 'username'<br>
  * <b>pwd</b>            = 'password'<br>
  * <b>maxObjects</b>     = 50 (Max of 50 connections)<br>
  * <b>expirationTime</b> = 300 (5 min before a connection is refreshed)<br>
  * <b>connectTimeOut</b> = 10 (Wait 10 seconds trying to connect to the DB)<br>
  * <b>alias</b>          = 'oracle'
  */
  public JDBCConnectionPool(String driver, String dsn, String usr, 
                            String pwd, int maxObjects, int expirationTime,
                            int connectTimeOut, String alias) {

    this.locked         = new ConcurrentHashMap<Connection, Long>();
    this.unlocked       = new ConcurrentHashMap<Connection, Long>();
    this.maxObjects     = maxObjects;
    this.expirationTime = expirationTime;
    this.driver         = driver;
    this.dsn            = dsn;
    this.usr            = usr;
    this.pwd            = pwd;
    this.alias          = alias;
    
    try {
      Class.forName(this.driver).newInstance();
    } catch (Exception e) {
      if(ErrorLog.enabled)  {
        ErrorLog.add(this,"Cannot create pool - " + e,false);
      }
    }

    DriverManager.setLoginTimeout(connectTimeOut);
    
    if(DebugLog.enabled) {
      DebugLog.add(this,"Connection pool initialized for '"+this.alias+"'");
    }    
  }

  /** Create a new SQL Connection */
  public Connection create() throws Exception {
    try {
      return (DriverManager.getConnection(this.dsn,
                                          this.usr,
                                          this.pwd));
    } catch (SQLException e) {
      String err = "Cannot create connection - " + e;
      if(ErrorLog.enabled) ErrorLog.add(this,err,false);
      throw new Exception(err);      
    } 
  }

  /** Expire the Connection by closing it */
  public void expire(Connection o) {
    try {
      ((Connection) o).close();
    } catch (SQLException e) {
      if(ErrorLog.enabled) {
        ErrorLog.add(this,"Cannot expire connection - " + e, false); 
      }
    }
  }

  /** Validate that a Connection is still ready to br used */
  public boolean validate(Connection o) {
    try {
      return (!((Connection) o).isClosed());
    } catch (SQLException e) {
      if(ErrorLog.enabled) {
        ErrorLog.add(this,"Cannot validate connection - " + e,false); 
      }
      return false;
    }
  }
  
  /**
   * Attempt to retrieve a connection from the pool. If a connection 
   * is not available then return a null.
   */
  public synchronized Connection checkOut(boolean _update) throws Exception {
    //Don't let too many objects be created.
    //Object that uses this pool is responsible for
    //blocking until a !null object is available.
    if(locked.size() >= maxObjects) {
      if(DebugLog.enabled) {
        DebugLog.add(this,"Max connections ("+locked.size()+
                          ") has been exceeded for alias '"+this.alias+"'");
      }
      return null;
    }
    
    long now = System.currentTimeMillis();
    Connection t;
    
    if (unlocked.size() > 0) {
      Enumeration<Connection> e = unlocked.keys();
      while (e.hasMoreElements()) {
        t = e.nextElement();
        if ((now - unlocked.get(t)) > expirationTime) {
          // object has expired       
          unlocked.remove(t);
          expire(t);
          t = null;
          if(DebugLog.enabled) {
            DebugLog.add(this,"A connection has expired for alias '"+
                              this.alias+"'");
          }          
        } else {
          if (validate(t)) {
            //checkout
            unlocked.remove(t);
            locked.put(t, now);
            if(DebugLog.enabled) {
              DebugLog.add(this,"A connection has been checked out for alias '"+
                                this.alias+"'");
            }            
            return t;
          } else {
            // object failed validation
            unlocked.remove(t);
            expire(t);
            t = null;
            if(DebugLog.enabled) {
              DebugLog.add(this,
                           "A connection has failed validation for alias '"+
                           this.alias+"'");
            }            
          }
        }
      }
    }
    
    //No connections available. Create a new one. If this fails it will throw an
    //exception which should be allowed to bubble up
    t = create();
    locked.put(t, now);

    if(DebugLog.enabled) {
      DebugLog.add(this,"A new connection has been created for alias '"+
                        this.alias+"'");
    }    
    
    return t;
  }
  
  /**
   *  Attempt to return a connection to the pool.
   */
  public synchronized void checkIn(Connection t) {
    if(t != null) {
      locked.remove(t);
      unlocked.put(t, System.currentTimeMillis());
      if(DebugLog.enabled) {
        DebugLog.add(this,"A connection has been checked in for alias '"+
                          this.alias+"'");
      }    
    } else {
      if(DebugLog.enabled) {
        DebugLog.add(this,"A null connection has been checked in for alias '"+
                          this.alias+"'");
      }      
    }
  }  
  
  /** Retrieve the total number of connections in pool. */
  public int getOpenConnectionCount() {
    return locked.size() + unlocked.size(); 
  }
  
  /** Retrieve the total number of connections idle in the pool. */
  public int getAvailableConnectionCount() {
    return maxObjects - locked.size();
  }
  
  /** Retrieve the total number of connections in use. */
  public int getUnavailableConnectionCount() {
    return locked.size();
  }   
  
  /** Get the alias this Connection pool is running against */
  public String getAlias() {
    return this.alias;
  }
}
