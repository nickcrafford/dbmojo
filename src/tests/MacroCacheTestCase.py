import unittest
from com.dbmojo import MacroCache

class MacroCacheTestCase(unittest.TestCase):
    
    def setUp(self):
        MacroCache.clear()
        
    def testPutGet(self):
        MacroCache.put("$x","123")
        assert(MacroCache.get("$x") == "123")
        
    def testPopulate(self):
        MacroCache.populate("src/tests/macros/")
        assert(MacroCache.get("$a")     == "a")
        assert(MacroCache.get("$b")     == "b")
        assert(MacroCache.get("$c.d")   == "d")
        assert(MacroCache.get("$c.e.a") == "f")
        
    def testClear(self):
        MacroCache.put("$x","x")
        assert(MacroCache.get("$x") == "x")
        MacroCache.clear()
        assert(MacroCache.get("$x") == None)
        
    def testGetAll(self):
        MacroCache.put("$a","a")
        MacroCache.put("$b","b")
        assert(len(MacroCache.getAll()) == 2)
        assert(MacroCache.get("$a") == "a")
        assert(MacroCache.get("$b") == "b")
        assert(MacroCache.get("$c") == None)