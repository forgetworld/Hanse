package core.formatter.marketplace.product

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ JsonSerializer, SerializerProvider }
import com.lvxingpai.model.marketplace.product.{ CommodityPlan, Commodity }
import com.lvxingpai.model.marketplace.seller.Seller
import com.lvxingpai.model.misc.ImageItem

import scala.collection.JavaConversions._

/**
 * Created by topy on 2015/11/3.
 */
class CommoditySnapsSerializer extends JsonSerializer[Commodity] {

  override def serialize(commodity: Commodity, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    gen.writeNumberField("commodityId", Option(commodity.commodityId) getOrElse 0L)
    gen.writeStringField("title", Option(commodity.title) getOrElse "")

    // 商家
    gen.writeFieldName("seller")
    val seller = commodity.seller
    if (seller != null) {
      val retSeller = serializers.findValueSerializer(classOf[Seller], null)
      retSeller.serialize(seller, gen, serializers)
    }

    gen.writeFieldName("plans")
    gen.writeStartArray()
    val plans = commodity.plans
    if (plans != null) {
      val retPlan = serializers.findValueSerializer(classOf[CommodityPlan], null)
      for (p <- plans)
        retPlan.serialize(p, gen, serializers)
    } else serializers.findNullValueSerializer(null)
    gen.writeEndArray()

    gen.writeFieldName("cover")
    val cover = commodity.cover
    val retCover = if (cover != null) serializers.findValueSerializer(classOf[ImageItem], null)
    else serializers.findNullValueSerializer(null)
    retCover.serialize(cover, gen, serializers)

    // images
    gen.writeFieldName("images")
    gen.writeStartArray()
    val images = commodity.images
    if (images != null && !images.isEmpty) {
      val ret = serializers.findValueSerializer(classOf[ImageItem], null)
      // 只取第一张图片
      ret.serialize(images.get(0), gen, serializers)
    }
    gen.writeEndArray()

    //    gen.writeFieldName("category")
    //    gen.writeStartArray()
    //    val categories = commodity.category
    //    if (categories != null) {
    //      for (category <- categories)
    //        gen.writeString(category)
    //    }
    //    gen.writeEndArray()

    //    gen.writeFieldName("desc")
    //    val desc = commodity.desc
    //    if (desc != null) {
    //      val retDesc = serializers.findValueSerializer(classOf[RichText], null)
    //      retDesc.serialize(desc, gen, serializers)
    //    }

    gen.writeEndObject()
  }
}