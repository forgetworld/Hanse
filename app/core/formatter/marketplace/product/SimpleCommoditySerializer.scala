package core.formatter.marketplace.product

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ JsonSerializer, SerializerProvider }
import com.lvxingpai.model.marketplace.product.Commodity
import com.lvxingpai.model.misc.ImageItem
import scala.collection.JavaConversions._

/**
 * Created by pengyt on 2015/11/13.
 */
class SimpleCommoditySerializer extends JsonSerializer[Commodity] {

  override def serialize(commodity: Commodity, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()

    gen.writeStringField("title", Option(commodity.title) getOrElse "")
    gen.writeNumberField("marketPrice", Option(commodity.marketPrice) getOrElse 0.0f)
    gen.writeNumberField("price", Option(commodity.price) getOrElse 0.0f)
    gen.writeNumberField("rating", Option(commodity.rating) getOrElse 0.0d)
    gen.writeNumberField("salesVolume", Option(commodity.salesVolume) getOrElse 0)
    // images
    gen.writeFieldName("images")
    gen.writeStartArray()
    val images = commodity.images
    if (images.nonEmpty) {
      val ret = serializers.findValueSerializer(classOf[ImageItem], null)
      for (image <- images)
        ret.serialize(image, gen, serializers)
    }
    gen.writeEndArray()

    gen.writeEndObject()
  }
}