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
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.IOException;
import java.io.*;
import java.util.*;

/** 
 *  Executes query/update sets passed as JSON encoded Strings. <br><br>
 *  <b><u>Valid JSON formats</u></b><br><br>
 *  <b>Raw SQL Query/Update</b><br>
 *  <i>{query:"select sysdate from dual"}</i><br><br>
 *  <b>Prepared statement Query/Update</b><br>
 *  <i>{query:"select sysdate from dual where 1 = ?", values:[1]}</i><br><br>
 *  <b>Note:</b> <i>Update statements and query statements cannot be 
 *  mixed in the same set&#46;</i><br><br>
 *  <b>Note:</b> <i>Each update set is treated as a single entity.<br>
 *  This means that if any update fails within a given update set the 
 *  update set<br> will stop executing and return an error message and all 
 *  changes will be<br>rolled back. Query sets do not follow this behavior, 
 *  each query is<br>treated separately. If one query fails the rest will be 
 *  executed.</i>
 */
public class QueryExecutor {
  
  private final static Pattern intPattern     = Pattern.compile("^\\d+");
  private final static Pattern doublePattern  = Pattern.compile("^\\d+\\.\\d+");
  
  private ConnectionPool pool;
  private Connection     conn;
  
  /** Creae a new instance&#46; All statements will be executed against
   *  the passed ConnectionPool object instance&#46;
   */
  public QueryExecutor(ConnectionPool pool) {
    this.pool = pool;
  }
  
  /** Try and grab a connection from the pool. Keep trying until we succeed.
    * If their is some sort of connection problem, the open() method will throw
    * an exception and bail
    */
  private void open(boolean update) throws Exception {
    if(this.pool != null) {
      this.conn = pool.checkOut(update);
      if(this.conn == null) {
        while(this.conn == null) {
          Thread.yield();
          this.conn = pool.checkOut(update);
        }      
      }
    }
  }
  
  /** Return the current connection to the connection pool
    * if possible.
    */
  private void close() {
    if(this.conn != null && this.pool != null) {
      this.pool.checkIn(this.conn);
    } else {
      //Probably want to know if this isn't true!!
    }
  }
  
  /**
   * Execute a set of queries/updates encoded in JSON via <b>reqStr</b> in the 
   * format <br><br><i>[{query:"select x from y",values:[]},{}&#46;&#46;&#46;]
   * </i>&#46;<br><br>The <b>update</b> flag determines whether or not to 
   * treat each statement in the <b>reqStr</b> as an update or a query&#46;
   */
  public String execute(String reqStr, boolean update) throws Exception {
    
    if(DebugLog.enabled) {
      DebugLog.add(this,"Begin execute");
    }
      
    String                           message      = "";    
    ArrayList<HashMap>               resultsList  = new ArrayList<HashMap>();
    LinkedHashMap<String, 
                  PreparedStatement> bpstmts      = null;
    Statement                        bstmt        = null;    
    
    try {
      this.open(update);
            
      if(update) { 
        conn.setAutoCommit(false);
      }
            
      final JSONArray reqs         = new JSONArray(reqStr);
      final boolean   batchUpdates = reqs.length() > 1;      
            
      //Connection MUST be ready to go
      if(this.conn == null) {
        throw new QueryExecutorException("Connection could not be checked out");
      }
            
      final int rLen = reqs.length();
      
      if(rLen <= 0) {
        throw new QueryExecutorException("No queries specified");
      }
            
      for(int r=0; r < rLen; r++) {
        String           rMessage          = "";
        final JSONObject reqObj            = reqs.getJSONObject(r);
        JSONArray        tValues           = reqObj.optJSONArray("values");
        String[]         values            = new String[(tValues != null ? 
                                                         tValues.length() : 0)];
        
        //Convert the JSONArray to a String[]
        for(int v=0; v < values.length; v++) {
          values[v] = tValues.getString(v);
        }
        
        String           query    = reqObj.getString("query");
        final boolean    prepared = values != null;

        //Can't move forward without a query!
        if(query == null || query.equals("")) {
          throw new QueryExecutorException("Query is missing");
        }
        
        //Here's where we need to do either an update or a query
        if(update) {
          if(batchUpdates) {
            // This is NOT a prepared statement and we need to create a
            // batch statement to add all non prepared statements to
            if(!prepared && bstmt == null) {
              bstmt = conn.createStatement();

            // This IS a prepared statement and we need to create a
            // ordered map of prepared statements so we can execute
            // these statements together in order (sortof...)
            } else if(prepared  && bpstmts == null ) {
              bpstmts = new LinkedHashMap<String, PreparedStatement>();
            }
            
            addBatchUpdate(this.conn, prepared, query, values, bstmt, bpstmts);
          } else {
            
            // Single update query / prepared statement to execute
            executeUpdate(this.conn, prepared, query, values);
          }
        } else {
          resultsList.add(executeQuery(this.conn, prepared, query, values));
        }
      }
      
      //Execute Batch Updates
      if(update && batchUpdates) {       
        //Execute any prepared statement batches that have been gathered.
        //If we have an SQL error and exception will be thrown
        if(bpstmts != null && bpstmts.size() > 0) {
          for(PreparedStatement p : bpstmts.values()) {
            if(DebugLog.enabled) {
              DebugLog.add(this,"Executing batch prepared statement");
            }
            
            p.executeBatch();
          }
        }
      
        //Execute all the standard SQL in a batch. 
        //If we have a SQL error an Exception will be thrown
        if(bstmt != null) { 
          if(DebugLog.enabled) {
            DebugLog.add(this,"Executing batch statement");
          }
          
          bstmt.executeBatch();
        }
      }
      
      if(update) { 
        this.conn.commit();
      }
  
    } catch(JSONException je) {
    
      //There was an error parsing the JSON
      final String err = je.toString();
      if(DebugLog.enabled) {
        DebugLog.add(this, err);
      }

      resultsList.add(Util.getError(err));
      
    } catch(Exception e) {
      
      //We couldn't connect to the DB...      
      if(this.conn == null) {
        final String err = e.toString();
        
        if(ErrorLog.enabled) {
          ErrorLog.add(this, err, false);      
        }

        resultsList.add(Util.getError(err));
        
      //There was an error executing the query/update
      } else if(update) {
          final String err = "Rolling Back Update(s): " + e;

          if(DebugLog.enabled) {
            DebugLog.add(this, err);
          }

          if(this.conn != null) {
            this.conn.rollback();
          }

          resultsList.add(Util.getError(err));          
      } else {
        final String err = e.toString();
        
        if(DebugLog.enabled) {
          DebugLog.add(this, err);
        }

        resultsList.add(Util.getError(err));
      }
      
    } finally {
      //Cleanup batch statement (If applicable)
      if(bstmt != null) {
        try {
          if(DebugLog.enabled) {
            DebugLog.add(this,"Closing batch statement");
          }
          bstmt.close();
        } catch(Exception se) {
          String err = "Error closing batch statement - " + se.toString();
          if(ErrorLog.enabled) {
            ErrorLog.add(this, err, false);
          }
          resultsList.add(Util.getError(err));
        }
      }
      
      //Cleanup the batch prepared statement (If applicable)
      if(bpstmts != null) {
        for(PreparedStatement p : bpstmts.values()) {
          try {
            if(p != null) {
              p.close();
              if(DebugLog.enabled) {
                DebugLog.add(this,"Closing batch prepared stmnt");
              }
            }
          } catch(Exception pse) {
            String err = "Error closing batch prepared stmnt - "+pse.toString();
            if(ErrorLog.enabled) {
              ErrorLog.add(this, err, false);          
            }
            resultsList.add(Util.getError(err));
          }
        }
      }        
        
      if(DebugLog.enabled) {
        DebugLog.add(this,"Closing connection");
      }
        
      //Cleanup DB connection (Always applicable)
      this.conn.close();
    }        
    
    if(DebugLog.enabled)  {
      DebugLog.add(this,"End execute");
    }
    
    //UPDATE => [{message:"",status:"success"}]
    if(update && resultsList.size() <= 0) {
      
      HashMap pObj = new HashMap();  
      pObj.put("message", "");
      pObj.put("status",  "success");
      pObj.put("rows",    new ArrayList());
      pObj.put("types",   new ArrayList());
      pObj.put("cols",    new ArrayList());
      resultsList.add(pObj);
      
    }
    
    //Serialize resultsArray into JSON
    return serializeToJson(resultsList);
  }  
 
  /** Add all the values in the String[] to the pstmt PreparedStatment
    * Use some regex action to figure out what data type each value is
    * before setting it
    */
  private void setPreparedStatementValues(PreparedStatement pstmt, 
                                         String[] values) throws Exception {
    final int vLen = values.length;
    for(int v=0; v < vLen; v++) {
      final String  val           = values[v];
      final int     idx           = v+1;
      final Matcher intMatcher    = intPattern.matcher(val);        
      final Matcher doubleMatcher = doublePattern.matcher(val);
      
      if(intMatcher.find()) {
        pstmt.setInt(idx, Integer.parseInt(val));  
      } else if(doubleMatcher.find()) {
        pstmt.setDouble(idx, Double.parseDouble(val));  
      } else {
        pstmt.setString(idx, val);
      }
    }
  }

  /** Add a batch update to either a single statement, the correct
    * passed prepared statement.
    */
  private void addBatchUpdate(Connection conn, boolean prepared, String query, 
                              String[] values, Statement bstmt, 
                              LinkedHashMap<String, PreparedStatement> bpstmts) 
                              throws Exception {
  
    //If this is NOT a prepared statement then add the query to a raw SQL batch
    if(!prepared) {
      if(DebugLog.enabled) {
        DebugLog.add(this,"Adding update '"+query+"' to statement batch");
      }
      bstmt.addBatch(query);            
    } else {
      //If this IS a prepared statement then check for its existence
      //in the pstmts hash. If it doesn't exist then create a new
      //pstmt for the query and add it to the hash.
      PreparedStatement pstmt = null;
      if(bpstmts.containsKey(query)) {
        if(DebugLog.enabled) {
          DebugLog.add(this,"Retrieving pstmt batch for query '"+query+"'");
        }
        pstmt = bpstmts.get(query);
      } else {
        if(DebugLog.enabled) {
          DebugLog.add(this,"Starting pstmt batch for query '"+query+"'");
        }
        
        pstmt = conn.prepareStatement(query);
      }
      
      if(DebugLog.enabled) {
        DebugLog.add(this,"Setting vals on pstmt batch for query '"+query+"'");
      }
      
      setPreparedStatementValues(pstmt, values);
      
      //Add THIS set of values to the batch for this specific 
      //prepared statement. Later on all prepared statment batches
      //will be executed sequentially
      if(DebugLog.enabled) {
        DebugLog.add(this,"Adding to pstmt batch for query '"+query+"'");
      }
      pstmt.addBatch();
      bpstmts.put(query, pstmt);          
    }  
  }
  
  /** Execute a single update. This handles both a raw query and
    * a prepared statement.
    */
  private void executeUpdate(Connection conn, boolean prepared, String query, 
                             String[] values) throws Exception {
    PreparedStatement pstmt = null;
    Statement         stmt  = null;
    
    try {
      if(!prepared) {
        if(DebugLog.enabled) 
          DebugLog.add(this,"This is a single statement update"); 
        stmt = conn.createStatement();
        stmt.executeUpdate(query);
      } else {
        if(DebugLog.enabled) 
          DebugLog.add(this,"This is a single prepared statement update");
        pstmt = conn.prepareStatement(query); 
        setPreparedStatementValues(pstmt, values);
        pstmt.executeUpdate();
      }
    } finally {
      if(stmt != null) {
        stmt.close();
      }    
      if(pstmt != null) {
        pstmt.close();
      }
    }
  }
  
  /** Execute a query i.e. NOT AN UPDATE. This method handles both
    * raw SQL and prepared statements.
    */
  private HashMap executeQuery(Connection conn, boolean prepared, String query, 
                               String[] values) throws Exception {   
       
    HashMap                      qObj      = new HashMap();    
    ArrayList<ArrayList<String>> rowList   = new ArrayList<ArrayList<String>>();
    ArrayList<String>            colList   = new ArrayList<String>();
    ArrayList<String>            typeList  = new ArrayList<String>();
    
    ResultSet         rset      = null;
    PreparedStatement pstmt     = null;
    Statement         stmt      = null;
    String            rMessage  = "";
        
    try {   
      if(prepared) {
        pstmt = conn.prepareStatement(query);
        setPreparedStatementValues(pstmt,values);
        rset  = pstmt.executeQuery();
        if(DebugLog.enabled) {
          DebugLog.add(this,"Prepared statement has been executed");
        }
      } else {
        stmt = conn.createStatement();      
        rset = stmt.executeQuery(query);
        if(DebugLog.enabled) {
          DebugLog.add(this,"Statement has been executed");
        }
      }
      
      final ResultSetMetaData rsetMetaData = rset.getMetaData();
      final int               numCols      = rsetMetaData.getColumnCount();
      boolean                 firstRow     = true;                

      //Loop through all the result ROWs
      while(rset.next()) {
        ArrayList<String> valList  = new ArrayList<String>();
        //JSONArray valArray = new JSONArray();
        //Loop through all the result COLs
        for(int i=1; i <= numCols; i++) {
          if(firstRow) {
            colList.add(rsetMetaData.getColumnName(i));
            typeList.add(rsetMetaData.getColumnTypeName(i));
          }
          valList.add(rset.getString(i));            
        }
        //Add each result row to a list of rows
        rowList.add(valList);
        firstRow = false;
      }         
          
      if(DebugLog.enabled) {
        DebugLog.add(this,"Result set JSON created");
      }
    } catch(Exception e) {
      //If something goes wrong then return the error as the message for the
      //result. Do not return any rows or column headers
      final String err = "Couldn't Execute Query: " + e.toString();
      
      if(DebugLog.enabled) {
        DebugLog.add(this, err);
      }
      
      return Util.getError(err);
    } finally {
      //Cleanup up JDBC stuff
 	  if(rset != null) {
 	    rset.close();
 	    if(DebugLog.enabled) {
 	      DebugLog.add(this,"Closing result set");
 	    }
 	  }
      if(pstmt != null) {
        pstmt.close();
        if(DebugLog.enabled) {
          DebugLog.add(this,"Closing prepared statement");
        }
      }
      
      if(stmt != null) {
        stmt.close();
        if(DebugLog.enabled) {
          DebugLog.add(this,"Closing statement");
        }
      }
    } 
    
    //Final JSON for this query is a JSON object
    //The rows attribute can be in either document or standard format
    qObj.put("types",   typeList);
    qObj.put("cols",    colList);
    qObj.put("rows",    rowList);
    //No message necessary since everything went off without a hitch
    qObj.put("message", ""); 
    //If we get this far then we know things are good
    qObj.put("status",  "success"); 
    
    return qObj;
  }
  
  /** Serialize the  result set to JSON. */
  private String serializeToJson(ArrayList<HashMap> resList) 
                                throws JSONException {
      
    JSONArray resArray = new JSONArray();
      
    final int rlen = resList.size();
    for(int i=0; i < rlen; i++) {
      JSONObject    jObj      = new JSONObject();
      final HashMap tHashMap  = resList.get(i);
      
      if(tHashMap.containsKey("message")) {
        jObj.put("message", (String)tHashMap.get("message"));
      }
      
      if(tHashMap.containsKey("status")) {
        jObj.put("status", (String)tHashMap.get("status"));
      }           
      
      if(tHashMap.containsKey("types")) {
        jObj.put("types", 
                  new JSONArray(((ArrayList)tHashMap.get("types")).toArray()));
      }      
      
      if(tHashMap.containsKey("cols")) {
        jObj.put("cols", 
                 new JSONArray(((ArrayList)tHashMap.get("cols")).toArray()));
      }      
      
      if(tHashMap.containsKey("rows")) {
        
        JSONArray       tJarr = new JSONArray();
        final ArrayList rows  = (ArrayList)tHashMap.get("rows");
        final int       tlen  = rows.size();
        
        for(int v=0; v < tlen; v++) {
          tJarr.put(new JSONArray(((ArrayList)rows.get(v)).toArray()));
        }
        
        jObj.put("rows", tJarr);
      }    
      
      resArray.put(jObj);  
    }
    
    return resArray.toString();
  }
}