package core.misc

import com.lvxingpai.model.account.UserInfo
import com.lvxingpai.model.geo.Locality
import com.lvxingpai.model.misc.{ ImageItem, PhoneNumber }
import com.lvxingpai.yunkai.{ UserInfo => YunkaiUser }
import com.twitter.{ util => twitter }
import org.bson.types.ObjectId
import org.mongodb.morphia.annotations.Entity
import play.api.libs.json.Json

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.language.implicitConversions
import scala.util.{ Failure, Success, Try }
import scala.xml.{ Elem, Node, NodeSeq }

/**
 * Created by zephyre on 7/10/15.
 */
object Implicits {

  implicit def long2String(v: Long): String = v.toString

  implicit def int2String(v: Int): String = v.toString

  implicit def float2String(v: Float): String = v.toString

  implicit def Node2String(body: Node): String = {
    body match {
      case NodeSeq.Empty => ""
      case _ => body.toString()
    }
  }

  implicit def yunkaiUser2UserInfo(u: YunkaiUser): UserInfo = {
    val userInfo = new UserInfo
    userInfo.userId = u.userId
    val i = new ImageItem
    i.url = u.avatar getOrElse ""
    userInfo.avatar = i
    userInfo.nickname = u.nickName
    userInfo
  }

  @Entity(noClassnameStored = true)
  case class PhoneNumberTemp(dialCode: Int, number: Long) {
    def toPhoneNumber = {
      val ret = new PhoneNumber
      ret.dialCode = dialCode
      ret.number = number
      ret
    }
  }

  implicit val phoneNumberReads = Json.reads[PhoneNumberTemp]

  implicit def phoneNumberTemp2Model(pt: PhoneNumberTemp): PhoneNumber = {
    val ret: PhoneNumber = new PhoneNumber()
    ret.dialCode = pt.dialCode
    ret.number = pt.number
    ret
  }

  @Entity(noClassnameStored = true)
  case class ImageItemTemp(url: String) {
    def toImageItem = {
      val ret = new ImageItem
      ret.url = url
      ret
    }
  }

  implicit val imageItemReads = Json.reads[ImageItemTemp]

  implicit def imageItemTemp2Model(pt: Option[Array[ImageItemTemp]]): Option[Seq[ImageItem]] = {
    if (pt.nonEmpty) {
      val ret = pt.get.map(x => {
        val i = new ImageItem
        i.url = x.url
        i
      })
      Option(ret)
    } else None
  }

  @Entity(noClassnameStored = true)
  case class TempLocality(id: String, zhName: String)

  implicit val localityReads = Json.reads[TempLocality]

  implicit def locality2Model(locality: TempLocality): Locality = {
    val l: Locality = new Locality()
    l.id = new ObjectId(locality.id)
    l.zhName = locality.zhName
    l
  }

  implicit def localities2ArrayModel(localities: Seq[TempLocality]): Seq[Locality] = localities map locality2Model

  implicit def NodeSeq2String(body: NodeSeq): String = {
    body match {
      case NodeSeq.Empty => ""
      case _ => body.toString()
    }
  }

  implicit def NodeSeq2Int(body: NodeSeq): Int = {
    body match {
      case NodeSeq.Empty => 0
      case _ => body.toString().toInt
    }
  }

  implicit class ElemChild(ns: NodeSeq) {
    def \* = ns flatMap {
      case e: Elem => e.child
      case _ => NodeSeq.Empty
    }
  }

  object TwitterConverter {
    implicit def scalaToTwitterTry[T](t: Try[T]): twitter.Try[T] = t match {
      case Success(r) => twitter.Return(r)
      case Failure(ex) => twitter.Throw(ex)
    }

    implicit def twitterToScalaTry[T](t: twitter.Try[T]): Try[T] = t match {
      case twitter.Return(r) => Success(r)
      case twitter.Throw(ex) => Failure(ex)
    }

    implicit def scalaToTwitterFuture[T](f: Future[T])(implicit ec: ExecutionContext): twitter.Future[T] = {
      val promise = twitter.Promise[T]()
      f.onComplete(promise update _)
      promise
    }

    implicit def twitterToScalaFuture[T](f: twitter.Future[T]): Future[T] = {
      val promise = Promise[T]()
      f.respond(promise complete _)
      promise.future
    }
  }

}
