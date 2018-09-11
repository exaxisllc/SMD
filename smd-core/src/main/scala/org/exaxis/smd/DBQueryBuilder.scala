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

import reactivemongo.bson._

import scala.util.{Success, Failure}
import com.typesafe.scalalogging.Logger

/* Implicits */

/**
 * Query builder wrapping common queries and MongoDB operators.
 *
 * TODO: create a real query `builder`
 *
 * @author      Pedro De Almeida (almeidap)
 */
object DBQueryBuilder {
	val logger = Logger("DBQueryBuilder")

	/**
	 * Convert a BSONObjectID to a BSONDocument containing the _id field name and the BSONObjectID.
	 *
	 * @param objectId - The BSONObjectID
	 * @return - a BSONDocument
	 */
	def id(objectId: BSONObjectID): BSONDocument = BSONDocument("_id" -> objectId)

	/**
	 * Convert an Option[String] to a BSONDocument containing the _id field name and the BSONObjectID.
	 * If the Option[String] is None it sets _id to BSONUndefined
	 *
	 * @param objectId
	 * @return - a BSONDocument
	 */
  def id(objectId: Option[String]): BSONDocument = objectId match {
     case None => BSONDocument("_id" -> BSONUndefined)
     case Some(s) => BSONObjectID.parse(s) match {
		 	case Success(success) => id(success)
			case Failure(failure) => logger.warn("BSONObjectID could not be parsed!! Defaulting to BSONUndefined!!")
				BSONDocument("_id" -> BSONUndefined)
		 }
   }

	/**
	 * Set a BSONDocument containing the field and BSONDocument data
	 *
	 * @param field  - The string name of the field to be set
	 * @param data - The BSONDocument data to be set for the specific field
	 * @return - a BSONDocument
	 */
	def set(field: String, data: BSONDocument): BSONDocument = set(BSONDocument(field -> data))

	/**
	 * Set a BSONDocument containing the field and T data
	 *
	 * @param field - The string name of the field to be set
	 * @param data - The T data to be set for the specific field
	 * @param writer - The BSONDocumentWriter for mashalling the T to a BSONDocument
	 * @tparam T - The type parameter T
	 * @return - a BSONDocument
	 */
	def set[T](field: String, data: T)(implicit writer: BSONDocumentWriter[T]): BSONDocument = set(BSONDocument(field -> data))

	/**
	 * Sets the data
	 *
	 * @param data - The T data to be set
	 * @return - a BSONDocument
	 */
	def set(data: BSONDocument): BSONDocument = BSONDocument("$set" -> data)

	/**
	 * Sets the data
	 *
	 * @param data - The T data to be set
	 * @param writer - The BSONDocumentWriter for mashalling the T to a BSONDocument
	 * @tparam T - The type parameter T
	 * @return - a BSONDocument
	 */
	def set[T](data: T)(implicit writer: BSONDocumentWriter[T]): BSONDocument = BSONDocument("$set" -> data)

	/**
	 * Push a BSONDocument containing the field and the data
	 *
	 * @param field - The string name of the field to push
	 * @param data - The T data to be assigned to the field to push
	 * @param writer - The BSONDocumentWriter for mashalling the T to a BSONDocument
	 * @tparam T - The type parameter T
	 * @return - a BSONDocument
	 */
	def push[T](field: String, data: T)(implicit writer: BSONDocumentWriter[T]): BSONDocument = BSONDocument("$push" -> BSONDocument(field -> data))

	/**
	 *
	 * @param field - The string name of the field to pull
	 * @param query
	 * @param writer - The BSONDocumentWriter for mashalling the T to a BSONDocument
	 * @tparam T - The type parameter T
	 * @return - a BSONDocument
	 */
	def pull[T](field: String, query: T)(implicit writer: BSONDocumentWriter[T]): BSONDocument = BSONDocument("$pull" -> BSONDocument(field -> query))

	/**
	 *
	 * @param field - The string name of the field to be unset
	 * @return - a BSONDocument
	 */
	def unset(field: String): BSONDocument = BSONDocument("$unset" -> BSONDocument(field -> 1))

	/**
	 * Increment a specific field by a specific amount
	 *
	 * @param field - The string name of the field to be incremented
	 * @param amount - The amount to add to the field
	 * @return - a BSONDocument
	 */
	def inc(field: String, amount: Int) = BSONDocument("$inc" -> BSONDocument(field -> amount))

}