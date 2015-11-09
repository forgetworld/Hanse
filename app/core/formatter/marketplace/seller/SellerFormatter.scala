package core.formatter.marketplace.seller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.lvxingpai.model.marketplace.product.{ Commodity, CommodityPlan, Pricing, StockInfo }
import com.lvxingpai.model.marketplace.seller.BankAccount
import com.lvxingpai.model.misc.PhoneNumber
import core.formatter.BaseFormatter
import core.formatter.marketplace.product.{ CommodityPlanSerializer, CommoditySerializer, PricingSerializer, StockInfoSerializer }
import core.formatter.misc.PhoneNumberSerializer
import core.model.SellerDTO

/**
 * Created by pengyt on 2015/11/3.
 */
class SellerFormatter extends BaseFormatter {
  override val objectMapper = {
    val mapper = new ObjectMapper()
    val module = new SimpleModule()
    module.addSerializer(classOf[CommodityPlan], new CommodityPlanSerializer)
    module.addSerializer(classOf[Commodity], new CommoditySerializer)
    module.addSerializer(classOf[Pricing], new PricingSerializer)
    module.addSerializer(classOf[StockInfo], new StockInfoSerializer)
    module.addSerializer(classOf[BankAccount], new BankAccountSerializer)
    module.addSerializer(classOf[SellerDTO], new SellerDTOSerializer)
    module.addSerializer(classOf[PhoneNumber], new PhoneNumberSerializer)
    mapper.registerModule(module)
    mapper
  }
}

