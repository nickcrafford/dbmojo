dbmojo
======

Stateless JDBC database proxy over HTTP

## Provides:
* Stateless HTTP/JSON based access to your database(s)
* Access any database that has a JDBC driver
* Connection pooling
* Auto batching of updates
* Supports raw SQL and prepared statements w/bind variables
* Result sets are cacheable via HTTP reverse proxy
* Has been benchmarked to handle > 5K requests per second

## Requirements:
* Java 1.5 or higher
* JDBC jars necessary to connect to your database(s) in your classpath.

## Configuration:
* Create a config file (config.json)

```
{serverPort:            9091,
 maxConcurrentRequests: 50,
 useGzip:               false,
 dbAliases:             [{alias:          "mysql",
                          maxConnections: 30,
                          expirationTime: 300,
                          connectTimeout: 5,
                          driver:         "com.mysql.jdbc.Driver",
                          dsn:            "jdbc:mysql://localhost:3306/",
                          username:       "root",
                          password:       "root"}]}
```

## Starting the server:
* Make sure DBMojo and your JDBC drivers are on your classpath
* java -cp .:dbmojo.jar com.dbmojo.DBMojoServer config.json

#### Execute Query Set:
* /?alias=mysql&json=[{query:"select sysdate() right_now from dual"},{query:"select subdate(sysdate(),1) yesterday from dual"}]

```
[
  {
    "message":"",
    "status":"success",
    "cols":["right_now"],
    "types":["DATETIME"],
    "rows":[["2010-08-08 00:46:38.0"]]
  },
  {
    "message":"",
    "status":"success",
    "cols":["yesterday"],
    "types":["DATETIME"],
    "rows":[["2010-08-07 00:46:38.0"]]
  }
]
```

#### Execute Update Set:
* /?alias=mysql&update=Y&json=[{query:"create table test.test_tbl (id int, blurb text)"},{query:"insert into test.test_tbl values(1,'Hello World!')"}]

```
[
  {
    "message":"",
    "status":"success"
    "cols":[],
    "types":[],
    "rows":[]
  }
]
```

#### Execute Prepared Statement Query:
* /?alias=mysql&json=[{query:"select subdate(sysdate(),1) yesterday from dual where 1 = ? and 2 = ?", values:[1,2]}]

```
[
  {
    "message":"",
    "status":"success",
    "cols":["yesterday"],
    "types":["DATETIME"],
    "rows":[["2010-08-07 00:50:18.0"]]
  }
]
```

#### Execute Prepared Statement Update:
* /?alias=mysql&update=Y&json=[{query:"insert into test.test_tbl (id,blurb) values(?,?)", values:[1,'xyz']}]

```
[
  {
    "message":"",
    "status":"success",
    "cols":[],
    "types":[],
    "rows":[]
  }
]
```

#### Stopping the server:
* q + Enter
