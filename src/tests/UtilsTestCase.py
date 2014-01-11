import os
import unittest
import zlib
from com.dbmojo import *

class UtilsTestCase(unittest.TestCase):

        def testFileToString(self):
            testFilename = "test_file.txt"
            testContent  = "This is a test"
            file = open(testFilename, 'w')
            file.write(testContent)
            file.close()
            newContent = Util.fileToString(testFilename)
            assert(newContent == testContent)
            os.remove(testFilename)

        def testGetSHA1(self):
            sha1Password = Util.getSHA1("123456")
            assert(sha1Password != '')

        def testGzipString(self):
            gzipString = Util.gzipString("123456")
            assert(gzipString != '')
         