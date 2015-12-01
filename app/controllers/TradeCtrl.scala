package controllers

import java.util.Date
import javax.inject._

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.lvxingpai.inject.morphia.MorphiaMap
import com.lvxingpai.model.account.RealNameInfo
import com.lvxingpai.model.marketplace.order.Order
import com.lvxingpai.model.marketplace.product.Commodity
import com.lvxingpai.model.misc.PhoneNumber
import core.api.{ CommodityAPI, OrderAPI }
import core.formatter.marketplace.order.{ SimpleOrderFormatter, OrderFormatter, OrderStatusFormatter }
import core.misc.HanseResult
import core.model.trade.order.OrderStatus
import org.bson.types.ObjectId
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Configuration
import play.api.mvc.{ Action, Controller, Results }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by topy on 2015/10/22.
 */
@Singleton
class TradeCtrl @Inject() (@Named("default") configuration: Configuration, datastore: MorphiaMap) extends Controller {

  implicit lazy val ds = datastore.map.get("k2").get

  val dateFmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  case class OrderTemp(name: String, commodity: Commodity, contact: ContactTemp, planId: String,
      quantity: Int, comment: String, time: Date, consumerId: Long) {
    def toOrder = {
      val order = new Order
      val now = DateTime.now().toDate()
      order.id = new ObjectId
      order.orderId = now.getTime
      order.consumerId = consumerId
      order.commodity = commodity
      order.contact = contact.toContact
      order.planId = planId
      order.quantity = quantity
      order.comment = comment
      order.rendezvousTime = time
      order.status = OrderStatus.Pending
      order.createTime = now
      order.updateTime = now
      // 设置订单的失效时间为三天
      val expireDate = DateTime.now().plusDays(3)
      order.expireDate = expireDate.toDate
      order
    }
  }

  case class ContactTemp(surname: String, givenName: String, phone: String, email: String) {
    def toContact = {
      val contact = new RealNameInfo
      val tel = new PhoneNumber
      contact.surname = surname
      contact.givenName = givenName
      // TODO 当前默认都是中国顾客
      tel.dialCode = 86
      tel.number = phone.toLong
      contact.tel = tel
      contact.email = email
      contact
    }
  }

  case class Traveller(key: String)

  /**
   * 创建订单
   * @return 返回订单信息
   */
  def createOrder() = Action.async(
    request => {
      val orderFmt = (new OrderFormatter).objectMapper
      val userId = request.headers.get("UserId").getOrElse("").toLong
      val ret = for {
        body <- request.body.asJson
        commodityId <- (body \ "commodityId").asOpt[Long]
        planId <- (body \ "planId").asOpt[String]
        rendezvousTime <- (body \ "rendezvousTime").asOpt[String]
        quantity <- (body \ "quantity").asOpt[Int]
        //travellers <- (body \ "travellers").asOpt[Array[Person]]
        phone <- (body \ "contactPhone").asOpt[String]
        email <- (body \ "contactEmail").asOpt[String]
        surname <- (body \ "contactSurname").asOpt[String]
        givenName <- (body \ "contactGivenName").asOpt[String]
        comment <- (body \ "contactComment").asOpt[String].orElse(Option(""))
      } yield {
        val date = DateTime.parse(rendezvousTime).toDate
        val contact = ContactTemp(surname, givenName, phone, email)
        for {
          commodity <- CommodityAPI.getCommoditySnapsById(commodityId, planId)
          order <- OrderAPI.createOrder(OrderTemp(commodity.title, commodity, contact, planId, quantity, comment, date, userId).toOrder)
        } yield {
          val node = orderFmt.valueToTree[JsonNode](order)
          HanseResult(data = Some(node))
        }
      }
      ret.getOrElse(Future {
        HanseResult.unprocessable()
      })
    }
  )

  /**
   * 订单详情
   * @param orderId 订单id
   * @return 订单详情
   */
  def getOrderInfo(orderId: Long) = Action.async(
    request => {
      val orderMapper = new OrderFormatter().objectMapper
      for {
        order <- OrderAPI.getOrder(orderId)
      } yield {
        val node = orderMapper.valueToTree[JsonNode](order)
        HanseResult(data = Some(node))
      }
    }
  )

  def getOrderStatus(orderId: Long) = Action.async(
    request => {
      val orderMapper = new OrderStatusFormatter().objectMapper
      for {
        order <- OrderAPI.getOrderOnlyStatus(orderId)
      } yield {
        val node = orderMapper.valueToTree[JsonNode](order)
        HanseResult(data = Some(node))
      }
    }
  )

  /**
   * 预支付
   * @return 带签名的字符串
   */
  def prePay() = Action.async(
    request => {
      val orderPara = for {
        body <- request.body.asJson
        orderId <- (body \ "orderId").asOpt[Long]
        payType <- (body \ "payType").asOpt[String]
      } yield orderId -> payType

      val mapper = new ObjectMapper()
      val node = mapper.createObjectNode()

      if (orderPara isEmpty) Future {
        HanseResult.unprocessable()
      }
      else {
        for {
          str <- OrderAPI.prePay(orderPara.get._2, orderPara.get._1)
        } yield {
          node.put("result", str)
          HanseResult(data = Some(node))
        }
      }
    }
  )

  /**
   * 根据订单号查询订单的支付结果, 如果支付成功, 直接返回, 如果不成功, 主动请求支付宝查询接口, 如果支付成功则修改订单状态, 否则直接返回未支付
   * @return 订单状态
   */
  def payConfirm() = Action.async(
    request => {
      val futureStatus = for {
        body <- request.body.asJson
        orderId <- (body \ "orderId").asOpt[Long]
      } yield OrderAPI.getOrderStatus(orderId)

      val mapper = new ObjectMapper()
      val node = mapper.createObjectNode()
      for {
        status <- futureStatus.get
      } yield {
        node.put("status", status)
        HanseResult(data = Some(node))
      }
    }
  )

  /**
   * 支付宝回调接口
   * @return 支付宝需要的结果：success
   */
  def notifyResult() = Action.async(
    request => {
      Future {
        // 获取回调数据
        val dataOpt = request.body.asFormUrlEncoded
        if (dataOpt.nonEmpty) {
          val data = dataOpt.get
          // 将获取的数据按字典排序
          val sortedKeys = data.keys.toSeq.sorted
          // 剔除"sign", "sign_type"字段, 将数据组装成所需的字符串
          val contents = sortedKeys filter (key => !(Seq("sign", "sign_type") contains key)) map (key => {
            val value = data(key) mkString ""
            s"$key=$value"
          }) mkString "&"
          // 支付宝签名
          val sign = data("sign").head
          // 订单信息
          val out_trade_no = data("out_trade_no").head.toLong
          // 订单状态
          val trade_status = data("trade_status").head
          // 验证支付宝签名
          // 支付宝公钥
          if (OrderAPI.verifyAlipay(contents, sign)) {
            // 系统的订单状态
            val orderStatus = OrderAPI.aliOrderStatus2OrderStatus(trade_status)
            // 验证通过
            // 根据支付宝的回调结果修改订单状态
            OrderAPI.updateOrderStatus(out_trade_no, orderStatus)
            // 返回支付宝信息
            Results.Ok("success")
          } else {
            HanseResult.forbidden(HanseResult.RetCode.FORBIDDEN, errorMsg = Some("invaild request"))
          }
        } else {
          HanseResult.forbidden(HanseResult.RetCode.FORBIDDEN, errorMsg = Some("invaild request"))
        }
      }
    }
  )

  /**
   * 根据用户id获取订单列表
   * 如果订单状态为空, 获取所在用户下的所有的订单列表
   * 如果订单状态不为空, 获取所在用户下的某个订单状态的订单列表
   * @param userId 用户id
   * @param status 订单状态
   * @return 订单列表
   */
  def getOrders(userId: Long, status: Option[String]) = Action.async(
    request => {
      val orderMapper = new SimpleOrderFormatter().objectMapper
      for {
        orders <- OrderAPI.getOrderList(userId, status)
      } yield {
        val node = orderMapper.valueToTree[JsonNode](orders)
        HanseResult(data = Some(node))
      }
    }
  )

}
