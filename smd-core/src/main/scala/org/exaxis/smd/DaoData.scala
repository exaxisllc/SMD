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

/**
 * A trait which contains DAO specific paramter information
 * @tparam T
 */
trait DaoData[T] {
  /**
   * defines the mapping of scala attributes to datastore attributes, same named attributes do not need to be mapped
   */
  val attributeMap:Map[String,String]
  /**
   * defines the attributes that will be matched against a query in the search.
   */
  val filterSet:Set[String]

  def dataSourceName(attrName:String) = attributeMap.getOrElse(attrName, attrName)
}


