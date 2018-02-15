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

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe.TypeTag
import scala.util.{Failure, Success}
import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter}
import play.api.mvc.{Action, Controller}
import play.api.libs.json.{Format,Json}
import play.api.Logger


/**
  * Created by dberry on 11/3/14.
 *
 * DAOController is a generic Controller that provides all the CRUD services for a domain object. The rest endpoints can call these methods to have
 * data sent to or retrieved from mongo collecions.
 *
 * Type param T must extend identifiable in order to be used by DAOController. Identifiable gives the class an id field that will be used as the key for
 * the CRUD Operations
 *
 */
abstract class MongoDaoController[T <: Identifiable] extends Controller {
  implicit val executionContext: ExecutionContext
  val dao:IdentifiableDAO[T]

  val defaultOffset = 0
  val defaultLimit = 25

  /**
   * Creates a T in a mongo collection.
   *
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @param fmt - The Json.format[T] on the companion object for T
   * @return - Action[JsValue]
   */
  def create()(implicit writer: BSONDocumentWriter[T], fmt:Format[T] ) = Action.async(parse.json) { implicit request =>
    // process the json body
    request.body.validate[T].map { instance =>
      dao.insert(instance).map {
        case Failure(t) => BadRequest(t.getLocalizedMessage)
        case Success(count) => if (count == 1) Created else BadRequest
      }
    }.getOrElse(Future.successful(BadRequest("invalid json")))
  }

  /**
   * Gets a T from the mongo collection using its id.
   *
   * @param id - id of the T to be retrieved
   * @param reader - The BSONDocumentReader on the companion object for T
   * @param fmt - The Json.format[T] on the companion object for T
   * @return - Action[AnyContent]
   */
  def getById(id:String)(implicit reader: BSONDocumentReader[T], fmt:Format[T]) = Action.async {
    dao.findById(Some(id)).map {
      case None => NotFound
      case Some(doc) => Ok(Json.toJson(doc))
    }.recover {
      case e: Exception => NotFound
    }
   }

  /**
   * Gets a T from the mongo collection using the url parameters as the keys
   *
   * @param reader - The BSONDocumentReader on the companion object for T
   * @param daoData - DaoSpecific data that must be passed along. Defined on the companion object for T
   * @param tag - Reflection info for T
   * @return - Action[AnyContent]
   */
  def getByAlt()(implicit reader: BSONDocumentReader[T], fmt:Format[T], daoData:DaoData[T], tag : TypeTag[T] ) = Action.async { request =>
    val paramMap = request.queryString.map {
          case (k, v) => Logger.debug(k+"->"+v); k -> v.mkString
    }
    dao.findOne(paramMap).map {
      case None => Logger.debug("Could not find by alternate attributes"); NotFound
      case Some(doc) => Ok(Json.toJson(doc))
    }.recover {
      case e: Exception => Logger.error("Errored out: "+e.getLocalizedMessage); NotFound
    }
   }

  /**
   * Updates a T based on its id. It retrieves the T from the request object.
   *
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @param fmt - The Json.format[T] on the companion object for T
   * @return - Action[JsValue]
   */
  def update()(implicit writer: BSONDocumentWriter[T], fmt:Format[T]) = Action.async(parse.json) { request =>
      // process the json body
      request.body.validate[T].map { instance =>
        dao.update(instance).map {
          case Failure(t) => BadRequest(t.getLocalizedMessage)
          case Success(count) => if (count > 0) Accepted else NotFound
        }
      }.getOrElse(Future.successful(BadRequest("invalid json")))
    }

  /**
   * Deletes a T based on its id.
   *
   * @param id - id of the T to be deleted
   * @return - Action[AnyContent] which is a OK or NOT FOUND
   */
  def delete(id:String) = Action.async {
    dao.remove(Some(id)).map {
      case Failure(t) => BadRequest(t.getLocalizedMessage)
      case Success(count) => if (count > 0) Ok else NotFound
    }
  }

  /**
   * Deletes documents where the query matches data in the filter set
   *
   * @param q - A query that gets turned into a regex, and searches all fields listed in the DAOs Filter Set
   * @param daoData - DaoSpecific data that must be passed along. Defined on the companion object for T
   * @param tag - Reflection info for T
   * @return - Action[AnyContent] containingg the count of documents deleted or an error message
   */
  def deleteList(q: Option[String])(implicit daoData:DaoData[T], tag : TypeTag[T] ) = Action.async { request =>
    val paramMap = request.queryString.map {
      case (k, v) => Logger.debug(k+"->"+v); k -> v.mkString
    } - "q"

    dao.remove(q, paramMap).map {
      case Failure(t) => BadRequest(t.getLocalizedMessage)
      case Success(count) => Ok(count.toString)
    }
  }

  /**
   * Retrieves a single page list of T.
   *
   * @param p - The page that is being requested.
   * @param ipp - Items per page
   * @param q - A query that gets turned into a regex, .*q.* this gets used from search pages, and searches all fields listed in the DAOs Filter Set
   * @param s - This is the sort order. It is in the format of field1,field2 1, field3 -1. Which is field1 asc, field2 desc, field3 asc
   * @param reader - The BSONDocumentReader on the companion object for T
   * @param fmt - The Json.format[T] on the companion object for T
   * @param pgfmt - The Json.format[Pagination] on the companion object for Pagination
   * @param daoData - DaoSpecific data that must be passed along. Defined on the companion object for T
   * @param tag - Reflection info for T
   * @return - Action[AnyContent]
   */
  def list(p:Int, ipp: Int, q: Option[String], s: Option[String])(implicit reader: BSONDocumentReader[T], fmt: Format[T], pgfmt: Format[Pagination], daoData:DaoData[T], tag : TypeTag[T] ) = Action.async {
    request => // get all the params that are not know params
      val paramMap = request.queryString.map {
        case (k, v) => Logger.debug(k+"->"+v); k -> v.mkString
      } - "q" - "s" - "p" - "ipp"

      Logger.debug("q = "+q)
      Logger.debug("s = "+s)
      Logger.debug("p = "+p)
      Logger.debug("ipp = "+ipp)

      dao.find(q, s, p, ipp, paramMap).map {
        docs =>
          Ok(Json.toJson(Map("page" -> Json.toJson(new Pagination(p,ipp,docs._2)), "items" -> Json.toJson(docs._1))))
      }
  }

}
