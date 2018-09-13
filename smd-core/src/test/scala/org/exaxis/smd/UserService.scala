package org.exaxis.smd

import reactivemongo.api.DefaultDB
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}


/**
  * An implementation of an IdentifiableDAO. It is responsible for associating the case class to its collection in the
  * mongodb
  *
  * @param injector
  */
class UserService(implicit val injector: Injector) extends IdentifiableDAO[User] with Injectable {
  implicit val executionContext = inject [ExecutionContext]
  val defaultDBFuture = inject [Future[DefaultDB]]
  val collectionName = "myusers"
}
