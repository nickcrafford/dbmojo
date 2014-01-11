import unittest
from com.dbmojo import JDBCConnectionPool
from com.dbmojo import QueryExecutor
from com.dbmojo import MacroCache
from com.dbmojo import DebugLog
from org.json   import JSONArray

class QueryExecutorTestCase(unittest.TestCase):
    
        def setUp(self):
            """ Create a DB connection pool to an embedded Apache
            Derby instance for testing. Create some macros for testing
            as well. """            
            driver = "org.apache.derby.jdbc.EmbeddedDriver"
            dsn    = "jdbc:derby:/testdb;create=true"
            self.maxObjects        = 10
            self.expirationTime    = 10
            self.connectionTimeout = 10
            self.pool              = JDBCConnectionPool(driver, dsn, "", "", 
                                                        self.maxObjects, 
                                                        self.expirationTime, 
                                                        self.connectionTimeout,"")
            self.executor          = QueryExecutor(self.pool)          
            MacroCache.put("$macro",   "SELECT 3 cold FROM SYSIBM.SYSDUMMY1")
            MacroCache.put("$pmacro",  "SELECT 4 cole FROM SYSIBM.SYSDUMMY1 where 1 = ?")
            MacroCache.put("$umacro",  "insert into qe_test (id,txt) values(7,'700')")
            MacroCache.put("$upmacro", "insert into qe_test (id,txt) values(?,?)")
            
            #Setup dummy table
            reqStr  =  '[{query:"create table qe_test (id int, txt varchar(100))"}]'
            self.executor.execute(reqStr, True, True)                   

        def testEscapeRegex(self):
            """ Make sure some of the characters that throw the Java regex
            package into a tizzy have been escaped, mainly ($,/). However,
            any other problem chars should be added as well. """
            testStr    = "$$$\\"
            escapedStr = QueryExecutor.escapeRegex(testStr)
            assert(escapedStr == "\$\$\$\\\\")
            
        def testConvertQueryToPstmt(self):
            """ Make sure we can convert some raw SQL into a 
            prepared statement """
            query  = "select sysdate from dual where 1 = 1 and x = 1.0 and r = 'test' "
            values = JSONArray()
            nQuery = QueryExecutor.convertQueryToPstmt(query, values)
            assert(nQuery == "select sysdate from dual where ? = ? and x = ? and r = ? ")
            assert(values.toString() == '["1","1","1.0","test"]')
            
        def testConvertDirtyQueryToPstmt(self):
            """ Make sure we can convert some raw SQL into a prepared statement
            even if it contains some regex borking chars. """
            query  = "select dollars from dual where 1 = 1 and x = 1.0 and r = '$500.00' "
            values = JSONArray()
            nQuery = QueryExecutor.convertQueryToPstmt(query, values)
            assert(nQuery == "select dollars from dual where ? = ? and x = ? and r = ? ")
            assert(values.toString() == '["1","1","1.0","\\\$500.00"]')            
            
        def testExecuteQueries(self):
            """ Make sure we can execute one query of each supported type
            as a query set. All the results should come back together. """
            reqStr  =  '[{query:"SELECT 1 cola FROM SYSIBM.SYSDUMMY1"},\
                         {query:"SELECT 2 colb FROM SYSIBM.SYSDUMMY1 where 1 = ?",values:[1]},\
                         {query:"SELECT SCHEMANAME colc FROM SYS.SYSSCHEMAS where SCHEMANAME = \'SYSIBM\' ", convert:true},\
                         {query:"$macro"},\
                         {query:"$pmacro",values:[1]}]'
            #Test w/Document Format
            outJson  = self.executor.execute(reqStr,False,True)
            testJson = ['[',
                        '{"message":"","cols":["COLA"],"status":"success","types":["INTEGER"],"rows":[{"COLA":"1"}]},'     ,
                        '{"message":"","cols":["COLB"],"status":"success","types":["INTEGER"],"rows":[{"COLB":"2"}]},'     ,
                        '{"message":"","cols":["COLC"],"status":"success","types":["VARCHAR"],"rows":[{"COLC":"SYSIBM"}]},',                        
                        '{"message":"","cols":["COLD"],"status":"success","types":["INTEGER"],"rows":[{"COLD":"3"}]},'     ,                           
                        '{"message":"","cols":["COLE"],"status":"success","types":["INTEGER"],"rows":[{"COLE":"4"}]}'      ,                           
                        ']']
            assert(outJson == ''.join(testJson))
    
            #Test w/o Document Format
            outJson2  = self.executor.execute(reqStr,False,False)
            testJson2 = ['[',
                        '{"message":"","cols":["COLA"],"status":"success","types":["INTEGER"],"rows":[["1"]]},'     ,
                        '{"message":"","cols":["COLB"],"status":"success","types":["INTEGER"],"rows":[["2"]]},'     ,
                        '{"message":"","cols":["COLC"],"status":"success","types":["VARCHAR"],"rows":[["SYSIBM"]]},',                        
                        '{"message":"","cols":["COLD"],"status":"success","types":["INTEGER"],"rows":[["3"]]},'     ,                           
                        '{"message":"","cols":["COLE"],"status":"success","types":["INTEGER"],"rows":[["4"]]}'      ,                           
                        ']']
            assert(outJson2 == ''.join(testJson2))    
    
        def testExecuteUpdates(self):
            """ Make sure we can execute one update of each supported type 
            as a set in a single batch."""
            #Setup dummy table
            reqStr  =  '[{query:"drop table qe_test"},\
                         {query:"create table qe_test (id int, txt varchar(100))"}]'
            self.executor.execute(reqStr,True,True)
            #Insert data
            reqStr  =  '[{query:"insert into qe_test (id,txt) values(1,\'100\')"}, \
                         {query:"insert into qe_test (id,txt) values(2,\'200\')"}, \
                         {query:"insert into qe_test (id,txt) values(3,\'300\')"}, \
                         {query:"insert into qe_test (id,txt) values(4,\'400\')"}, \
                         {query:"insert into qe_test (id,txt) values(?,?)",values:[5,\'500\']},\
                         {query:"insert into qe_test (id,txt) values(6,\'600\')",convert:true},\
                         {query:"$umacro"},\
                         {query:"$upmacro",values:[8,\'800\']}]'
            outJson  = self.executor.execute(reqStr,True,True)
            assert(outJson == '[{"message":"","status":"success"}]')
            
            #Retrieve and confirm w/document Format
            outQJson = self.executor.execute('[{query:"select id,txt from qe_test order by id"}]',False,True)
            testJson = ['[{"message":"","cols":["ID","TXT"],"status":"success","types":["INTEGER","VARCHAR"],',
                        '"rows":[',
                        '{"ID":"1","TXT":"100"},',
                        '{"ID":"2","TXT":"200"},',
                        '{"ID":"3","TXT":"300"},',                        
                        '{"ID":"4","TXT":"400"},',                        
                        '{"ID":"5","TXT":"500"},',                        
                        '{"ID":"6","TXT":"600"},',                        
                        '{"ID":"7","TXT":"700"},',                        
                        '{"ID":"8","TXT":"800"}]}]']
            assert(outQJson == ''.join(testJson))
            
            #Retrieve and confirm w/o document Format
            outQJson = self.executor.execute('[{query:"select id,txt from qe_test order by id"}]',False,False)
            testJson = ['[{"message":"","cols":["ID","TXT"],"status":"success","types":["INTEGER","VARCHAR"],',
                        '"rows":[',
                        '["1","100"],',
                        '["2","200"],',
                        '["3","300"],',                        
                        '["4","400"],',                        
                        '["5","500"],',                        
                        '["6","600"],',                        
                        '["7","700"],',                        
                        '["8","800"]]}]']
            assert(outQJson == ''.join(testJson))            
            
        def testSingleUpdate(self):
            """ Make sure running a single Raw SQL update 
            doesn't create a batch. """
            reqStr  =  '[{query:"drop table qe_test"},\
                         {query:"create table qe_test (id int, txt varchar(100))"}]'
            
            createAndDrop = self.executor.execute(reqStr,True,True)      
            assert(createAndDrop == '[{"message":"","status":"success"}]')
            
            outJson  = self.executor.execute('[{query:"insert into qe_test (id,txt) values(1,\'100\')"}]',True,True) 
            assert(outJson == '[{"message":"","status":"success"}]')
            
            outQJson = self.executor.execute('[{query:"select * from qe_test order by id"}]',False,True)
            assert(outQJson == '[{"message":"","cols":["ID","TXT"],"status":"success","types":["INTEGER","VARCHAR"],"rows":[{"ID":"1","TXT":"100"}]}]')
            
        def testSinglePreparedUpdate(self):
            """ Make sure running a single Prepared Statement update
            doesn't create a batch. """
            reqStr  =  '[{query:"drop table qe_test"},\
                         {query:"create table qe_test (id int, txt varchar(100))"}]'
            createAndDrop = self.executor.execute(reqStr,True,True)      
            assert(createAndDrop == '[{"message":"","status":"success"}]')  
            
            outJson  = self.executor.execute('[{query:"insert into qe_test (id,txt) values(?,?)",values:[9,\'900\']}]',True,True)  
            assert(outJson == '[{"message":"","status":"success"}]')
            
            outQJson = self.executor.execute('[{query:"select * from qe_test order by id"}]',False,True)
            assert(outQJson == '[{"message":"","cols":["ID","TXT"],"status":"success","types":["INTEGER","VARCHAR"],"rows":[{"ID":"9","TXT":"900"}]}]')                    
