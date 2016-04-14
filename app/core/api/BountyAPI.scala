package core.api

import java.util.Date

import com.lvxingpai.model.account.RealNameInfo
import com.lvxingpai.model.geo.Locality
import com.lvxingpai.model.marketplace.order.Bounty
import com.lvxingpai.model.marketplace.product.Schedule
import com.lvxingpai.model.marketplace.seller.Seller
import com.lvxingpai.yunkai.{ UserInfo => YunkaiUser }
import core.exception.ResourceNotFoundException
import core.payment.PaymentService
import org.joda.time.DateTime
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.UpdateResults

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

/**
 *
 * Created by topy on 2016/3/29.
 */
object BountyAPI {

  /**
   * 根据Id取得悬赏信息
   *
   * @param bountyId
   * @param fields
   * @param ds
   * @return
   */
  def getBounty(bountyId: Long, fields: Seq[String])(implicit ds: Datastore): Future[Option[Bounty]] = {
    Future {
      Option(ds.find(classOf[Bounty], "itemId", bountyId).retrievedFields(true, fields: _*).get)
    }
  }

  def getBounty(bountyId: Long, fields: Seq[String], flag: Option[Boolean])(implicit ds: Datastore): Future[Option[Bounty]] = {
    Future {
      Option(ds.find(classOf[Bounty], "itemId", bountyId).retrievedFields(flag.getOrElse(true), fields: _*).get)
    }
  }

  /**
   * 根据订单id查询订单信息. 和getOrder不同的是, 如果无法查找到对应的记录, 该方法会抛出异常
   * @param bountyId 订单id
   * @return
   */
  def fetchBounty(bountyId: Long)(implicit ds: Datastore): Future[Bounty] = {
    getBounty(bountyId, Seq("schedules"), Some(false)) map (_ getOrElse {
      throw ResourceNotFoundException(s"Cannot find order #$bountyId")
    })
  }

  /**
   * 用户创建悬赏
   *
   * @param userId
   * @param contact
   * @param destination
   * @param departure
   * @param departureDate
   * @param timeCost
   * @param participantCnt
   * @param perBudget
   * @param participants
   * @param service
   * @param topic
   * @param memo
   * @param bountyPrice
   * @param ds
   * @return
   */
  def createBounty(userId: Long, contact: RealNameInfo, destination: Seq[Locality], departure: Locality, departureDate: Date, timeCost: Int,
    participantCnt: Int, perBudget: Int, participants: Seq[String], service: String, topic: String, memo: String, bountyPrice: Int)(implicit ds: Datastore): Future[Bounty] = {
    val bounty = new Bounty()
    val now = DateTime.now().toDate
    bounty.itemId = now.getTime
    bounty.createTime = now
    bounty.updateTime = now
    bounty.contact = contact
    bounty.departure = departure
    bounty.departureDate = departureDate
    bounty.timeCost = timeCost
    bounty.participants = participants
    bounty.budget = perBudget
    bounty.destination = destination
    bounty.service = service
    bounty.topic = topic
    bounty.memo = memo
    bounty.consumerId = userId
    bounty.bountyPrice = bountyPrice
    bounty.status = "pub"
    Future {
      ds.save[Bounty](bounty)
      bounty
    }
  }

  /**
   * 创建基于悬赏的订单
   *
   * @param bountyId
   * @param scheduleId
   * @param ds
   * @return
   */
  def orderBounty(bountyId: Long, scheduleId: Long)(implicit ds: Datastore): Future[UpdateResults] = {
    val future = for {
      bounty <- BountyAPI.getBounty(bountyId, Seq("schedules"))
    } yield {
      if (bounty.isEmpty)
        throw ResourceNotFoundException(s"Cannot find bounty.ItemId:" + bountyId)
      val scheduledOp = bounty.get.schedules.find(sc => {
        sc.itemId == scheduleId
      })
      val scheduled = scheduledOp match {
        case None => throw ResourceNotFoundException(s"Cannot find schedule.ItemId:" + scheduleId)
        case _ => scheduledOp.get
      }
      val statusQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId
      val statusOps = ds.createUpdateOperations(classOf[Bounty]).set("scheduled", scheduled).set("totalPrice", scheduled.price)
      ds.update(statusQuery, statusOps)
    }
    future
  }

  /**
   * 取得某用户的悬赏列表
   *
   * @param userId
   * @param ds
   * @return
   */
  def getBounties(userId: Option[Long])(implicit ds: Datastore): Future[Seq[Bounty]] = {
    Future {
      val query = ds.createQuery(classOf[Bounty])
      if (userId.nonEmpty)
        query.field("consumerId").equal(userId.get)
      query.or(
        query.criteria("paid").equal(true),
        query.criteria("totalPrice").equal(0)
      )
      query.asList()
    }
  }

  /**
   * 取得某个悬赏的行程方案列表
   *
   * @param bountyId
   * @param ds
   * @return
   */
  def getSchedule(bountyId: Long)(implicit ds: Datastore): Future[Seq[Schedule]] = {
    Future {
      val bounty = Option(ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId get)
      bounty match {
        case None => Seq()
        case x => x.get.schedules
      }
    }
  }

  /**
   * 商家根据某个悬赏，发布行程安排来应征
   *
   * @param bountyId
   * @param seller
   * @param desc
   * @param price
   * @param ds
   * @return
   */

  def addSchedule(bountyId: Long, seller: Option[Seller], desc: String, price: Int)(implicit ds: Datastore): Future[Unit] = {
    if (seller.isEmpty)
      throw ResourceNotFoundException(s"Cannot find seller.")
    Future {
      val sc = new Schedule
      val now = DateTime.now().toDate
      sc.desc = desc
      sc.createTime = now
      sc.updateTime = now
      sc.price = price
      sc.seller = seller.get
      sc.itemId = now.getTime
      sc.title = "行程安排"
      sc.bountyId = bountyId
      sc.status = "pub"
      val statusQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId
      val statusOps = ds.createUpdateOperations(classOf[Bounty]).add("schedules", sc)
      ds.update(statusQuery, statusOps)
    }
  }

  /**
   * 商家接单
   *
   * @param bountyId
   * @param seller
   * @param ds
   * @return
   */
  def addTakers(bountyId: Long, seller: Option[Seller])(implicit ds: Datastore): Future[Unit] = {
    if (seller.isEmpty)
      throw ResourceNotFoundException(s"Cannot find seller.")
    Future {
      val statusQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId
      val statusOps = ds.createUpdateOperations(classOf[Bounty]).add("takers", seller.get.userInfo)
      ds.update(statusQuery, statusOps)
    }
  }

  /**
   * 将某个悬赏的订金设置为已支付
   *
   * @param bountyId 订单号
   * @param provider 支付渠道
   */
  def setBountyPaid(bountyId: Long, provider: PaymentService.Provider.Value)(implicit ds: Datastore): Future[Unit] = {
    val providerName = provider.toString

    // 设置payment状态
    val paymentQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId field
      s"paymentInfo.$providerName" notEqual null
    val paymentOps = ds.createUpdateOperations(classOf[Bounty]).set(s"paymentInfo.$providerName.paid", true).set("bountyPaid", true)

    val ret: Future[UpdateResults] = Future {
      ds.update(paymentQuery, paymentOps)
    }
    ret map (_ => ())
  }

  /**
   * 支付赏金订单
   * @return
   */
  def payBounty(bounty: Bounty, provider: PaymentService.Provider.Value)(implicit ds: Datastore): Future[Bounty] = {
    import PaymentService.Provider._
    Future {
      // 设置支付状态
      provider match {
        case Alipay =>
          bounty.paymentInfo(Alipay.toString).paid = true
        case WeChat =>
          bounty.paymentInfo(WeChat.toString).paid = true
      }
      bounty.bountyPaid = true
      ds.save[Bounty](bounty)
      bounty
    }
  }
}
