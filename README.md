# bitpeace – Handle binary data with doobie

This is a library to store and load binary data based on
[doobie](https://github.com/tpolecat/doobie).

It stores binary files in chunks and offers ways to retrieve parts of
each file, making it for example useful when serving range requests
over http.


## Using

Bitpeace is available from maven central for scala 2.11 and 2.12.

```
"com.github.eikek" %% "bitpeace-core" % "0.1.0"
```

The initial version `0.1.0` is build against doobie `0.4.x`. From
bitpeace `0.2.0` it is build agains doobie `0.5.x`.


## Dependencies

It obviously depends on [doobie](https://github.com/tpolecat/doobie)
(the
[cats](http://typelevel.org/cats/)/[fs2](https://github.com/functional-streams-for-scala/fs2)
version).

While trying to minimize further dependencies, I choose to include two
more:

- `tika-core` (optional), Apache 2.0 License, http://tika.apache.org
- `scodec-bits`, BSD-3-Clause, https://github.com/scodec/scodec-bits

Both dependencies are itself “dependency-free”.

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

``` scala
BitpeaceConfig.default[Task]
```

It uses javas `UUID` class to generate random ids and has no ability
to detect mimetypes. The mimetype will always be
`application/octet-stream` for all files.

If you add `tika-core` to your project, you can use the other default:

``` scala
BitpeaceConfig.defaultTika[Task]
```

which only differs in that the `MimetypeDetect` is now implemented
using the `tika` library.

The second requirement is a doobie `Transactor` to connect to the
database. For example, this creates one for the H2 database:

``` scala
val xa = Transactor.fromDriverManager[Task](
  "org.h2.Driver", s"jdbc:h2:./testdb", "sa", ""
)
```

Given a config and a transactor, the main entrypoint `Bitpeace` can be created:

``` scala
val bitpeace = Bitpeace(BitpeaceConfig.defaultTika, xa)
```

In order to start using it, the database schema must exist. The
`BitpeaceTables` class is a convenience helper to do that:

``` scala
BitpeaceTables(BitpeaceConfig.default).create(sql.Dbms.H2)
  .transact(xa).unsafeRun
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

``` scala
import bitpeace._

val chunksize = 128 * 1024
val data: Stream[F, Byte] = …
val meta: Stream[F, FileMeta] = bitpeace.saveNew(data, chunksize, MimetypeHint.none)
```

The `FileMeta` return value contains some meta data about the input
data, like a sha checksum, size, chunksize and the mimetype. Its `id`
can later be used to get the data back out.

The id is generated using the random id generation function from the
config. You can supply a custom fixed id for a file, too.

``` scala
bitpeace.saveNew(data, chunksize, MimetypeHint.none, fileId = Some("abc123"))
```

#### save (no duplicates)

The `saveNew` command simply inserts the data generating a random
id. If you don't want duplicates, you can run `makeUnique`:

``` scala
val out: Outcome[FileMeta] = bitpeace.makeUnique(meta)
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

``` scala
bitpeace.saveNew(data, chunksize, MimetypeHint.none).flatMap(bitpeace.makeUnique)
```

or use the operation `save` (which is just a shortcut for the above):

``` scala
bitpeace.save(data, chunksize, MimetypeHint.none)
```

#### addChunks

The third case is when chunks of data arrive in some random
order. Then you can use `addChunk`:

``` scala
val chunk = FileChunk(…)
bitpeace.addChunk(chunk, chunksize, 12, MimetypeHint.none)
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

``` scala
val id: String = …
val meta: Stream[F, FileMeta] = bitpeace.get(id)
val data: Stream[F, Byte] = meta.through(bitpeace.fetchData(RangeDef.all))
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

``` scala
// get the first chunk only
bitpeace.fetchData(RangeDef.firstChunk)

// get the first x bytes
bitpeace.fetchData(RangeDef.firstBytes(1024))

// get next 2K bytes skipping 4K bytes
bitpeace.fetchData(RangeDef.bytes(Some(4 * 1024), Some(2 * 1024)))

// get all remaining bytes after skipping 4K
bitpeace.fetchData(RangeDef.bytes(Some(4 * 1024), None))
```


## Misc

The library is distributed using the MIT license.

Feedback is very welcome! Put it in a mail to `eikek` at `posteo.de`
or the issue tracker ….
