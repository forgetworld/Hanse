package core.api

import core.model.account.UserInfo
import core.model.trade.order.Person
import org.bson.types.ObjectId
import org.mongodb.morphia.Datastore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by pengyt on 2015/11/16.
 */
object TravellerAPI {

  /**
   * 添加旅客信息
   * @param userId 用户id
   * @param person 旅客信息
   * @return 旅客键值和旅客信息
   */
  def addTraveller(userId: Long, person: Person)(implicit ds: Datastore): Future[(String, Person)] = {
    val query = ds.createQuery(classOf[UserInfo])
    val key = new ObjectId().toString

    val ops = ds.createUpdateOperations(classOf[UserInfo]).add("travellers", key -> person, false)
    Future {
      ds.findAndModify(query, ops, false, true)
      key -> person
    }
  }

  /**
   * 修改旅客信息
   * @param userId 用户id
   * @param person 旅客信息
   * @return 旅客键值和旅客信息
   */
  def updateTraveller(userId: Long, key: String, person: Person)(implicit ds: Datastore): Future[(String, Person)] = {

    val query = ds.createQuery(classOf[UserInfo]).field("userId").equal(userId)
    val ops = ds.createUpdateOperations(classOf[UserInfo]).set(s"travellers.$key", person)
    Future {
      ds.updateFirst(query, ops)
      key -> person
    }
  }

  /**
   * 删除旅客信息
   * @param userId 用户id
   * @param key 旅客信息键值
   * @return 空
   */
  def deleteTraveller(userId: Long, key: String)(implicit ds: Datastore): Future[Unit] = {

    val query = ds.createQuery(classOf[UserInfo])
    val opsRm = ds.createUpdateOperations(classOf[UserInfo]).unset(s"travellers.$key")
    Future {
      ds.updateFirst(query, opsRm)
    }
  }

  /**
   * 根据用户id和旅客键值取得旅客信息
   * @param userId 用户id
   * @param key 旅客信息键值
   * @return 旅客信息
   */
  def getTraveller(userId: Long, key: String)(implicit ds: Datastore): Future[Person] = {

    val query = ds.createQuery(classOf[UserInfo]).field("userId").equal(userId)

    Future {
      query.get().travellers(key)
    }
  }

  /**
   * 根据用户id取得所有旅客信息
   * @param userId 用户id
   * @return 旅客信息列表
   */
  def getTravellerList(userId: Long)(implicit ds: Datastore): Future[Map[String, Person]] = {

    val query = ds.createQuery(classOf[UserInfo]).field("userId").equal(userId)
    Future {
      query.get.travellers
    }
  }
}