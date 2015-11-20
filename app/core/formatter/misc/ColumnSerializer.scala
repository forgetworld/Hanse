package core.formatter.misc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ SerializerProvider, JsonSerializer }
import com.lvxingpai.model.misc.ImageItem
import core.model.misc.Column
import scala.collection.JavaConversions._
/**
 * Created by pengyt on 2015/11/13.
 */
class ColumnSerializer extends JsonSerializer[Column] {

  override def serialize(column: Column, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    if (column.title != null)
      gen.writeStringField("title", column.title)
    // images
    gen.writeFieldName("images")
    gen.writeStartArray()
    val images = column.images
    if (images.nonEmpty) {
      val ret = serializers.findValueSerializer(classOf[ImageItem], null)
      for (image <- images)
        ret.serialize(image, gen, serializers)
    }
    gen.writeEndArray()

    if (column.link != null)
      gen.writeStringField("link", column.link)

    gen.writeEndObject()
  }
}