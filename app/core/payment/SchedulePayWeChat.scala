package core.payment

import java.net.URL
import java.util.{ Date, UUID }
import javax.inject.Inject

import com.lvxingpai.inject.morphia.MorphiaMap
import com.lvxingpai.model.marketplace.order.{ Bounty, Prepay }
import core.api.BountyAPI
import core.exception.GeneralPaymentException
import core.misc.Utils
import core.payment.PaymentService.Provider
import core.service.ViaeGateway
import org.mongodb.morphia.Datastore
import play.api.Play.current
import play.api.inject.BindingKey
import play.api.libs.ws.WS
import play.api.{ Configuration, Play }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by topy on 2016/4/11.
 */
class SchedulePayWeChat @Inject() (private val morphiaMap: MorphiaMap, implicit private val viaeGateway: ViaeGateway) extends BountyPay {

  override lazy val provider: Provider.Value = Provider.WeChat

  override lazy val datastore: Datastore = morphiaMap.map("k2")

  /**
   * 计算签名
   * @param data 参数
   * @return
   */
  private def genSign(data: Map[String, String]): String = {
    val stringA = (for ((k, v) <- data.toList.sorted) yield s"$k=$v").mkString("&")
    val stringSignTemp = stringA + "&key=" + SchedulePayWeChat.apiSecrete
    Utils.MD5(stringSignTemp).toUpperCase
  }

  /**
   * 创建一个新的Prepay. 如果创建失败, 比如发生乐观锁冲突之类的情况, 则返回Future(None)
   *
   * @return
   */
  override def createPrepay(bounty: Bounty): Future[Option[Prepay]] = {
    // 构造有待签名的Map
    val callBack = Map("notify_url" -> SchedulePayWeChat.notifyUrl)
    val randomStr = Map("nonce_str" -> UUID.randomUUID().toString.replace("-", ""))

    val accountInfo = {
      Map("appid" -> SchedulePayWeChat.appid, "mch_id" -> SchedulePayWeChat.mchid)
    }
    // 支付商家计划时的价格，是悬赏的总价格减去已支付的订金
    val totalFee = bounty.totalPrice - bounty.bountyPrice
    if (totalFee == 0) {
      throw new RuntimeException(s"Error in creating WeChat prepay. return_msg=价格不能为0")
    }

    val content = Map(
      "out_trade_no" -> bounty.scheduled.itemId.toString,
      "body" -> "旅行派旅行方案支付",
      "trade_type" -> "APP",
      "total_fee" -> totalFee.toString
    )
    val params: Map[String, String] = content ++ accountInfo ++ callBack ++ randomStr
    val sign = Map("sign" -> genSign(params))
    val resultParams = params ++ sign

    val body = Utils.addChildrenToXML(<xml></xml>, resultParams)

    for {
      response <- WS.url(SchedulePayWeChat.unifiedOrderUrl)
        .withHeaders("Content-Type" -> "text/xml; charset=utf-8")
        .withRequestTimeout(30000)
        .post(body)
    } yield {
      val body = scala.xml.XML loadString new String(response.bodyAsBytes, "UTF8")
      val prepayId = (body \ "prepay_id").text
      val returnCode = (body \ "return_code").text
      val resultCode = (body \ "result_code").text
      val returnMsg = (body \ "return_msg").text
      val errorCode = (body \ "err_code").text
      val errorMsg = (body \ "err_code_des").text

      if (returnCode != "SUCCESS" || resultCode != "SUCCESS") {
        throw new RuntimeException(s"Error in creating WeChat prepay. return_msg=$returnMsg / " +
          s"err_code=$errorCode / err_code_des=$errorMsg")
      }

      val providerName = provider.toString

      // 创建新的Prepay对象
      val prepay = new Prepay
      prepay.provider = providerName
      prepay.amount = totalFee
      prepay.createTime = new Date
      prepay.updateTime = new Date
      prepay.prepayId = prepayId

      val query = datastore.createQuery(classOf[Bounty]) field "itemId" equal bounty.itemId field
        s"scheduledPaymentInfo.$providerName" equal null
      val ops = datastore.createUpdateOperations(classOf[Bounty]).set(s"scheduledPaymentInfo.$providerName", prepay)
      val updateResult = datastore.update(query, ops)
      if (updateResult.getUpdatedExisting) Some(prepay)
      else None
    }
  }

  /**
   * 获得订单在某个具体渠道的支付详情(即是否支付)
   * @param bounty
   * @return
   */
  override def refreshPaymentStatus(bounty: Bounty): Future[Bounty] = {
    // 调用微信查询订单接口
    val randomStr = Map("nonce_str" -> UUID.randomUUID().toString.replace("-", ""))
    val accountInfo = {
      Map("appid" -> SchedulePayWeChat.appid, "mch_id" -> SchedulePayWeChat.mchid)
    }
    val content = Map(
      // transaction_id 指微信订单号；out_trade_no指旅行派订单号
      //"transaction_id" -> prepayId
      "out_trade_no" -> bounty.scheduled.itemId.toString
    )
    val params: Map[String, String] = content ++ accountInfo ++ randomStr
    val sign = Map("sign" -> genSign(params))
    val resultParams = params ++ sign

    val body = Utils.addChildrenToXML(<xml></xml>, resultParams)
    val wsFuture = WS.url(SchedulePayWeChat.queryUrl)
      .withHeaders("Content-Type" -> "text/xml; charset=utf-8")
      .withRequestTimeout(30000)
      .post(body.toString())

    wsFuture flatMap (ws => {
      val body = scala.xml.XML loadString new String(ws.bodyAsBytes, "UTF8")
      // 支付状态
      val trade_state = (body \ "trade_state").text
      // val transaction_id = (body \ "transaction_id").text // 微信支付订单号
      // val out_trade_no = (body \ "out_trade_no").text // 商户订单号
      val returnCode = (body \ "return_code").text
      val resultCode = (body \ "result_code").text
      val returnMsg = (body \ "return_msg").text
      val errorCode = (body \ "err_code").text
      val errorMsg = (body \ "err_code_des").text

      if (returnCode != "SUCCESS" || resultCode != "SUCCESS") {
        throw new RuntimeException(s"Error in creating WeChat prepay. return_msg=$returnMsg / " +
          s"err_code=$errorCode / err_code_des=$errorMsg")
      }

      // 旅行派订单状态：pending|paid|committed|finished|cancelled|expired|refundApplied|refunded
      // wx订单交易状态：SUCCESS—支付成功 REFUND—转入退款 NOTPAY—未支付 CLOSED—已关闭
      // REVOKED—已撤销（刷卡支付） USERPAYING--用户支付中 PAYERROR--支付失败
      if (trade_state.equals("SUCCESS")) {
        BountyAPI.setSchedulePaid(bounty.itemId, PaymentService.Provider.WeChat)(datastore) map (_ => {
          bounty
        })
      } else
        Future(bounty)
    })
  }

  /**
   * 获得sidecar信息. (比如: 签名等, 就位于其中)
   * @return
   */
  override protected def createSidecar(bounty: Bounty, prepay: Prepay): Map[String, Any] = {
    val original = Map(
      "appid" -> SchedulePayWeChat.appid,
      "partnerid" -> SchedulePayWeChat.mchid,
      "prepayid" -> prepay.prepayId,
      "package" -> "Sign=WXPay",
      "noncestr" -> UUID.randomUUID().toString.replace("-", ""),
      "timestamp" -> (System.currentTimeMillis / 1000).toString
    )
    original + ("sign" -> genSign(original))
  }

  /**
   * 处理支付渠道服务器发来的异步调用
   * @param params 参数
   * @return
   */
  override def handleCallback(params: Map[String, Any]): Future[Any] = {
    val data = params mapValues { case ss: Any => ss.toString }
    implicit val ds = datastore
    try {
      if (!SchedulePayWeChat.verifyWeChatPay(data, data("sign"))) {
        Future {
          SchedulePayWeChat.wechatCallBackError
        }
      } else {
        // 更新数据库
        val tradeNumber = params.getOrElse("out_trade_no", "").toString
        val scheduleId = try {
          tradeNumber.toLong
        } catch {
          case _: NumberFormatException => throw GeneralPaymentException(s"Invalid out_trade_no: $tradeNumber")
        }

        for {
          schedule <- BountyAPI.getScheduleById(scheduleId)
          _ <- BountyAPI.setSchedulePaid(schedule.bountyId, PaymentService.Provider.WeChat)
        } yield {
          SchedulePayWeChat.wechatCallBackOK
        }
      }
    } catch {
      case e: GeneralPaymentException => Future {
        throw e
      }
    }
  }

  /**
   * 执行退款操作
   *
   * @return
   */
  override def refundProcess(bounty: Bounty, amount: Int): Future[Unit] = {
    val content = Map("refund_fee" -> amount.toString, "total_fee" -> bounty.bountyPrice.toString,
      "out_trade_no" -> bounty.scheduled.itemId.toString)

    val url = SchedulePayWeChat.refundOrderUrlUrl
    val randomStr = Map("nonce_str" -> Utils.nonceStr())

    val accountInfo = {
      Map("appid" -> SchedulePayWeChat.appid, "mch_id" -> SchedulePayWeChat.mchid)
    }
    val refundInfo = {
      // 操作人ID为应用的mchid  退款单单号
      Map("op_user_id" -> SchedulePayWeChat.mchid, "out_refund_no" -> System.currentTimeMillis.toString)
    }

    val params = content ++ accountInfo ++ randomStr ++ refundInfo
    val sign = Map("sign" -> genSign(params))
    val resultParams = params ++ sign
    val body = Utils.addChildrenToXML(<xml></xml>, resultParams)
    val wsFuture = WS.url(url)
      .withHeaders("Content-Type" -> "text/xml; charset=utf-8")
      .withRequestTimeout(30000)
      .post(body.toString())

    wsFuture map (ws => {
      val body = scala.xml.XML loadString new String(ws.bodyAsBytes, "UTF8")
      // 退款单号
      val returnCode = (body \ "return_code").text
      val resultCode = (body \ "result_code").text
      val returnMsg = (body \ "return_msg").text
      val errorCode = (body \ "err_code").text
      val errorMsg = (body \ "err_code_des").text
      val refundNo = (body \ "out_refund_no").text

      if (returnCode != "SUCCESS" || resultCode != "SUCCESS") {
        throw new RuntimeException(s"Error in creating WeChat prepay. return_msg=$returnMsg / " +
          s"err_code=$errorCode / err_code_des=$errorMsg")
      }
    })
  }

  /**
   * 查询退款
   * @param params 参数
   * @return
   */
  override def refundQuery(params: Map[String, Any]): Future[Any] = ???
}

object SchedulePayWeChat {
  lazy val instance = Play.application.injector.instanceOf[SchedulePayWeChat]

  lazy private val conf = {
    val key = BindingKey(classOf[Configuration]) qualifiedWith "default"
    Play.current.injector instanceOf key
  }

  // 微信服务器的url
  lazy val unifiedOrderUrl = (conf getString "hanse.payment.wechat.unifiedOrderUrl").get
  // 退款url
  lazy val refundOrderUrlUrl = (conf getString "hanse.payment.wechat.refundOrderUrl").get

  lazy val notifyUrl = {
    val baseUrl = new URL(conf getString "hanse.baseUrl" getOrElse "http://localhost:9000")

    val protocol = baseUrl.getProtocol
    val host = baseUrl.getHost
    val port = Some(baseUrl.getPort) flatMap (p => if (p == -1 || p == 80) None else Some(p))
    val path1 = baseUrl.getPath
    val path2 = controllers.routes.BountyCtrl.wechatCallback("schedules").url
    s"$protocol://$host${port map (p => s":$p") getOrElse ""}$path1$path2"
  }

  lazy val queryUrl = (conf getString "hanse.payment.wechat.queryOrderUrl").get

  lazy val appid = (conf getString "hanse.payment.wechat.appid").get

  lazy val apiSecrete = (conf getString "hanse.payment.wechat.apisecret").get

  lazy val mchid = (conf getString "hanse.payment.wechat.mchid").get

  /**
   * 验证微信签名
   * @param params 签名所需的数据
   * @param sign 签名
   * @return 签名是否通过
   */
  def verifyWeChatPay(params: Map[String, String], sign: String): Boolean = {
    // 将获取的数据按字典排序
    val sortedKeys = params.keys.toSeq.sorted
    // 剔除"sign"字段, 将数据组装成所需的字符串
    val contents = sortedKeys filterNot (Seq("sign") contains _) map
      (key => s"$key=${params(key)}") mkString "&"
    val stringSignTemp = contents + "&key=" + SchedulePayWeChat.apiSecrete
    Utils.MD5(stringSignTemp).toUpperCase.equals(sign)
  }

  val wechatCallBackOK =
    <xml>
      <return_code>
        <![CDATA[SUCCESS]]>
      </return_code>
      <return_msg>
        <![CDATA[OK]]>
      </return_msg>
    </xml>

  val wechatCallBackError =
    <xml>
      <return_code>
        <![CDATA[FAIL]]>
      </return_code>
      <return_msg>
        <![CDATA[FAIL]]>
      </return_msg>
    </xml>

}