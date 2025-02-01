package io.hiis.service.application.services.database

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import mongo4cats.bson.Document
import mongo4cats.models.collection.{ FindOneAndUpdateOptions, UpdateOptions }
import mongo4cats.operations.Projection
import mongo4cats.zio.ZMongoCollection
import zio.{ Task, ZIO }

trait MongodbService[+T] {
  import MongodbService._

  protected def collection: Task[ZMongoCollection[Document]]

  /**
   * Save an object in database
   *
   * @param obj
   *   the object
   * @return
   *   The saved object
   */
  final def save[TT >: T](obj: TT)(implicit encoder: Encoder[TT], decoder: Decoder[TT]): Task[TT] =
    for {
      coll <- collection
      _    <- coll.insertOne(obj)
    } yield obj

  /**
   * Save many objects in database
   *
   * @param objects
   *   the list of objects
   * @return
   *   The saved objects
   */
  final def saveMany[TT >: T](
      objects: Seq[TT]
  )(implicit encoder: Encoder[TT], decoder: Decoder[TT]): Task[Seq[TT]] = for {
    coll <- collection
    _    <- coll.insertMany(objects)
  } yield objects

  /**
   * Delete elements using single query
   *
   * @param query
   *   the query
   * @return
   *   Future[True] if operation was successful
   */
  final def deleteOne(query: Document): Task[Unit] = for {
    coll <- collection
    _    <- coll.deleteOne(query)
  } yield ()

  /**
   * Delete elements using single query
   *
   * @param query
   *   the bson query
   * @return
   *   Future[True] if operation was successful
   */
  final def deleteMany(query: Document): Task[Unit] = for {
    coll <- collection
    _    <- coll.deleteMany(query)
  } yield ()

  /**
   * Delete elements using multiple queries
   *
   * @param queries
   *   the comma separated list of queries
   * @return
   */
  final def deleteMany(queries: Document*): Task[Unit] =
    for {
      coll <- collection
      _    <- ZIO.foreach(queries)(doc => coll.deleteMany(doc))
    } yield ()

  /**
   * Update an object in database
   *
   * @param query
   *   the query
   * @param obj
   *   the object
   * @return
   *   The updated object if operation was successful
   */
  final def replaceOne[TT >: T](query: Document, obj: TT)(implicit
      encoder: Encoder[TT],
      decoder: Decoder[TT]
  ): Task[TT] = for {
    coll <- collection
    _    <- coll.replaceOne(query, obj)
  } yield obj

  /**
   * Update an object in database
   *
   * @param query
   *   the query
   * @param obj
   *   the object
   * @return
   *   The updated object if operation was successful
   */
  final def replaceMany[TT >: T](query: Document, obj: TT)(implicit
      encoder: Encoder[TT],
      decoder: Decoder[TT]
  ): Task[TT] = for {
    coll <- collection
    _    <- coll.replaceOne(query, obj)
  } yield obj

  /**
   * Update some parts of the document in the database
   *
   * @param query
   *   the query
   * @param obj
   *   the object
   * @return
   *   The updated object if operation was successful
   */
  final def updateOne[TT >: T](
      query: Document,
      obj: Document,
      options: FindOneAndUpdateOptions = FindOneAndUpdateOptions(upsert = true)
  )(implicit
      encoder: Encoder[TT],
      decoder: Decoder[TT]
  ): Task[Option[TT]] =
    for {
      coll <- collection
      value <- coll
        .findOneAndUpdate(query, obj, options)
        .map(_.flatMap(doc => toObject(doc)(encoder, decoder)))
    } yield value

  /**
   * Update some parts of the document in the database
   *
   * @param query
   *   the query
   * @param obj
   *   the object
   * @return
   *   The updated object if operation was successful
   */
  final def updateMany[TT >: T](
      query: Document,
      obj: Document,
      options: UpdateOptions = UpdateOptions(upsert = true)
  )(implicit
      encoder: Encoder[TT],
      decoder: Decoder[TT]
  ): Task[Option[TT]] =
    for {
      coll  <- collection
      value <- coll.updateMany(query, obj, options) *> get[TT](query)
    } yield value

  /**
   * Get a particular object from database
   *
   * @param query
   *   the query
   * @return
   *   The object if found
   */
  final def get[TT >: T](
      query: Document
  )(implicit encoder: Encoder[TT], decoder: Decoder[TT]): Task[Option[TT]] = for {
    coll   <- collection
    result <- coll.find(query).projection(Projection.excludeId).limit(1).all
    value <- ZIO
      .fromOption(result.headOption)
      .foldZIO(_ => ZIO.succeed(None), doc => toObjectZIO[TT](doc).map(value => Some(value)))
  } yield value

  /**
   * Get objects from database that satisfies the query
   *
   * @param query
   *   the query
   * @return
   *   The object if found or Future[None] otherwise
   */
  final def getMany[TT >: T](
      query: Document
  )(implicit encoder: Encoder[TT], decoder: Decoder[TT]): Task[List[TT]] = for {
    coll <- collection
    result <- coll
      .find(query)
      .projection(Projection.excludeId)
      .limit(Int.MaxValue)
      .all
      .flatMap(docs => toObjectsZIO[TT](docs.toSeq))
  } yield result.toList
}

object MongodbService {
  implicit def toDocument[T: Encoder: Decoder](obj: T): Document =
    Document.parse(obj.asJson.noSpaces)

  implicit def toObject[T: Encoder: Decoder](doc: Document): Option[T] =
    decode[T](doc.toJson).toOption

  implicit def toObjectZIO[T: Encoder: Decoder](doc: Document): Task[T] =
    ZIO.fromEither(decode[T](doc.toJson))

  implicit def toSeqOfDocuments[T: Encoder: Decoder](objects: Seq[T]): Seq[Document] =
    objects.map(toDocument[T])

  implicit def toObjects[T: Encoder: Decoder](objects: Seq[Document]): Seq[T] =
    objects.flatMap(toObject[T])

  implicit def toObjectsZIO[T: Encoder: Decoder](objects: Seq[Document]): Task[Seq[T]] =
    ZIO.collectAll(objects.map(toObjectZIO[T]))
}
