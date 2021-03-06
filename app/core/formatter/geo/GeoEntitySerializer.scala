package core.formatter.geo

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ SerializerProvider, JsonSerializer }
import com.lvxingpai.model.geo.GeoEntity

/**
 * Created by pengyt on 2015/11/4.
 */
class GeoEntitySerializer extends JsonSerializer[GeoEntity] {

  override def serialize(geoEntity: GeoEntity, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    gen.writeStringField("id", if (geoEntity.id != null) geoEntity.id.toString else "")
    gen.writeEndObject()
  }
}