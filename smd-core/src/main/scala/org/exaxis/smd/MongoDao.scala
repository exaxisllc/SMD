/*
 * Copyright (c) 2013-2017 Exaxis, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.exaxis.smd

import org.joda.time.DateTime

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger
import reactivemongo.api.{Cursor, DefaultDB, QueryOpts, ReadPreference}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{MultiBulkWriteResult, WriteError, WriteResult}
import reactivemongo.bson.{BSONBoolean, BSONDateTime, BSONDouble, BSONInteger, BSONLong, BSONRegex, _}
import reactivemongo.api.commands.bson.BSONCountCommandImplicits._
import reactivemongo.api.commands.bson.BSONCountCommand.Count
import akka.stream.Materializer


/**
 * MongoDAO[T] is a wrapper around the reactiveMongo libraries to consolidate all the common code that each individual class
 * would need for CRUD functions. This class works at a higher level. It works by having the class to be marshalled in and
 * out of mongo as the type parameter T.
 *
 * Created by dberry on 13/3/14.
 */
trait MongoDao[T] {
  val logger = Logger(classOf[MongoDao[T]])

  /**
   * The name of the collection in the mongo database. This will be defined in the companion object for T
   */
  val collectionName: String

  implicit val defaultDBFuture : Future[DefaultDB];
  implicit val executionContext : ExecutionContext

  /**
   * The reactive mongo collection to perform all the mongo calls
   *
   * @return - BSONCollection
   */
//  lazy val collection = reactiveMongoApi.db.collection[BSONCollection](collectionName)
  def collection = defaultDBFuture.map(_.collection[BSONCollection](collectionName))

  /**
   * Inserts a list of T into mongo
   *
   * @param docList - List of T
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @return - a Future Try[Int]
   */
  def insert(docList: List[T], ordered:Boolean=false)(implicit writer: BSONDocumentWriter[T]): Future[Try[Int]] = {
    logger.debug(s"Inserting document: [collection=$collectionName, data=$docList]")
    multiTryIt(collection.flatMap{coll =>
      val bulkDocs = docList.map(implicitly[coll.ImplicitlyDocumentProducer](_))
      coll.bulkInsert(ordered=true)(bulkDocs: _*)
    })
  }


  /**
   * Insert a T into mongo
   *
   * @param document - a T
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @return - a Future Try[Int]
   */
  def insert(document: T)(implicit writer: BSONDocumentWriter[T]): Future[Try[Int]] = {
    logger.debug(s"Inserting document: [collection=$collectionName, data=$document]")
    tryIt(collection.flatMap(_.insert(document)))
  }

  /**
   *  Retrieve a T by its string id.
   *
   * @param id - an Option[String]
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return - a Future Option[T]
   */
  def findById(id: Option[String])(implicit reader: BSONDocumentReader[T]): Future[Option[T]] = findOne(DBQueryBuilder.id(id))

  /**
   * Retrieve a T by its BSONObjectID
   *
   * @param id - a BSONObjectID
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return - a Future Option[T]
   */
  def findById(id: BSONObjectID)(implicit reader: BSONDocumentReader[T]): Future[Option[T]] = findOne(DBQueryBuilder.id(id))

  /**
   * Retrieve a sorted List of T based on a query and a sort order
   *
   * @param query - a BSONDocument containing query information
   * @param sort - a BSONDocument containing sort information
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return - a Future List[T]
   */
  def findAll(query: BSONDocument = BSONDocument.empty, sort: BSONDocument = BSONDocument.empty)(implicit reader: BSONDocumentReader[T]): Future[List[T]] = {
    logger.debug(s"Finding documents: [collection=$collectionName, query=$query]")
    collection.flatMap(_.find(query).sort(sort).cursor[T]().collect[List](-1,Cursor.FailOnError[List[T]]()))
  }

  /**
   * Retrieve a sorted List of T based on information received in a HTTP request
   *
   * @param filter - A regex to match docs against
   * @param orderby - The orderby information
   * @param params - Specific values or ranges to query on
   * @param reader - The BSONDocumentReader on the companion object for T
   * @param daoData - The filterset and attribute map information for T
   * @param tag - Type information for T
   * @return - Future List[T]
   */
  def findAll(filter: Option[String], orderby: Option[String], params: Map[String, String])(implicit reader: BSONDocumentReader[T], daoData:DaoData[T], tag : TypeTag[T] ): Future[List[T]] = {
    // Execute the query
    findAll(buildQueryDocument(filter, params, daoData), buildSortDocument(orderby, daoData.attributeMap))
  }

  /**
   * Retrieve a page worth List of T based on information received in a HTTP request
   *
   * @param filter - A regex to match docs against
   * @param orderby - The orderby information
   * @param page - The page of data to be returned
   * @param ipp - Items per page
   * @param params - Specific values or ranges to query on
   * @param reader - The BSONDocumentReader on the companion object for T
   * @param daoData - The filterset and attribute map information for T
   * @param tag - Type information for T
   * @return - Future[(List[T], Int)]
   */
  def find(filter: Option[String], orderby: Option[String], page: Int, ipp: Int, params: Map[String, String])(implicit reader: BSONDocumentReader[T], daoData:DaoData[T], tag : TypeTag[T] ): Future[(List[T], Int)] = {
  // Execute the query
    find(buildQueryDocument(filter, params, daoData), buildSortDocument(orderby, daoData.attributeMap), page, ipp)
  }

  /**
   * Builds a BSONDocument containing sort information based on string data
   *
   * @param orderby - The orderby information to be parsed
   * @param attributeMap - The map of scala attributes to mongo attributes
   * @return
   */
  private def buildSortDocument(orderby: Option[String], attributeMap:Map[String,String]) = orderby match {
    case None => logger.debug("orderby = None"); BSONDocument.empty
    case Some(sorts) => logger.debug("orderby = "+sorts);
      sorts.split(",").foldLeft(BSONDocument.empty) {
        (doc, s) =>
          s.split(" ").toList match {
            case a :: Nil => doc ++ (attributeMap.getOrElse(a, a) -> 1)
            case a :: b :: Nil => doc ++ (attributeMap.getOrElse(a, a) -> b.toInt)
            case _ => doc ++ ()
          }
      }
  }

  /**
   * Builds a BSONDocument containing query information based on string data
   *
   * @param filter - a regex to be matched against the filterset
   * @param params - params that contain search criteria
   * @param daoData - The filterset and attribute map information for T
   * @param tag - Type information for T
   * @return - BSONDocument
   */
  private def buildQueryDocument(filter: Option[String], params: Map[String, String], daoData:DaoData[T])(implicit tag : TypeTag[T] ) = {
    val leftDocument = filter match {
              case None => logger.debug("q = None"); BSONDocument.empty
              case Some(s) => logger.debug("q = "+s)
                BSONDocument( "$or" -> daoData.filterSet.foldLeft(BSONArray.empty) { (arr, attr) => arr ++  BSONDocument(attr -> BSONRegex(".*" + s + ".*", "i")) })
            }
    val rightDocument = buildAttributeDocument(params, daoData.attributeMap)
    if (!leftDocument.isEmpty && !rightDocument.isEmpty)
      BSONDocument("$and" -> (BSONArray.empty ++ leftDocument ++ rightDocument))
    else if (!leftDocument.isEmpty)
      leftDocument
    else if (!rightDocument.isEmpty)
      rightDocument
    else
      BSONDocument.empty

  }

  /**
   * Parses the params and turns it into a BSONDocument containing the matching information of attr->value
    *
    * @param params - params that contain search criteria
   * @param attributeMap - mapping of scala to mongo attributes
   * @param tag - Type information for T
   * @return BSONDocument
   */
  private def buildAttributeDocument(params: Map[String, String], attributeMap:Map[String,String] )(implicit tag : TypeTag[T] ) = {
    params.keys.foldLeft(BSONDocument.empty) {
      (doc,key) => doc ++ processAttribute(key, params.get(key).get, attributeMap.getOrElse(key, key))
    }
  }

  /**
   * Process attribute turns query parameters and values into searchable objects within mongo. Depending on the format of sval, we will convert
   * name and sval into an exact match, an in, or a range
   *
   * ?datefield=12341234    converts to exact match BSONDocument( datefield -> BSONDateTime(123412134) )
   * ?intfield=60           converts to exact match BSONDocument( intfield -> BSONInteger(60)
   * ?intfield=[60, 70, 80] converts to in match    BSONDocument( intfield -> BSONDocument( $in -> BSONArray( BSONInteger(60), BSONInteger(70), BSONInteger(80) ) ) )
   * ?intfield=(60,)        converts to $gte match  BSONDocument( intfield -> BSONDocument( $and -> BSONArray( BSONDocument( $gte -> BSONInteger(60) ), BSONDocument() ) ) )
   * ?intfield=(,70)        converts to $lte match  BSONDocument( intfield -> BSONDocument( $and -> BSONArray( BSONDocument(), BSONDocument($lte -> BSONInteger(70)) ) ) )
   * ?intfield=(60,70)      converts to range match BSONDocument( intfield -> BSONDocument( $and -> BSONArray( BSONDocument( $gte -> BSONInteger(60) ), BSONDocument($lte -> BSONInteger(70)) ) ) )
   *
   * @param name - the scala name for the attribute
   * @param sval - the string value of the attribute
   * @param dbName - the data store name of the attribute
   * @param tag - the scala type tag for the attribute
   * @return - a tuple (String, BSONValue)
   */
  private def processAttribute(name:String, sval:String,  dbName:String)(implicit tag : TypeTag[T] ) : (String, BSONValue) = {
    sval.charAt(0) match {
      case '(' => processRange(name, sval.substring(1,sval.length-1), dbName) // process the range
      case '[' => processIn(name, sval.substring(1,sval.length-1), dbName)    // process the in
      case s => dbName -> convertAttributeToBson(name, sval)
    }
  }

  /**
   * Process query parameters with range values into searchable objects within mongo.
   *
   * ?intfield=(60,)        converts to $gte match  BSONDocument( intfield -> BSONDocument( $and -> BSONArray( BSONDocument( $gte -> BSONInteger(60) ), BSONDocument() ) ) )
   * ?intfield=(,70)        converts to $lte match  BSONDocument( intfield -> BSONDocument( $and -> BSONArray( BSONDocument(), BSONDocument($lte -> BSONInteger(70)) ) ) )
   * ?intfield=(60,70)      converts to range match BSONDocument( intfield -> BSONDocument( $and -> BSONArray( BSONDocument( $gte -> BSONInteger(60) ), BSONDocument($lte -> BSONInteger(70)) ) ) )
   *
   * @param name - the scala name for the attribute
   * @param sval - the string value of the attribute
   * @param dbName - the data store name of the attribute
   * @param tag - the scala type tag for the attribute
   * @return -  a tuple (String, BSONValue)
   */
  // TODO - Get the process range code to drop the $and when it is just a $lte or $gte
  private def processRange(name:String, sval:String,  dbName:String)(implicit tag : TypeTag[T] ) : (String, BSONValue) = {
    val rangeArray = sval.split(",", -1)
    "$and" -> (BSONArray.empty ++ buildRangeBSON("$gte", name, rangeArray(0), dbName) ++ buildRangeBSON("$lte", name, rangeArray(1), dbName))
  }

  /**
   * Build the BSONDocument for the range information
   *
   * @param symbol - "$gte" or "$lte"
   * @param name - The scala name for the attribute
   * @param sval - The value to be compared
   * @param dbName - The mongo name for the attribute
   * @param tag - the scala type tag for the attribute
   * @return - BSONDocument
   */
  private def buildRangeBSON(symbol:String, name:String, sval:String,  dbName:String)(implicit tag : TypeTag[T] ) : BSONDocument = {
    if (sval.isEmpty)
      BSONDocument.empty
    else
      BSONDocument( dbName -> BSONDocument(symbol -> convertAttributeToBson(name, sval)))
  }

  /**
   * Build the BSONDocument for the in match
   *
   * @param name - The scala name for the attribute
   * @param sval - The value to be compared
   * @param dbName - The mongo name for the attribute
   * @param tag - the scala type tag for the attribute
   * @return - BSONDocument
   */
  private def processIn(name:String, sval:String,  dbName:String)(implicit tag : TypeTag[T] ) : (String, BSONValue) = {
    dbName -> BSONDocument( "$in" -> (sval.split(",").map{ inVal => convertAttributeToBson(name, inVal) }))
  }

  /**
   * Converts a name and string value into a BSONValue of the appropriate BSONType. It uses scala reflection to determine the scala type and then maps
   * it into know BSON types.
   *
   * @param name - the datastore name for the attribute
   * @param sval - the string value of the attribute
   * @param tag - the scala type tag for the attribute
   * @return - BSONValue
    * @note WARNING - This method uses EXPERIMENTAL reflection code from scala. Scala does not guarantee backwards compatibility in their releases.
   * @note If you upgrade scala and this method no longer compiles then it MUST be fixed using whatever new classes, methods, etc that the scala team
   * @note decided to go with. I would rather have this than living with things like type erasure for backwards compatibility. If you do not know what
   * @note type erasure is then please put this code down, step away from the code, and let a more informed person work on this code.
   */
  private def convertAttributeToBson(name:String, sval:String)(implicit tag : TypeTag[T] ) : BSONValue = {
      // use reflection to validate the attribute and to construct the right BSONValue
    // TODO: name has . notation to access members and submembers of the class, need to respect that
    tag.tpe.member(TermName(name)) match {
      case NoSymbol => BSONString(sval)
      case s => s.typeSignature match {
          case NullaryMethodType(tpe) if typeOf[Boolean] =:= tpe          => BSONBoolean(sval.toBoolean)
          case NullaryMethodType(tpe) if typeOf[Option[Boolean]] =:= tpe  => BSONBoolean(sval.toBoolean)
          case NullaryMethodType(tpe) if typeOf[Int] =:= tpe              => BSONInteger(sval.toInt)
          case NullaryMethodType(tpe) if typeOf[Option[Int]] =:= tpe      => BSONInteger(sval.toInt)
          case NullaryMethodType(tpe) if typeOf[Long] =:= tpe             => BSONLong(sval.toLong)
          case NullaryMethodType(tpe) if typeOf[Option[Long]] =:= tpe     => BSONLong(sval.toLong)
          case NullaryMethodType(tpe) if typeOf[Double] =:= tpe           => BSONDouble(sval.toDouble)
          case NullaryMethodType(tpe) if typeOf[Option[Double]] =:= tpe   => BSONDouble(sval.toDouble)
          case NullaryMethodType(tpe) if typeOf[DateTime] =:= tpe         => BSONDateTime(sval.toLong)
          case NullaryMethodType(tpe) if typeOf[Option[DateTime]] =:= tpe => BSONDateTime(sval.toLong)
          case _ => if (sval.contains("*")) BSONRegex(sval.replaceAll("\\*", ".*"),"i") else BSONString(sval)  // default is a String
        }
    }
  }

  /**
   * Find a list of documents based on the query information and return a page of information in sorted order
   *
   * @param selector - A BSONDocument containing the selector information
   * @param document - The T containing the data for the document
   * @param sort - A BSONDocument containing the sort criteria
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @return - Future[T]
   */
  def findAndUpdate(selector: BSONDocument = BSONDocument.empty, document: T, sort: BSONDocument = BSONDocument.empty)(implicit reader: BSONDocumentReader[T], writer: BSONDocumentWriter[T]): Future[Try[T]] = {
    val queryString = BSONDocument.pretty(selector)
    val sortString = BSONDocument.pretty(sort)
    logger.debug(s"Finding and modifying documents: [collection=$collectionName, query=$queryString] sort=$sortString")
    for {
      doc <- collection.flatMap(_.findAndUpdate(selector, document, true, true, Some(sort)).map { famr =>
              famr.result[T] match {
                case Some(t) => Success(t)
                case None => famr.lastError match {
                  case Some(le) => Failure(new Exception(le.err.getOrElse("")))
                  case None => Failure(new Exception("Could not find document"))
                }
              }
            })
    } yield doc
  }

  /**
   * Find a list of documents based on the query information and return a page of information in sorted order
   *
   * @param query - A BSONDocument containing the query information
   * @param sort - A BSONDocument containing the sort criteria
   * @param page - The page to return
   * @param ipp - The number of items to return
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return - Future[(List[T], Int)]
   */
  def find(query: BSONDocument = BSONDocument.empty, sort: BSONDocument = BSONDocument.empty, page: Int, ipp: Int)(implicit reader: BSONDocumentReader[T]): Future[(List[T], Int)] = {
    val queryString = BSONDocument.pretty(query)
    val sortString = BSONDocument.pretty(sort)
    logger.debug(s"Finding documents with paging: [collection=$collectionName, query=$queryString] sort=$sortString")
    for {
      docs <- collection.flatMap(_.find(query).sort(sort).options(QueryOpts((page-1)*ipp, ipp)).cursor[T]().collect[List](ipp,Cursor.FailOnError[List[T]]()))
      totalDocs <- collection.flatMap(_.runCommand(Count(query), ReadPreference.Nearest(None)))
    } yield (docs, (totalDocs.count/ipp)+1)
  }

  /**
   * Enumerates a list of documents based on the query information and returns documents in sorted order as they are available. It basically streams the docs
   * from mongo to here. Very Reactive.
   *
   * @param query - A BSONDocument containing the query information
   * @param sort - A BSONDocument containing the sort criteria
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return -  Future[Enumerator[T] ]
   */
  def enumerate(query: BSONDocument = BSONDocument.empty, sort: BSONDocument = BSONDocument.empty)(implicit reader: BSONDocumentReader[T]) = {
    import reactivemongo.play.iteratees.cursorProducer
    val queryString = BSONDocument.pretty(query)
    val sortString = BSONDocument.pretty(sort)
    logger.debug(s"Enumerating documents: [collection=$collectionName, query=$queryString] sort=$sortString")
    collection.map(_.find(query).sort(sort).cursor[T]().enumerator())
  }

  /**
   * Creates a document Source for a list of documents based on the query information and returns documents in sorted order as they are available. It basically streams the docs
   * from mongo to here. Very Reactive.
   *
   * @param query - A BSONDocument containing the query information
   * @param sort - A BSONDocument containing the sort criteria
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return - Future[Source[T, Future[State] ]
   */
  def source(query: BSONDocument = BSONDocument.empty, sort: BSONDocument = BSONDocument.empty)(implicit reader: BSONDocumentReader[T], materializer: Materializer) = {
    import reactivemongo.akkastream.{ State, cursorProducer }
    val queryString = BSONDocument.pretty(query)
    val sortString = BSONDocument.pretty(sort)
    logger.debug(s"Sourcing documents: [collection=$collectionName, query=$queryString] sort=$sortString")
    collection.map(_.find(query).sort(sort).cursor[T]().documentSource())
  }

  /**
   * Find and return one T or None from mongo
   *
   * @param paramMap - params that contain search criteria
   * @param reader - The BSONDocumentReader on the companion object for T
   * @param daoData - DaoSpecific data that must be passed along. Defined on the companion object for T
   * @param tag - the scala type tag for the attribute
   * @return - Future Option[T]
   */
  def findOne(paramMap:Map[String, String])(implicit reader: BSONDocumentReader[T], daoData:DaoData[T], tag : TypeTag[T] ): Future[Option[T]] = {
    logger.debug(s"Finding one: [collection=$collectionName, paramMap=$paramMap]")
    findOne(buildAttributeDocument(paramMap, daoData.attributeMap))
  }

  /**
   * Find and return one T or None from mongo
    *
    * @param query - BSONDocument containing the query information
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return - Future Option[T]
   */
  def findOne(query: BSONDocument = BSONDocument.empty)(implicit reader: BSONDocumentReader[T]): Future[Option[T]] = {
    val queryString = BSONDocument.pretty(query)
    logger.debug(s"Finding one: [collection=$collectionName, query=$queryString]")
    collection.flatMap(_.find(query).one[T])
  }

  /**
   * Update a document in mongo
   *
   * @param id - The String id of the document
   * @param document - The T containing the data for the document
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @return - Future Try[Int]
   */
  def update(id: Option[String], document: T)(implicit writer: BSONDocumentWriter[T]): Future[Try[Int]] = {
    logger.debug(s"Updating document: [collection=$collectionName, id=$id, document=$document]")
    tryIt(collection.flatMap(_.update(DBQueryBuilder.id(id), document)))
  }

  /**
   * Update documents in mongo
   *
   * @param selector - The BSONDocument to selct the documents to update
   * @param modifier - The modifications to that document
   * @param multiple - should multiple records be updated
   * @return - Future Try[Int]
   */
  def update(selector:BSONDocument, modifier:BSONDocument, multiple:Boolean = false): Future[Try[Int]] = {
    tryIt(collection.flatMap(_.update(selector, modifier, multi=multiple)))
  }

  /**
   * Update a document in mongo
   *
   * @param id - The BSONObjectID of the document
   * @param document - The T containing the data for the document
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @return - Future Try[Int]
   */
  def update(id: BSONObjectID, document: T)(implicit writer: BSONDocumentWriter[T]): Future[Try[Int]] = {
    logger.debug(s"Updating document: [collection=$collectionName, id=$id, document=$document]")
    tryIt(collection.flatMap(_.update(DBQueryBuilder.id(id), document)))
  }

  /**
   *
   * @param id
   * @param query
   * @return - Future Try[Int]
   */
  def update(id: Option[String], query: BSONDocument): Future[Try[Int]] = {
    logger.debug(s"Updating by query: [collection=$collectionName, id=$id, query=$query]")
    tryIt(collection.flatMap(_.update(DBQueryBuilder.id(id), query)))
  }

  /**
   * Push a field in the document
   *
   * @param id - String id of the document
   * @param field - field in the document
   * @param data - field data
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @tparam S
   * @return - Future Try[Int]
   */
  def push[S](id: Option[String], field: String, data: S)(implicit writer: BSONDocumentWriter[S]): Future[Try[Int]] = {
    logger.debug(s"Pushing to document: [collection=$collectionName, id=$id, field=$field data=$data]")
    tryIt(collection.flatMap(_.update(DBQueryBuilder.id(id), DBQueryBuilder.push(field, data))))
  }

  /**
   * Pull a field in the document
   *
   * @param id - String id of the document
   * @param field - field in the document to pull
   * @param data - field data
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @tparam S
   * @return - Future Try[Int]
   */
  def pull[S](id: Option[String], field: String, data: S)(implicit writer: BSONDocumentWriter[S]): Future[Try[Int]] = {
    logger.debug(s"Pulling from document: [collection=$collectionName, id=$id, field=$field query=$data]")
    tryIt(collection.flatMap(_.update(DBQueryBuilder.id(id), DBQueryBuilder.pull(field, data))))
  }

  /**
   * unset the field on the document with id
   *
   * @param id - id of the document to unset the field
   * @param field - the field to be unset
   * @return
   */
  def unset(id: Option[String], field: String): Future[Try[Int]] = {
    logger.debug(s"Unsetting from document: [collection=$collectionName, id=$id, field=$field]")
    tryIt(collection.flatMap(_.update(DBQueryBuilder.id(id), DBQueryBuilder.unset(field))))
  }

  /**
   * Remove documents based on information passed in on the query string
   *
   * @param filter - a regex pattern to match documents
   * @param params - parameters to match documents
   * @param daoData - DaoSpecific data that must be passed along. Defined on the companion object for T
   * @param tag - the scala type tag for the attribute
   * @return - Future Try[Int]
   */
  def remove(filter: Option[String], params: Map[String, String])(implicit daoData:DaoData[T], tag : TypeTag[T] ): Future[Try[Int]] = {
    val queryDoc = buildQueryDocument(filter, params, daoData)
    if (queryDoc.isEmpty)
      Future {
        Failure(new IllegalArgumentException("Arguments must be provided for a delete operation"))
      }
    else
      remove(queryDoc)
  }

  /**
   * Removes a document from mongo by its String id
   *
   * @param id - String id
   * @return - Future Try[Int]
   */
  def remove(id: Option[String]): Future[Try[Int]] = remove(DBQueryBuilder.id(id))

  /**
   * Removes a document from mongo by its BSONObjectID
   *
   * @param id - String id
   * @return - Future Try[Int]
   */
  def remove(id: BSONObjectID): Future[Try[Int]] = {
    logger.debug(s"Removing document: [collection=$collectionName, id=$id]")
    tryIt(collection.flatMap(_.remove(DBQueryBuilder.id(id))))
  }

  /**
   * Removes all documents that match the query.
   *
   * @param query - A BSONDocument containg the query to match against.
   * @param firstMatchOnly - boolean to delete just the first match or all matches
   * @return - Future Try[Int]
   */
  def remove(query: BSONDocument, firstMatchOnly: Boolean = false): Future[Try[Int]] = {
    logger.debug(s"Removing document(s): [collection=$collectionName, firstMatchOnly=$firstMatchOnly, query=${BSONDocument.pretty(query)}]")
    tryIt(collection.flatMap(_.remove(query, firstMatchOnly = firstMatchOnly)))
  }

  /**
   * Removes all the documents in a collection
   *
   * @return - Future Try[Int]
   */
  def removeAll(): Future[Try[Int]] = remove(BSONDocument())

  /**
   * Execute a function that returns a Future[WriteResult] and return a Future[Try[Int]]. The Int that is returned
   * is the number of documents, records, etc that were effected by the operation.
   *
   * @param operation - a function that returns a Future[WriteResult]
   * @return - a Future Try[Int].
   */
  // TODO: Need to replace WriteResult with something that is not Mongo specific
  def tryIt(operation: Future[WriteResult]): Future[Try[Int]] = operation.map {
    writeResult =>
      writeResult.writeErrors.isEmpty match {
        case true => Success(writeResult.n)
        case false => Failure(new Exception(writeResult.writeErrors.foldLeft(""){(s:String,w:WriteError)=> s+";"+w.errmsg}))
      }
  } recover {
    case throwable => Failure(throwable)
  }

  /**
   * Execute a function that returns a Future[MultiBulkWriteResult] and return a Future[Try[Int]]. The Int that is returned
   * is the number of documents, records, etc that were effected by the operation.
   *
   * @param operation - a function that returns a Future[MultiBulkWriteResult]
   * @return - a Future Try[Int].
   */
  def multiTryIt(operation: Future[MultiBulkWriteResult]): Future[Try[Int]] = operation.map {
    writeResult =>
      writeResult.errmsg match {
        case Some(msg) => Failure(new Exception(msg))
        case None => Success(writeResult.totalN)
      }
  } recover {
    case throwable => Failure(throwable)
  }

}
