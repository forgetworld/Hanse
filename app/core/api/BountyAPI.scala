package core.api

import java.util
import java.util.Date

import com.lvxingpai.model.account.{ RealNameInfo, UserInfo }
import com.lvxingpai.model.geo.Locality
import com.lvxingpai.model.guide.Guide
import com.lvxingpai.model.marketplace.order.Order.Status._
import com.lvxingpai.model.marketplace.order.{ Bounty, Prepay }
import com.lvxingpai.model.marketplace.product.Schedule
import com.lvxingpai.model.marketplace.seller.Seller
import com.lvxingpai.yunkai.{ UserInfo => YunkaiUser }
import core.exception.{ OrderStatusException, ResourceNotFoundException }
import core.formatter.marketplace.order.BountyFormatter
import core.payment.PaymentService.Provider._
import core.payment._
import core.service.ViaeGateway
import org.bson.types.ObjectId
import org.joda.time.DateTime
import org.mongodb.morphia.Datastore
import org.mongodb.morphia.query.UpdateResults
import play.api.Play
import play.api.Play.current

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

/**
 *
 * Created by topy on 2016/3/29.
 */
object BountyAPI {

  /**
   * 根据Id取得悬赏信息
   *
   * @param bountyId
   * @param ds
   * @return
   */
  def getBounty(bountyId: Long)(implicit ds: Datastore): Future[Option[Bounty]] = {
    Future {
      Option(ds.find(classOf[Bounty], "itemId", bountyId).get)
    }
  }

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
   * 取得服务过的用户数量
   *
   * @param ds
   * @return
   */
  def getBountyCnt()(implicit ds: Datastore): Future[Int] = {
    Future {
      val bounties = ds.find(classOf[Bounty]).retrievedFields(true, Seq("takers"): _*).asList()
      // keven说这个数不能小于100 20160512
      bounties.map(_.schedules).size + 100
    }
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
  def createBounty(userId: Long, userInfo: UserInfo, contact: RealNameInfo, destination: Seq[Locality], departure: Locality, departureDate: Date, timeCost: Int,
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
    bounty.participantCnt = participantCnt
    bounty.budget = perBudget
    bounty.destination = destination
    bounty.service = service
    bounty.topic = topic
    bounty.memo = memo
    bounty.consumerId = userId
    bounty.consumer = userInfo
    bounty.bountyPrice = bountyPrice
    bounty.status = "pending"
    val future = Future {
      ds.save[Bounty](bounty)
      bounty
    }

    for {
      bounty <- future
      sellerIdList <- SellerAPI.getSellers(bounty.destination.map(_.id))
    } yield {
      val viae = Play.application.injector instanceOf classOf[ViaeGateway]
      val bountyNode = BountyFormatter.instance.formatJsonNode(bounty)
      // 发消息，发布悬赏
      if (bounty.bountyPaid)
        viae.sendTask("viae.event.marketplace.onPubBountyPaid", kwargs = Some(Map("bounty" -> bountyNode, "sellers" -> sellerIdList)))
      else
        viae.sendTask("viae.event.marketplace.onPubBountyUnPaid", kwargs = Some(Map("bounty" -> bountyNode, "sellers" -> sellerIdList)))
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
      if (Option(bounty.get.schedules).isEmpty)
        throw ResourceNotFoundException(s"Cannot find schedules in this bounty.ItemId:" + bountyId)
      val scheduled = bounty.get.schedules.find(sc => {
        sc.itemId == scheduleId
      }) match {
        case None => throw ResourceNotFoundException(s"Cannot find schedule.ItemId:" + scheduleId)
        case x => x.get
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
  def getBounties(userId: Option[Long], sortBy: String, sort: String, start: Int, count: Int)(implicit ds: Datastore): Future[Seq[Bounty]] = {
    Future {
      val query = ds.createQuery(classOf[Bounty])
      if (userId.nonEmpty)
        // 取得某用户的悬赏
        query.field("consumerId").equal(userId.get)
      else {
        // 取得发布在首页的悬赏
        query.or(
          query.criteria("bountyPaid").equal(true),
          query.criteria("bountyPrice").equal(0)
        )
        query.field("status").equal("pending")
      }
      val orderStr = if (sort.equals("asc")) sortBy else s"-$sortBy"
      query.order(orderStr).offset(start).limit(count)
      query.retrievedFields(false, Seq("schedules"): _*)
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
  def getSchedules(bountyId: Long)(implicit ds: Datastore): Future[Seq[Schedule]] = {
    Future {
      val bounty = Option(ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId get)
      bounty match {
        case None => Seq()
        case x => Option(x.get.schedules) map (_.toSeq) getOrElse Seq()
      }

    }
  }

  /**
   * 取得某个悬赏的行程方案详情
   *
   * @param bountyId
   * @param ds
   * @return
   */
  def getScheduleById(bountyId: Long, scheduleId: Long)(implicit ds: Datastore): Future[Schedule] = {
    Future {
      val bounty = Option(ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId retrievedFields (true, Seq("schedules"): _*) get)
      val ret: Seq[Schedule] = bounty match {
        case None => Seq()
        case x => Option(x.get.schedules) map (_.toSeq) getOrElse Seq()
      }
      ret filter (_.itemId == scheduleId) get 0
    }
  }

  /**
   * 取得方案详情
   * @param scheduleId
   * @param ds
   * @return
   */
  def getScheduleById(scheduleId: Long)(implicit ds: Datastore): Future[Schedule] = {
    Future {
      val ret = Option(ds.createQuery(classOf[Schedule]) field "itemId" equal scheduleId get)
      if (ret.isEmpty)
        throw ResourceNotFoundException(s"Cannot find scheduleId:" + scheduleId)
      else
        ret.get
    }
  }

  /**
   * 取得商家的方案
   *
   * @param sellerId
   * @param ds
   * @return
   */
  def getScheduleBySellerId(sellerId: Long, sortBy: String, sort: String, start: Int, count: Int)(implicit ds: Datastore): Future[Seq[Schedule]] = {
    Future {
      val query = ds.createQuery(classOf[Schedule]) field "seller.sellerId" equal sellerId
      val orderStr = if (sort.equals("asc")) sortBy else s"-$sortBy"
      query.order(orderStr).offset(start).limit(count)
      query.asList()
    }
  }

  def getBountyBySellerId(sellerId: Long, sortBy: String, sort: String, start: Int, count: Int)(implicit ds: Datastore): Future[Seq[Bounty]] = {
    val future = Future {
      val query = ds.createQuery(classOf[Schedule]) field "seller.sellerId" equal sellerId retrievedFields (true, Seq("bountyId"): _*)
      val orderStr = if (sort.equals("asc")) sortBy else s"-$sortBy"
      query.order(orderStr).offset(start).limit(count)
      Option(query.asList())
    }
    for {
      schedules <- future
    } yield {
      val ids = schedules map (_.toSeq) getOrElse Seq() map (_.bountyId)
      val query = ds.createQuery(classOf[Bounty])
      //field "itemId" in ids field "takers.userId" equal sellerId retrievedFields (false, Seq("schedules"): _*) asList ()
      query.or(
        query.criteria("itemId").in(ids),
        query.criteria("takers.userId").equal(sellerId)
      )
      query.retrievedFields(false, Seq("schedules"): _*) asList ()
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

  def addSchedule(bountyId: Long, seller: Option[Seller], guide: Option[Guide], userInfo: UserInfo, desc: String, price: Int)(implicit ds: Datastore): Future[Unit] = {
    if (seller.isEmpty)
      throw ResourceNotFoundException(s"Cannot find seller.")
    val sc = new Schedule
    val now = DateTime.now().toDate
    sc.desc = desc
    sc.createTime = now
    sc.updateTime = now
    sc.price = price

    val sellerDetail = seller.get
    sellerDetail.userInfo = userInfo
    sc.seller = sellerDetail
    sc.itemId = now.getTime
    sc.title = "行程安排"
    sc.bountyId = bountyId
    sc.status = "pub"
    if (guide.nonEmpty)
      sc.guide = guide.get

    val future = Future {
      val statusQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId
      val statusOps = ds.createUpdateOperations(classOf[Bounty]).add("schedules", sc)
      //ds.update(statusQuery, statusOps)
      ds.findAndModify(statusQuery, statusOps)
    }
    for {
      bounty <- future
    } yield {
      ds.save[Schedule](sc)
      val viae = Play.application.injector instanceOf classOf[ViaeGateway]
      val bountyNode = BountyFormatter.instance.formatJsonNode(bounty)
      // 发消息，已提交方案
      viae.sendTask("viae.event.marketplace.onBountyScheduled", kwargs = Some(Map("user" -> bounty.consumer.userId)))
    }
  }

  /**
   * 商家接单
   *
   * @param bountyId
   * @param ds
   * @return
   */
  def setTakers(bountyId: Long, userInfo: UserInfo)(implicit ds: Datastore): Future[Unit] = {
    Future {
      val statusQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId
      val statusOps = ds.createUpdateOperations(classOf[Bounty]).add("takers", userInfo)
      ds.update(statusQuery, statusOps)
      val viae = Play.application.injector instanceOf classOf[ViaeGateway]
      // 发消息，申请退款
      viae.sendTask("viae.event.marketplace.onBountyRefundApply")
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
   * 将某个悬赏的方案，设为已支付
   * @param bountyId
   * @param provider
   * @param ds
   * @return
   */
  def setSchedulePaid(bountyId: Long, provider: PaymentService.Provider.Value)(implicit ds: Datastore): Future[Unit] = {
    val providerName = provider.toString

    // 设置payment状态
    val paymentQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bountyId field
      s"scheduledPaymentInfo.$providerName" notEqual null
    val paymentOps = ds.createUpdateOperations(classOf[Bounty]).set(s"scheduledPaymentInfo.$providerName.paid", true).set("schedulePaid", true).set("status", "paid")

    val ret: Future[UpdateResults] = Future {
      ds.update(paymentQuery, paymentOps)
    }
    ret map (_ => ())
  }

  /**
   * 取得攻略信息
   *
   * @param id
   * @param ds
   * @return
   */
  def getGuide(id: String)(implicit ds: Datastore): Future[Option[Guide]] = {
    Future {
      (Try(new ObjectId(id)) map (oid => {
        val query = ds.createQuery(classOf[Guide]).field("_id").equal(oid)
          .retrievedFields(true, Seq("title", "images", "status", "summary", "updateTime"): _*)
        Option(query.get())
      })).toOption getOrElse None
    }
  }

  /**
   * 商家同意退款
   * @param operator
   * @param data
   * @return
   */
  def refundApprove(operator: Long, data: Option[Map[String, Any]], bounty: Bounty, target: String)(implicit ds: Datastore): Future[Unit] = {
    // 是否存在退款申请
    val withApplication = bounty.status == RefundApplied.toString

    val amount = data getOrElse Map() get "amount" flatMap (v => {
      (Try(Some(v.toString.toFloat)) recover {
        case _: NumberFormatException => None
      }).get
    }) map (v => {
      // 如果不存在退款申请，就属于商家直接退款，则必须全额退款
      if (!withApplication) bounty.totalPrice - bounty.discount else v.toInt
    }) getOrElse (bounty.totalPrice - bounty.discount) // 默认情况下, 退还全款

    val paymentInfo: util.Map[String, Prepay] = target match {
      case "bounty" => bounty.paymentInfo
      case "schedule" => bounty.scheduledPaymentInfo
      case _ => throw OrderStatusException("Target Info error.")
    }
    val providerName: String =
      if (Option(paymentInfo) map (_.toMap) getOrElse Map() contains WeChat.toString)
        WeChat.toString
      else
        Alipay.toString

    if (bounty.status == Pending.toString || bounty.status == Refunded.toString)
      throw OrderStatusException("Can not refund.")

    for {
      newOrder <- {
        if (providerName == WeChat.toString)
          BountyPayWeChat.instance.refundProcess(bounty, amount)
        else
          BountyPayAli.instance.refundProcess(bounty, amount)
      }
    } yield {
      val queryField = target match {
        case "bounty" => s"paymentInfo.$providerName"
        case "schedule" => s"scheduledPaymentInfo.$providerName"
      }
      // 设置payment状态
      val paymentQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bounty.itemId
      val paymentOps = ds.createUpdateOperations(classOf[Bounty]).set(s"$queryField.paid", true).set("status", Refunded.toString)
      ds.update(paymentQuery, paymentOps)
      val viae = Play.application.injector instanceOf classOf[ViaeGateway]
      val bountyNode = BountyFormatter.instance.formatJsonNode(bounty)
      // 发消息，同意退款
      viae.sendTask("viae.event.marketplace.onBountyRefundApprove", kwargs = Some(Map("bounty" -> bountyNode, "amount" -> (bounty.totalPrice - bounty.discount))))
      newOrder
    }
  }

  def refundApply(bounty: Bounty)(implicit ds: Datastore): Future[Unit] = {
    if (!bounty.bountyPaid && !bounty.schedulePaid)
      throw OrderStatusException("Can not refund apply.")
    Future {
      val paymentQuery = ds.createQuery(classOf[Bounty]) field "itemId" equal bounty.itemId
      val paymentOps = ds.createUpdateOperations(classOf[Bounty]).set("status", RefundApplied.toString)
      ds.update(paymentQuery, paymentOps)
      val viae = Play.application.injector instanceOf classOf[ViaeGateway]
      val bountyNode = BountyFormatter.instance.formatJsonNode(bounty)
      // 发消息，申请退款
      viae.sendTask("viae.event.marketplace.onBountyRefundApply", kwargs = Some(Map("bounty" -> bountyNode)))
    }
  }
}
