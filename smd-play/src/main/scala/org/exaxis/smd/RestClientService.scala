package org.exaxis.smd

import play.api.Logger
import play.api.i18n.{Lang, MessagesApi}
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.{WSClient, WSRequest}
import play.mvc.Http

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by dberry on 16/3/14.
  */
abstract class RestClientService[T <: Identifiable] {
  implicit val ws:WSClient
  implicit val lang:Lang
  implicit val messagesApi:MessagesApi
  implicit val executionContext:ExecutionContext

  val uriKey:String

  def create(identifiable : T)(implicit fmt:Format[T]) : Future[Try[T]] = {
    val uriFormat = "%s"
    executeWS[T](uriFormat, uriKey){holder => holder.post(Json.toJson(identifiable)).map { response =>
      response.status match {
        case Http.Status.CREATED => Success(identifiable)
        case _ => Logger.error(s"create failed for $uriFormat for $uriKey")
          Failure(new Exception(response.statusText+":"+response.status))
      }
    }
    }
  }

  def update(identifiable : T)(implicit fmt:Format[T]) : Future[Try[T]] = {
    val uriFormat = "%s"
    executeWS[T](uriFormat, uriKey){holder => holder.put(Json.toJson(identifiable)).map { response =>
      response.status match {
        case Http.Status.ACCEPTED => Success(identifiable)
        case _ => Logger.error(s"update failed for $uriFormat for $uriKey")
          Failure(new Exception(response.statusText+":"+response.status))
      }
    }
    }
  }

  def delete(identifiable : T) : Future[Try[T]] = {
    val uriFormat = "%s/"+identifiable.id
    executeWS[T](uriFormat, uriKey){holder => holder.delete.map { response =>
      response.status match {
        case Http.Status.OK => Success(identifiable)
        case _ => Logger.error(s"delete failed for $uriFormat for $uriKey")
          Failure(new Exception(response.statusText+":"+response.status))
      }
    }
    }
  }

  def delete(id : String) : Future[Try[String]] = {
    val uriFormat = "%s/"+id
    executeWS[String](uriFormat, uriKey){holder => holder.delete.map { response =>
      response.status match {
        case Http.Status.OK => Success(id)
        case _ => Logger.error(s"delete failed for $uriFormat for $uriKey")
          Failure(new Exception(response.statusText+":"+response.status))
      }
    }
    }
  }

  def delete(filter:Option[String]=None, attributeMap: Map[String,Any]=Map[String,Any]()) : Future[Try[Int]] = {
    val uriFormat = "%s"
    executeWS[Int](uriFormat, uriKey, buildQueryParams( filter, attributeMap)){holder => holder.delete.map { response =>
      response.status match {
        case Http.Status.OK => Success(response.body.toInt)
        case _ => Logger.error(s"delete failed for $uriFormat for $uriKey")
          Failure(new Exception(response.statusText+":"+response.status))
      }
    }
    }
  }

  def get(identifiable : T)(implicit fmt:Format[T])  : Future[Try[T]] = get(identifiable.id)

  def get(id:Option[String])(implicit fmt:Format[T]) : Future[Try[T]] = id match {
    case None => Future(Failure(new IllegalArgumentException("invalid id")))
    case Some(s) =>  {
      val uriFormat = "%s/"+id.getOrElse("")
      executeWS[T](uriFormat, uriKey){holder => holder.get.map { response =>
        Logger.debug(response.body)
        response.status match {
          case Http.Status.OK => Success(response.json.validate[T].get)
          case _ => Logger.error(s"get failed for $uriFormat for $uriKey")
            Failure(new Exception(response.statusText+":"+response.status))
        }
      }
      }
    }
  }

  def get(id1:String, id2:String)(implicit fmt:Format[T]) : Future[Try[T]] = {
    val uriFormat = s"%s/$id1/$id2"
    executeWS[T](uriFormat, uriKey){holder => holder.get.map { response =>
      Logger.debug(response.body)
      response.status match {
        case Http.Status.OK => Success(response.json.validate[T].get)
        case _ => Logger.error(s"get failed for $uriFormat for $uriKey")
          Failure(new Exception(response.statusText+":"+response.status))
      }
    }
    }
  }


  def get(attributes:List[(String, String)])(implicit fmt:Format[T]) : Future[Try[T]] = {
    val uriFormat = "%s/alt"
    executeWS[T](uriFormat, uriKey, attributes){holder => holder.get.map { response =>
      Logger.debug(response.body)
      response.status match {
        case Http.Status.OK => Success(response.json.validate[T].get)
        case _ => Logger.error(s"get failed for $uriFormat for $uriKey")
          Failure(new Exception(response.statusText+":"+response.status))
      }
    }
    }
  }


  def list(filter:Option[String]=None, attributeMap: Map[String,Any]=Map[String,Any](), sortMap: Map[String,Int]=Map[String,Int](), page:Int, ipp:Int)(implicit fmt:Format[T]) : Future[Try[( Pagination, List[T])]] = {
    val uriFormat = "%s"
    Logger.debug("In RestService.list")
    executeWS[(Pagination,List[T])](uriFormat, uriKey, buildQueryParams( filter, attributeMap, sortMap, page, ipp )){holder => holder.get.map { response =>
      response.status match {
        case Http.Status.OK => Logger.debug("Got Okay Status from list")
          val page = (response.json \ "page").as[Pagination]
          val items = (response.json \ "items").as[List[T]]
          Logger.debug(s"Total Pages ${page.totalPages}")

          Success((page,items))
        case _ => val msg = response.statusText+":"+response.status
          Logger.error(msg)
          Failure(new Exception(msg))
      }
    }
    }
  }

  private def executeWS[S](uriFormat:String,uriKey:String, queryParams:List[(String, String)]=List()) (holderProcessor : (WSRequest => Future[Try[S]])) = {
    Logger.debug("In executeWS: serviceUriKey ="+uriKey)
    val uriValue = messagesApi(uriKey)
    val fullUri = uriFormat.format(uriValue)
    Logger.debug(s"Calling HolderProcessor for uri $fullUri")
    holderProcessor(ws.url(fullUri).withQueryStringParameters(queryParams: _*))
  }

  private def buildQueryParams( filter:Option[String], attributeMap: Map[String,Any], sortMap: Map[String,Int], page:Int, ipp:Int ) : List[(String,String)] = {
    Logger.debug("In buildQueryParams")
    ("p",page.toString) :: ("ipp", ipp.toString) :: (for { key <- attributeMap.keys } yield (key,attributeMap.get(key).get.toString)).toList ++ sort2String(sortMap) ++ (filter match {
      case Some(s) => List(("q",s.toString))
      case None => Nil
    })
  }

  private def buildQueryParams( filter:Option[String], attributeMap: Map[String,Any]) : List[(String,String)] = {
    (for { key <- attributeMap.keys } yield (key,attributeMap.get(key).get.toString)).toList ++ (filter match {
      case Some(s) => List(("q",s.toString))
      case None => Nil
    })
  }

  private def sort2String(sortMap:Map[String, Int]) : List[(String,String)] = {
    val param = (for { key <- sortMap.keys } yield key+" "+sortMap.get(key).get.toString).mkString("",",","")
    if (param.isEmpty) List() else List(("s",param))
  }

}
