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
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** A Clustered connection pool. This alow us to set multiple read
 *  targets and a single write target for all activity against
 *  one of these. */
public class JDBCClusteredConnectionPool implements ConnectionPool {
  private String                                    alias, writeToAlias;
  private String[]                                  readFromAliases;
  private int                                       connectionIdx;
  private ConcurrentHashMap<String, ConnectionPool> connPools;
  private ConcurrentHashMap<Connection, String>     checkedOutConns;
  
  /** Initialize a new Clustered Connection Pool */
  public JDBCClusteredConnectionPool(String alias, ConnectionPool writeTo, 
                                     ArrayList<ConnectionPool> readFrom) {
                                         
    this.connPools = new ConcurrentHashMap<String, ConnectionPool>();

    //Add read aliases to the available connection pools
    this.readFromAliases = new String[readFrom.size()];
    for(int i=0; i < readFrom.size(); i++) {
      final ConnectionPool tPool = readFrom.get(i);
      this.readFromAliases[i] = tPool.getAlias();
      this.connPools.put(this.readFromAliases[i], tPool);
      
    }
    
    //Add the write alias to the available connection pools
    this.writeToAlias = writeTo.getAlias();
    this.connPools.put(this.writeToAlias, writeTo);
    
    //Initialize the Map for checked out connections
    this.checkedOutConns = new ConcurrentHashMap<Connection, String>();
  }

  /** Check out a Connection from the cluster. The connections will
   *  be balanced in a Round Robin fashion across all read aliases.
   *  If this is an update checkout then use the update alias. */
  public synchronized Connection checkOut(boolean update) throws Exception { 
    String checkOutAlias;
      
    if(update) {
      checkOutAlias = this.writeToAlias;
    } else {
      checkOutAlias = this.readFromAliases[this.connectionIdx];
      
      //Round Robin Load Balancing
      if(this.connectionIdx >= this.readFromAliases.length-1) {
        this.connectionIdx = 0;
      } else {
        this.connectionIdx++;
      }      
    }
        
    //Grab the connection for the correct read/write alias
    Connection conn;
    
    try {
      conn = this.connPools.get(checkOutAlias).checkOut(update);
    } catch(Exception e) {
      conn = null;
    }
        
    //Failover to the next read alias if the connection is null
    //If this is a write alias then go ahead and return a null connection
    if(conn == null && !update) {
      return this.checkOut(false);
    } 
    
    //Keep track of the connections checkedOut so we can return them
    //to the appropriate connection pools
    this.checkedOutConns.put(conn, checkOutAlias);
    
    checkOutAlias = null;
    
    return conn;
  }
  
  /** Check a Connection back into the correct Connection
   *  pool. */
  public synchronized void checkIn(Connection t) {
    if(t != null) {
      final String tAlias = checkedOutConns.get(t);
      connPools.get(tAlias).checkIn(t);
    } else {
      //Could not check connection back in
    }
  } 
  
  /** Return the alias for the Clustered Connection Pool */
  public String getAlias() {
    return this.alias;
  }
}