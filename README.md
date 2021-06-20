# bitpeace – Handle binary data with doobie

[![Build Status](https://img.shields.io/travis/eikek/bitpeace/master?style=flat-square)](https://travis-ci.org/eikek/bitpeace)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat-square&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![License](https://img.shields.io/github/license/eikek/bitpeace.svg?style=flat-square&color=steelblue)](https://github.com/eikek/bitpeace/blob/master/LICENSE.txt)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.eikek/bitpeace-core_2.13?color=blue&style=flat-square)](https://search.maven.org/artifact/com.github.eikek/bitpeace-core_2.13)

This is a library to store and load binary data based on
[doobie](https://github.com/tpolecat/doobie).

It stores binary files in chunks and offers ways to retrieve parts of
each file, making it for example useful when serving range requests
over http.


## Using

Bitpeace is available from maven central for scala 2.12, 2.13 and 3.

```
"com.github.eikek" %% "bitpeace-core" % "0.9.0-M1"
```

Note: from 0.9.0 on, it is build against FS2 3/CE3. When doobie
releases a final version (currently it is 1.0.0-Mx), then bitpeace
0.9.0 will be released as well. Until then progress is tracked in
0.9.0-Mx releases.

## Dependencies

It obviously depends on [doobie](https://github.com/tpolecat/doobie)
and therefore on [cats](http://typelevel.org/cats/) and
[fs2](https://github.com/functional-streams-for-scala/fs2).

While trying to minimize further dependencies, I chose to these:

- `tika-core` (optional), Apache 2.0 License, http://tika.apache.org
- `scodec-bits`, BSD-3-Clause, https://github.com/scodec/scodec-bits
- `jakarta.activation-api`, BSD-3-Clause
  https://eclipse-ee4j.github.io/jaf/ (used to parse mimetype, it is
  included in the jdk up until java8)

The last two dependencies are itself “dependency-free”. The
`tika-core` dependcny is marked as optional. It is only required, if
using the provided config that uses tika for mime-type extraction
`BitpeaceConfig.defaultTika`.

Since mimetype detection belongs to “handling binaries”, I wanted to
include it in this library. Tika is used for mimetype detection by
default, but can be replaced by custom code. Therefore it is added as
optional dependency and you need to declare it in case you want to use
it.

The `scodec-bits` library is used for its `ByteVector` class; to avoid
usages of `Array[Byte]`.

## DBMS Support

DML statements (Queries and `insert`/`update`) are standard SQL and
should work on most database systems.

The code to create the schema `BitpeaceTables` works with Postgres and
H2. For other database systems, simply create the two tables yourself.


## Setup

Bitpeace needs a small config that defines the table names
to use and two functions:

- instance of `MimetypeDetect` to detect mimetype given some bytes
- generate a random string to use as an primary key

There is a default config:

```scala
import _root_.bitpeace._, cats.effect.IO

val cfg1 = BitpeaceConfig.default[IO]
// cfg1: BitpeaceConfig[IO] = BitpeaceConfig(
//   metaTable = "FileMeta",
//   chunkTable = "FileChunk",
//   mimetypeDetect = bitpeace.MimetypeDetect$$anon$1@1be14a6b,
//   randomIdGen = Delay(
//     thunk = bitpeace.BitpeaceConfig$$$Lambda$31724/1487159639@3ae57761
//   )
// )
```

It uses javas `UUID` class to generate random ids and has no ability
to detect mimetypes. The mimetype will always be
`application/octet-stream` for all files.

If you add `tika-core` to your project, you can use the other default:

```scala
val cfg2 = BitpeaceConfig.defaultTika[IO]
// cfg2: BitpeaceConfig[IO] = BitpeaceConfig(
//   metaTable = "FileMeta",
//   chunkTable = "FileChunk",
//   mimetypeDetect = bitpeace.TikaMimetypeDetect$@3eaef9ac,
//   randomIdGen = Delay(
//     thunk = bitpeace.BitpeaceConfig$$$Lambda$31724/1487159639@3ae57761
//   )
// )
```

which only differs in that the `MimetypeDetect` is now implemented
using the `tika` library.

The second requirement is a doobie `Transactor` to connect to the
database. For example, this creates one for the H2 database:

```scala
import doobie._
import doobie.implicits._

val xa = Transactor.fromDriverManager[IO](
  "org.h2.Driver", s"jdbc:h2:/tmp/bitpeace-testdb", "sa", ""
)
// xa: Transactor.Aux[IO, Unit] = doobie.util.transactor$Transactor$$anon$13@5b29f615
```

Given a config and a transactor, the main entrypoint `Bitpeace` can be created:

```scala
val bitpeace = Bitpeace(BitpeaceConfig.defaultTika[IO], xa)
// bitpeace: Bitpeace[IO[A]] = bitpeace.Bitpeace$$anon$1@32756445
```

In order to start using it, the database schema must exist. The
`BitpeaceTables` class is a convenience helper to do that:

```scala
import cats.effect.unsafe.implicits.global

BitpeaceTables(BitpeaceConfig.default[IO]).create(sql.Dbms.H2).transact(xa).unsafeRunSync()
```


## Usage

### Storing data

The data to store is given as a `Stream[F, Byte]`. A chunksize must be
specified that defines how many bytes are stored in one blob
object. Other two parameters involve a hint to support mimetype
detection (for example the filename) and a timestamp associated to
that file.

#### saveNew

Data can be inserted using `saveNew`:

```scala
import fs2._
import scodec.bits.ByteVector
import cats.effect.unsafe.implicits.global //for use in the repl //for use in the repl

val chunksize = 128 * 1024
// chunksize: Int = 131072
val data = Stream.chunk[IO, Byte](Chunk.byteVector(ByteVector.fromValidHex("68656c6c6f20776f726c64")))
// data: Stream[IO, Byte] = Stream(..)
val meta = bitpeace.saveNew(data, chunksize, MimetypeHint.none)
// meta: Stream[IO[A], FileMeta] = Stream(..)
val savedFileMeta = meta.compile.lastOrError.unsafeRunSync()
// savedFileMeta: FileMeta = FileMeta(
//   id = "2e94d87d-a565-47c3-8354-f1583aa0d42f",
//   timestamp = 2021-06-20T15:05:08.261Z,
//   mimetype = Mimetype(primary = "text", sub = "plain", params = Map()),
//   length = 11L,
//   checksum = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
//   chunks = 1,
//   chunksize = 131072
// )
```

The `FileMeta` return value contains some meta data about the input
data, like a sha checksum, size, chunksize and the mimetype. Its `id`
can later be used to get the data back out.

The id is generated using the random id generation function from the
config. You can supply a custom fixed id for a file, too.

```scala
bitpeace.saveNew(data, chunksize, MimetypeHint.none, fileId = Some("abc123"))
// res1: Stream[IO[A], FileMeta] = Stream(..)
```

#### save (no duplicates)

The `saveNew` command simply inserts the data generating a random
id. If you don't want duplicates, you can run `makeUnique`:

```scala
val out = bitpeace.makeUnique(savedFileMeta)
// out: Stream[IO[A], Outcome[FileMeta]] = Stream(..)
```

This will check if there is a file with the same checksum. If true,
the given `FileMeta` (and data) is deleted and the just found value is
returned. This is encoded in the `Outcome.Unmodified` value.

If there is no such file, the id of the given `FileMeta` is updated to
be its checksum (to ensure no duplicates). Then it is returned inside
a `Outcome.Created` indicating that the given data has been used. If
during the id update an error occurs, it may be caused by storing the
same file concurrently. Then it is again tried to lookup an existing
file.

You can combine those two operations:

```scala
bitpeace.saveNew(data, chunksize, MimetypeHint.none).flatMap(bitpeace.makeUnique)
// res2: Stream[IO[x], Outcome[FileMeta]] = Stream(..)
```

or use the operation `save` (which is just a shortcut for the above):

```scala
bitpeace.save(data, chunksize, MimetypeHint.none)
// res3: Stream[IO[A], Outcome[FileMeta]] = Stream(..)
```

#### addChunks

The third case is when chunks of data arrive in some random
order. Then you can use `addChunk`:

```scala
val chunk = FileChunk("file-id", 1, ByteVector.fromValidHex("68656c6c6f20776f726c64"))
// chunk: FileChunk = FileChunk(
//   fileId = "file-id",
//   chunkNr = 1L,
//   chunkData = Chunk(
//     bytes = View(
//       at = scodec.bits.ByteVector$AtArray@2c734184,
//       offset = 0L,
//       size = 11L
//     )
//   )
// )
bitpeace.addChunk(chunk, chunksize, 12, MimetypeHint.none)
// res4: Stream[IO[A], Outcome[FileMeta]] = Stream(..)
```

It is necessary to tell when the last chunk arrives, to calculate the
checksum and set the timestamp. That's why you either need to tell the
total number of chunks (it is the `12` above), or the total length of
the file together with the intended chunksize.

The operation returns the updated `FileMeta` object and you can tell
whether the data is complete if the length and checksum are set. The
result is wrapped in a `Outcome` to tell whether the chunk already
existed or not.

_Chunks must be 0-indexed!_

### Retrieving data

The id to identify the `FileMeta` object is required to retrieve
data. With a `FileMeta` object, one can stream the bytes using either
`fetchData` or `fetchData2`.

```scala
val id: String = "xyz123"
// id: String = "xyz123"
val meta2 = bitpeace.get(id)
// meta2: Stream[IO[A], Option[FileMeta]] = Stream(..)
val data2 = meta.through(bitpeace.fetchData(RangeDef.all))
// data2: Stream[IO[x], Byte] = Stream(..)
```

The difference between `fetchData` and `fetchData2` is that the former
uses one connection per chunk, whereas the latter uses one connection
for the entire stream (i.e. it is closed once the stream terminates).

The `fetchData` operations expect a `RangeDef` argument. This can be
used to return a specific byte range. A `RangeDef` is a function from
`FileMeta` and a range request to a `Range`. Since range requests can
be wrong (i.e. exceed total length), the return is wrapped in a
`cats.data.Validated`. The `RangeDef` companion object contains
several methods to construct `RangeDefs`. For example:

```scala
// get the first chunk only
bitpeace.fetchData(RangeDef.firstChunk)
// res5: Stream[IO[A], FileMeta] => Stream[IO[A], Byte] = bitpeace.Bitpeace$$anon$1$$Lambda$32092/1618457419@17788fd0

// get the first x bytes
bitpeace.fetchData(RangeDef.firstBytes(1024))
// res6: Stream[IO[A], FileMeta] => Stream[IO[A], Byte] = bitpeace.Bitpeace$$anon$1$$Lambda$32092/1618457419@42d8a187

// get next 2K bytes skipping 4K bytes
bitpeace.fetchData(RangeDef.bytes(Some(4 * 1024), Some(2 * 1024)))
// res7: Stream[IO[A], FileMeta] => Stream[IO[A], Byte] = bitpeace.Bitpeace$$anon$1$$Lambda$32092/1618457419@5452e088

// get all remaining bytes after skipping 4K
bitpeace.fetchData(RangeDef.bytes(Some(4 * 1024), None))
// res8: Stream[IO[A], FileMeta] => Stream[IO[A], Byte] = bitpeace.Bitpeace$$anon$1$$Lambda$32092/1618457419@629be4e7
```


## Misc

The library is distributed using the MIT license.

Feedback is very welcome! Put it in a mail to `eikek` at `posteo.de`
or the issue tracker.
