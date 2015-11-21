package core.formatter.marketplace.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.lvxingpai.model.account.UserInfo
import com.lvxingpai.model.geo.Country
import com.lvxingpai.model.marketplace.order.{ Prepay, Order, Person }
import com.lvxingpai.model.marketplace.product.Commodity
import com.lvxingpai.model.marketplace.seller.{ Seller, BankAccount }
import com.lvxingpai.model.misc.{ IdProof, ImageItem, PhoneNumber, RichText }
import core.formatter.BaseFormatter
import core.formatter.formatter.taozi.ImageItemSerializer
import core.formatter.geo.SimpleCountrySerializer
import core.formatter.marketplace.product.CommoditySerializer
import core.formatter.marketplace.seller.{ SellerSerializer, BankAccountSerializer }
import core.formatter.misc.{ IdProofSerializer, PersonSerializer, PhoneNumberSerializer, RichTextSerializer }
import core.formatter.user.UserSerializer

/**
 * Created by pengyt on 2015/11/21.
 */
class OrderFormatter extends BaseFormatter {

  override val objectMapper = {
    val mapper = new ObjectMapper()
    val module = new SimpleModule()
    mapper.registerModule(DefaultScalaModule)
    module.addSerializer(classOf[Order], new OrderSerializer)
    module.addSerializer(classOf[Commodity], new CommoditySerializer)
    module.addSerializer(classOf[RichText], new RichTextSerializer)
    module.addSerializer(classOf[BankAccount], new BankAccountSerializer)
    module.addSerializer(classOf[Seller], new SellerSerializer)
    module.addSerializer(classOf[PhoneNumber], new PhoneNumberSerializer)
    module.addSerializer(classOf[UserInfo], new UserSerializer)
    module.addSerializer(classOf[ImageItem], new ImageItemSerializer)
    module.addSerializer(classOf[Person], new PersonSerializer)
    module.addSerializer(classOf[IdProof], new IdProofSerializer)
    module.addSerializer(classOf[Country], new SimpleCountrySerializer)
    module.addSerializer(classOf[Prepay], new PrepaySerializer)
    mapper.registerModule(module)
    mapper
  }
}
