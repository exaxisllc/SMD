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

import reactivemongo.api.commands.{UpdateWriteResult, Upserted}
import reactivemongo.bson._

import scala.concurrent.Future
import scala.util.Try

/**
 * IdentifiableDAO is a trait that adds methods to MongoDAO that work with Identifiable.
 *
 * It uses the id attribute of the Identifiable to simplifiy and execute methods that take an id.
 *
 * @author dberry
 */
trait IdentifiableDAO[T <: Identifiable] extends MongoDao[T] {

  /**
   * Update a T document in a mongo collection
   *
   * @param document - a T
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @return - Future[Try[Int] ]
   */
  def update(document: T)(implicit writer: BSONDocumentWriter[T]): Future[Try[UpdateWriteResult]] = super.update(document.id, document)

  /**
   * Update a list of T documents in a mongo collection
   *
   * @param documents - a list of T
   * @param writer - The BSONDocumentWriter on the companion object for T
   * @return - Future[Try[Int] ]
   */
  def update(documents: List[T])(implicit writer: BSONDocumentWriter[T]): Future[List[Try[UpdateWriteResult]]] = Future.sequence(documents.map{ document => super.update(document.id, document) })

  /**
   * Remove a T document in a mongo collection
   *
   * @param document - a T
   * @return - Future[Try[Int] ]
   */
  def remove(document: T): Future[Try[Int]] = super.remove(document.id)

  /**
   * Remove a list of T documents in a mongo collection
   *
   * @param documents - a list of T
   * @return - Future[Try[Int] ]
   */
  // TODO: look to make deletes faster by id $in list
  def remove(documents: List[T]): Future[List[Try[Int]]] = Future.sequence(documents.map{ document => super.remove(document.id) })

  /**
   * Find a T by its id
   *
   * @param document - a T
   * @param reader - The BSONDocumentReader on the companion object for T
   * @return - Future[Try[Int] ]
   */
  def findById(document: T)(implicit reader: BSONDocumentReader[T]): Future[Option[T]] = super.findById(document.id)

}
