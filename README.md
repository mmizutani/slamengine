[![Build status](https://travis-ci.org/slamdata/slamengine.svg?branch=master)](https://travis-ci.org/slamdata/slamengine)
[![Coverage Status](https://img.shields.io/coveralls/slamdata/slamengine.svg)](https://coveralls.io/r/slamdata/slamengine)
[![Stories in Ready](https://badge.waffle.io/slamdata/slamengine.png?label=ready&title=Ready)](https://waffle.io/slamdata/slamengine)

# SlamEngine

The NoSQL analytics engine that powers SlamData.

This is the open source site for SlamData for people who want to hack on or contribute to the development of SlamData.

**For pre-built installers for the SlamData application, please visit the [official SlamData website](http://slamdata.com).**

**Note**: SlamData only supports MongoDB 2.6.X and higher.

## Using the Pre-Built JARs

In [Github Releases](http://github.com/slamdata/slamengine/releases), you can find pre-built JARs.

After installing Java 7, you can run the console as follows:

```
java -jar [jar-file] slamdata.engine.repl.Repl  [config file]
```

You can run the lightweight HTTP API as follows:

```
java -jar [jar-file] slamdata.engine.api.Server [config file]
```

where `[jar-file]` is the JAR from Github releases, and `[config file]` is the configuration file required by SlamEngine ([see below](#configure)).

## Building from Source

**Note**: This requires Java 7.

### Checkout

```bash
git clone git@github.com:slamdata/slamengine.git
```

### Build

```bash
./sbt one-jar
```

The build process will ask
```
Multiple main classes detected, select one to run:

 [1] slamdata.engine.repl.Repl
 [2] slamdata.engine.api.Server
```
Choose `slamdata.engine.repl.Repl` for a JAR that offers a standalone interactive session, or `slamdata.engine.api.Server` for a server that the SlamData front-end (or any HTTP client) can talk to.

The path of the JAR will be `./target/scala-2.10/slamengine_2.10-[version]-SNAPSHOT-one-jar.jar`, where `[version]` is the SlamEngine version number.

### Configure

Create a configuration file with the following format:

```json
{
  "server": {
    "port": 8080
  },

  "mountings": {
    "/": {
      "mongodb": {
        "database":       "foo",
        "connectionUri":  "mongodb://..."
      }
    }
  }
}
```

### Run

```bash
java -jar path/to/slamengine.jar [config file]
```

## REPL Usage

The interactive REPL accepts SQL `SELECT` queries.

```json
slamdata$ select * from "/zips" where state='CO' limit 3
...
{ "_id" : "80002" , "city" : "ARVADA" , "loc" : [ -105.098402 , 39.794533] , "pop" : 12065 , "state" : "CO"}
{ "_id" : "80003" , "city" : "ARVADA" , "loc" : [ -105.065549 , 39.828572] , "pop" : 32980 , "state" : "CO"}
{ "_id" : "80004" , "city" : "ARVADA" , "loc" : [ -105.11771 , 39.814066] , "pop" : 33260 , "state" : "CO"}

slamdata$ select city from "/zips" limit 3
...
{ "_id" : "35004" , "city" : "ACMAR"}
{ "_id" : "35005" , "city" : "ADAMSVILLE"}
{ "_id" : "35006" , "city" : "ADGER"}
```

You may also store the result of a SQL query:

```sql
slamdata$ out1 := select * from "/zips" where state='CO' limit 3
```

Type `help` for information on other commands.


## API Usage

The server provides a simple JSON API.


### GET /query/fs/[path]?q=[query]

Executes a SQL query, contained in the single, required query parameter, on the backend responsible for the 
request path. The result is returned in the response body, formatted as one JSON object per line.

SQL `limit` syntax may be used to keep the result size reasonable.


### POST /query/fs/[path]?foo=var

Executes a SQL query, contained in the request body, on the backend responsible for the request path. 

The `Destination` header must specify the *output path*, where the results of the query will become available
if this API successfully completes.

All paths referenced in the query, as well as the output path, are interpreted as relative to the request path,
unless they begin with `/`.

SlamSQL supports variables inside queries (`SELECT * WHERE pop > :cutoff`). Values for these variables should
be specified as query parameters in this API. Failure to specify valid values for all variables used 
inside a query will result in an error.

This API method returns the name where the results are stored, as an absolute path, as well as logging
information.

```json
{
  "out": "/[path]/tmp231",
  "phases": [
    ...
  ]
}
```

If an error occurs while compiling or executing the query, a 500 response is 
produced, with this content:

```json
{
  "error": "[very large error text]",
  "phases": [
    ...
  ]
}
```

The `phases` array contains a sequence of objects containing the result from
each phase of the query compilation process. A phase may result in a tree of 
objects with `type`, `label` and (optional) `children`:

```json
{
  ...,
  "phases": [
    ...,
    {
      "name": "Logical Plan",
      "tree": {
        "type": "LogicalPlan/Let",
        "label": "'tmp0",
        "children": [
          {
            "type": "LogicalPlan/Read",
            "label": "./zips"
          },
          ...
        ]
      }
    },
    ...
  ]
}
```

Or a blob of text:

```json
{
  ...,
  "phases": [
    ...,
    {
      "name": "Mongo",
      "detail": "db.zips.aggregate([\n  { \"$sort\" : { \"pop\" : 1}}\n])\n"
    }
  ]
}
```

Or an error (typically no further phases appear, and the error repeats the 
error at the root of the response):

```json
{
  ...,
  "phases": [
    ...,
    {
      "name": "Physical Plan",
      "error": "Cannot compile ..."
    }
  ]
}
```

### GET /metadata/fs/[path]

Retrieves metadata about the files, directories, and mounts at the specified path.

```json
{
  "children": [
    {"name": "test", "type": "mount"},
    {"name": "foo", "type": "directory"},
    {"name": "bar", "type": "file"}
  ]
}
```

### GET /data/fs/[path]?offset=[offset]&limit=[limit]

Retrieves data from the specified path, formatted as one JSON object per line. The `offset` and `limit` parameters are optional, and may be used to page through results.

```json
{"id":0,"guid":"03929dcb-80f6-44f3-a64c-09fc1d810c61","isActive":true,"balance":"$3,244.51","picture":"http://placehold.it/32x32","age":38,"eyeColor":"green","latitude":87.709281,"longitude":-20.549375}
{"id":1,"guid":"09639710-7f99-4fe1-a890-b1b592cbe223","isActive":false,"balance":"$1,544.65","picture":"http://placehold.it/32x32","age":27,"eyeColor":"blue","latitude":52.394181,"longitude":-0.631589}
{"id":2,"guid":"e71b7f01-ce0e-4824-ad1e-4e118872aec4","isActive":true,"balance":"$1,882.92","picture":"http://placehold.it/32x32","age":24,"eyeColor":"green","latitude":30.061766,"longitude":-106.813523}
{"id":3,"guid":"79602676-6f63-41d0-9c0a-a4f5851a43db","isActive":false,"balance":"$1,281.00","picture":"http://placehold.it/32x32","age":25,"eyeColor":"blue","latitude":14.713939,"longitude":62.253264}
{"id":4,"guid":"0024a8ad-373f-459a-8316-d50d7a8f7b10","isActive":true,"balance":"$1,908.50","picture":"http://placehold.it/32x32","age":26,"eyeColor":"brown","latitude":-21.874648,"longitude":67.270659}
{"id":5,"guid":"f7e33b92-a885-450e-8ad5-92103b1f5ff3","isActive":true,"balance":"$2,231.90","picture":"http://placehold.it/32x32","age":31,"eyeColor":"blue","latitude":58.461107,"longitude":176.40584}
{"id":6,"guid":"a2863ec1-9652-46d3-aa12-aa92308de055","isActive":false,"balance":"$1,621.67","picture":"http://placehold.it/32x32","age":34,"eyeColor":"blue","latitude":-83.908456,"longitude":67.190633}
```

### PUT /data/fs/[path]

Replaces data at the specified path, formatted as one JSON object per line in the same format as above.
Either succeeds, replacing any previous contents atomically, or else fails leaving the previous contents
unchanged.

### POST /data/fs/[path]

Appends data to the specified path, formatted as one JSON object per line in the same format as above.
If an error occurs, some data may have been written, and the content of the response describes what 
was done.

### DELETE /data/fs/[path]

Removes all data at the specified path. Single files are deleted atomically.


### MOVE /data/fs/[path]

Moves data from one path to another within the same backend. The new path must
be provided in the "Destination" request header. Single files are deleted atomically.

## Troubleshooting

First, make sure that the slamdata/slamengine Github repo is building correctly (the status is displayed at the top of the README). Then, you can use
```bash
./sbt test
```
to ensure that your local version is also passing the tests.

Check to see if the problem you are having is mentioned in the [Github issues](https://github.com/slamdata/slamengine/issues) and, if it isn’t, feel free to create a new issue.

You can also discuss issues on the SlamData IRC channel: [#slamdata](irc://chat.freenode.net/%23slamdata) on [Freenode](http://freenode.net).

## Legal

Released under the GNU AFFERO GENERAL PUBLIC LICENSE. See `LICENSE` file in the repository.

Copyright 2014 SlamData Inc.
