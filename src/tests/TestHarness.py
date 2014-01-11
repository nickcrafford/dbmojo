import unittest

from UtilsTestCase              import *
from QueryExecutorTestCase      import *
from JDBCConnectionPoolTestCase import *
from MacroCacheTestCase         import *

testSuite = unittest.TestSuite()

#Util Tests
testSuite.addTest(UtilsTestCase("testFileToString"))
testSuite.addTest(UtilsTestCase("testGetSHA1"))
testSuite.addTest(UtilsTestCase("testGzipString"))

#JDBCConnectionPool Tests
testSuite.addTest(JDBCConnectionPoolTestCase("testCheckOut"))
testSuite.addTest(JDBCConnectionPoolTestCase("testCheckIn"))


#QueryExecutor Tests
testSuite.addTest(QueryExecutorTestCase("testEscapeRegex"))
testSuite.addTest(QueryExecutorTestCase("testConvertQueryToPstmt"))
testSuite.addTest(QueryExecutorTestCase("testConvertDirtyQueryToPstmt"))
testSuite.addTest(QueryExecutorTestCase("testExecuteQueries"))
testSuite.addTest(QueryExecutorTestCase("testExecuteUpdates"))
testSuite.addTest(QueryExecutorTestCase("testSingleUpdate"))
testSuite.addTest(QueryExecutorTestCase("testSinglePreparedUpdate"))

#MacroCache Tests
testSuite.addTest(MacroCacheTestCase("testPutGet"))
testSuite.addTest(MacroCacheTestCase("testPopulate"))
testSuite.addTest(MacroCacheTestCase("testClear"))
testSuite.addTest(MacroCacheTestCase("testGetAll"))

runner = unittest.TextTestRunner()
runner.run(testSuite)