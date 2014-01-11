import unittest
from com.dbmojo import JDBCConnectionPool

class JDBCConnectionPoolTestCase(unittest.TestCase):
    
        def setUp(self):
            driver = "org.apache.derby.jdbc.EmbeddedDriver"
            dsn    = "jdbc:derby:/testdb;create=true"
          
            self.maxObjects        = 5
            self.expirationTime    = 1
            self.connectionTimeout = 5
            self.pool              = JDBCConnectionPool(driver, dsn, "", "", self.maxObjects, 
                                                        self.expirationTime, 
                                                        self.connectionTimeout,"")

        def testCheckOut(self):
            #Make sure connection is actually being opened
            conn = self.pool.checkOut(False)
            assert(not conn.isClosed())            
            assert(self.pool.getUnavailableConnectionCount() == 1)
            assert(self.pool.getAvailableConnectionCount() == self.maxObjects-1)            
            for x in range(10):
              conn = self.pool.checkOut(False)
              #Make sure we never go over the total number of allocated connections
              assert(self.pool.getOpenConnectionCount() <= self.maxObjects)
              #If we are requesting a connection and none are available verify that a null is 
              #being returned
              if self.pool.getOpenConnectionCount() > self.maxObjects:
                assert(conn == None)              

        def testCheckIn(self):
            conn = self.pool.checkOut(False)
            #Make sure just one connection has been removed from the pool
            assert(self.pool.getUnavailableConnectionCount() == 1)
            assert(self.pool.getAvailableConnectionCount() == self.maxObjects-1)
            self.pool.checkIn(conn)
            #Make sure once the connection has been returned to the pool that it is 
            #availablef or use.
            assert(self.pool.getUnavailableConnectionCount() == 0)
            assert(self.pool.getAvailableConnectionCount() == self.maxObjects)            