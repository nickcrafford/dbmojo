package com.dbmojo;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
    Copyright © 2001,2005-2010 Jarno Elonen <elonen@iki.fi>
    Redistribution and use in source and binary forms, with or without modification, are permitted 
    provided that the following conditions are met:
    
    * Redistributions of source code must retain the above copyright notice, this list of conditions 
      and the following disclaimer.        
    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
      and the following disclaimer in the documentation and/or other materials provided with the distribution.        
    * The name of the author may not be used to endorse or promote products derived from this software 
      without specific prior written permission.
    
    THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT 
    NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
    IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
    OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
    WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
    OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/**
 * A simple, tiny, nicely embeddable HTTP 1.0 server in Java (Modified for DBMojo). 
 *
 * <p> NanoHTTPD version 1.12
 * Copyright &copy; 2001,2005-2010 Jarno Elonen (elonen@iki.fi, http://iki.fi/elonen/)
 *
 * <p><b>Features + limitations: </b><ul>
 *
 *    <li> Only one Java file </li>
 *    <li> Java 1.1 compatible </li>
 *    <li> Released as open source, Modified BSD licence </li>
 *    <li> No fixed config files, logging, authorization etc. (Implement yourself if you need them.) </li>
 *    <li> Supports parameter parsing of GET and POST methods </li>
 *    <li> Supports both dynamic content and file serving </li>
 *    <li> Never caches anything </li>
 *    <li> Doesn't limit bandwidth, request time</li>
 *    <li> Concurrent connections managed Thread pool</li>
 *    <li> Default code serves files and shows all HTTP parameters and headers</li>
 *    <li> Contains a built-in list of most common mime types </li>
 *    <li> All header names are converted lowercase so they don't vary between browsers/clients </li>
 *
 * </ul>
 *
 * <p><b>Ways to use: </b><ul>
 *
 *    <li> Run as a standalone app, serves files from current directory and shows requests</li>
 *    <li> Subclass serve() and embed to your own program </li>
 *
 * </ul>
 *
 * See the end of the source file for distribution license
 * (Modified BSD licence)
 */

public class NanoHTTPD {

  public class Response {
    public String      status;
    public String      mimeType;
    public InputStream data;
    public Properties  header = new Properties();	

    public Response() {
      this.status = HTTP_OK;
    }

    public Response( String status, String mimeType, InputStream data ) {
      this.status   = status;
      this.mimeType = mimeType;
      this.data     = data;
    }

    public Response( String status, String mimeType, String txt ) {
      this.status   = status;
      this.mimeType = mimeType;
      this.data     = new ByteArrayInputStream( txt.getBytes());
    }
    
    public void addHeader( String name, String value ) {
      header.put(name, value);
    }
  }  
  
	//Get a date formate instance based on local US time
  private static java.text.SimpleDateFormat gmtFrmt;
  static {
    gmtFrmt = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
  }  
  
  //HTTP Status Codes
  public static final String HTTP_OK             = "200 OK";
  public static final String HTTP_REDIRECT       = "301 Moved Permanently";
  public static final String HTTP_FORBIDDEN      = "403 Forbidden";
  public static final String HTTP_NOTFOUND       = "404 Not Found";
  public static final String HTTP_BADREQUEST     = "400 Bad Request";
  public static final String HTTP_INTERNALERROR  = "500 Internal Server Error";
  public static final String HTTP_NOTIMPLEMENTED = "501 Not Implemented";
  
  //Common MIME Types
  public static final String MIME_PLAINTEXT      = "text/plain";
  public static final String MIME_HTML           = "text/html";
  public static final String MIME_JSON           = "text/json";
  public static final String MIME_DEFAULT_BINARY = "application/octet-stream";

  private short           maxConcurrentRequests;
  private ExecutorService execSvc;
  private int             myTcpPort;

  public NanoHTTPD() {}
  
  public void start(int port, short maxConReq) throws IOException {
    maxConcurrentRequests = maxConReq;
    myTcpPort             = port;
    execSvc               = Executors.newFixedThreadPool(maxConcurrentRequests);
     
    final ServerSocket ss = new ServerSocket(myTcpPort);
    
    //Start a Thread that will spawn all the worker threds
    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          while(true) {
            launchThread(ss);
          }
        } catch (IOException ioe) {
          System.out.println(ioe.toString());
        }
      }
    });
    t.setDaemon(true);
    t.start();
  }

  //Shutdown all worker threads and stop the server
  public void stop() {
    this.execSvc.shutdown();
    System.exit(0);
  }
  
  //Launch a worker thread. If the number of concurrent requests is
  //greater than what is allowed wait until an opening is available.
  public synchronized void launchThread(ServerSocket ss) throws IOException {
    execSvc.execute(new HTTPSession(ss.accept()));
  }
  
  /**
   * URL-encodes everything between "/"-characters.
   * Encodes spaces as '%20' instead of '+'.
   */
  private String encodeUri( String uri ) {
    String newUri = "";
    StringTokenizer st = new StringTokenizer( uri, "/ ", true );
    while ( st.hasMoreTokens()) {
      String tok = st.nextToken();
      if ( tok.equals( "/" )) {
        newUri += "/";
      } else if ( tok.equals( " " )) {
        newUri += "%20";
      } else {
        try { 
          newUri += URLEncoder.encode( tok, "UTF-8" ); 
        } catch ( UnsupportedEncodingException uee ) {}
      }
    }
    return newUri;
  }
	
  public Response serve(String clientIp, String uri, String method, Properties header, Properties parms ) {
    return new Response(HTTP_OK,MIME_PLAINTEXT,"No implemented");
  }  
  
  /**
   * Handles one session, i.e. parses the HTTP request
   * and returns the response.
   */
  private class HTTPSession implements Runnable {
    private Socket mySocket;
    
    public HTTPSession(Socket s) {
      mySocket = s;
    }
    
    public void run() {
      
      try { 
        InetAddress clientAddress = mySocket.getInetAddress();
        InputStream is            = mySocket.getInputStream();
        
        if(is == null) return;
        
        BufferedReader in = new BufferedReader( new InputStreamReader( is ));
        
        // Read the request line
        String inLine = in.readLine();
        if (inLine == null) return;
        StringTokenizer st = new StringTokenizer( inLine );
        if ( !st.hasMoreTokens()) {
          sendError( HTTP_BADREQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html" );
        }
        
        String method = st.nextToken();
        if ( !st.hasMoreTokens()) {
          sendError( HTTP_BADREQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html" );
        }
        
        String uri = st.nextToken();
        // Decode parameters from the URI
        Properties parms = new Properties();
        int qmi = uri.indexOf( '?' );
        if ( qmi >= 0 ) {
          decodeParms( uri.substring( qmi+1 ), parms );
          uri = decodePercent( uri.substring( 0, qmi ));
        } else {
          uri = decodePercent(uri);
        }
        
        // If there's another token, it's protocol version,
        // followed by HTTP headers. Ignore version but parse headers.
        // NOTE: this now forces header names uppercase since they are
        // case insensitive and vary by client.
        Properties header = new Properties();
        if ( st.hasMoreTokens()) {
          String line = in.readLine();
          while ( line.trim().length() > 0 ) {
            int p = line.indexOf( ':' );
            header.put( line.substring(0,p).trim().toLowerCase(), line.substring(p+1).trim());
            line = in.readLine();
          }
        }
        
        // If the method is POST, there may be parameters
        // in data section, too, read it:
        if ( method.equalsIgnoreCase( "POST" )) {	  
          long size = 0x7FFFFFFFFFFFFFFFl;
          String contentLength = header.getProperty("content-length");
          if (contentLength != null) {
            try { size = Integer.parseInt(contentLength); }
            catch (NumberFormatException ex) {}
          }
          
          String postLine = "";
          char   buf[]    = new char[512];
          int    read     = in.read(buf);
          while ( read >= 0 && size > 0 && !postLine.endsWith("\r\n") ) {
            size -= read;
            postLine += String.valueOf(buf, 0, read);
            if ( size > 0 ) {
              read = in.read(buf);
            }
          }
          
          postLine = postLine.trim();
          decodeParms( postLine, parms );
        }
        
        // Ok, now do the serve()
        Response r = serve( clientAddress.getHostAddress(), uri, method, header, parms );
        if ( r == null ) {
          sendError( HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Serve() returned a null response." );
        } else {
          sendResponse( r.status, r.mimeType, r.header, r.data );
        }

        in.close();
      } catch ( IOException ioe ) {
        try {
          sendError( HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
        } catch ( Throwable t ) {}
      } catch ( InterruptedException ie ) {
        // Thrown by sendError, ignore and exit the thread.
      }
    }

    /**
     * Decodes the percent encoding scheme. <br/>
     * For example: "an+example%20string" -> "an example string"
     */
    private String decodePercent( String str ) throws InterruptedException {
      try {
        StringBuffer sb = new StringBuffer();
        for( int i=0; i<str.length(); i++ ) {
          char c = str.charAt( i );
          switch ( c ) { 
            case '+':
              sb.append( ' ' );
              break;
            case '%':
              sb.append((char)Integer.parseInt( str.substring(i+1,i+3), 16 ));
              i += 2;
              break;
            default:
              sb.append( c );
              break;
          }
        }
      
        return new String( sb.toString().getBytes());
      } catch( Exception e ) {
        sendError( HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding." );
        return null;
      }
    }

    /**
     * Decodes parameters in percent-encoded URI-format
     * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
     * adds them to given Properties. NOTE: this doesn't support multiple
     * identical keys due to the simplicity of Properties -- if you need multiples,
     * you might want to replace the Properties with a Hastable of Vectors or such.
     */
    private void decodeParms( String parms, Properties p ) throws InterruptedException {
      if ( parms == null ) {
        return;
      }

      StringTokenizer st = new StringTokenizer( parms, "&" );
      while ( st.hasMoreTokens()) {
        String e = st.nextToken();
        int sep = e.indexOf( '=' );
        if ( sep >= 0 ) {
          p.put( decodePercent( e.substring( 0, sep )).trim(), decodePercent( e.substring( sep+1 )));
        }
      }
    }

    /**
     * Returns an error message as a HTTP response and
     * throws InterruptedException to stop furhter request processing.
     */
    private void sendError( String status, String msg ) throws InterruptedException {
      sendResponse( status, MIME_PLAINTEXT, null, new ByteArrayInputStream( msg.getBytes()));
      throw new InterruptedException();
    }

    /**
     * Sends given response to the socket.
     */
    private void sendResponse(String status, String mime, Properties header, InputStream data) {
      try {
        if ( status == null ) {
          throw new Error("sendResponse(): Status can't be null.");
        }
      
        OutputStream out = mySocket.getOutputStream();
        PrintWriter  pw  = new PrintWriter( out );
        pw.print("HTTP/1.0 " + status + " \r\n");

        if ( mime != null ) {
          pw.print("Content-Type: " + mime + "\r\n");
        }

        if ( header == null || header.getProperty( "Date" ) == null ) {
          pw.print( "Date: " + gmtFrmt.format( new Date()) + "\r\n");
        }

        if ( header != null ) {
          Enumeration e = header.keys();
          while ( e.hasMoreElements()) {
            String key = (String)e.nextElement();
            String value = header.getProperty( key );
            pw.print( key + ": " + value + "\r\n");
          }
        }

        pw.print("\r\n");
        pw.flush();

        if ( data != null ) { 
          byte[] buff = new byte[2048];
          while (true) {
            int read = data.read( buff, 0, 2048 );
            if (read <= 0) {
              break;
            }
            out.write( buff, 0, read );
          }
        }
      
        out.flush();
        out.close();
        if ( data != null ) {
          data.close();
        }
      } catch( IOException ioe ) {
        // Couldn't write? No can do.
        try { mySocket.close(); } catch( Throwable t ) {}
      }
    }
  }
}