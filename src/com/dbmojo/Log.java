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
import java.util.logging.FileHandler;

/**
 * Simple wrapper around stock Java logging
 */
class Log {
  private Logger logger;
  
  /** Create a new logger. Give the logger a name, a path to place the file
   *  and a level to log at. Almost all loggers using this class will set
   *  the log level to FINEST.
   */
  public Log(String loggerName, String logPath, Level level) throws IOException {
    Handler handler = new FileHandler(logPath, true);
    handler.setFormatter(new SimpleFormatter()); //No XML crap
    logger = Logger.getLogger(loggerName);

    //Remove base handlers
    Logger rootLogger = Logger.getLogger("");
    try {
      Handler[] handlers = rootLogger.getHandlers();
      for(int h=0; h < handlers.length; h++) {
        rootLogger.removeHandler(handlers[h]);
      }
    } catch(SecurityException se) {
      System.out.println(se);
    }
    
    logger.addHandler(handler);	
    logger.setLevel(level);
  }
  
  /** Log an INFO message */
  public void info(String msg) {
    write(Level.INFO,msg);
  }

  /** Log an WARNING message */    
  public void warn(String msg) {
    write(Level.WARNING,msg);
  }

  /** Log an CRITICAL message */    
  public void critical(String msg) {
    write(Level.SEVERE,msg);    
  }
    
  private void write(Level level, String msg) {
    if(logger != null) {
      logger.log(level,msg);
    }
  }
}