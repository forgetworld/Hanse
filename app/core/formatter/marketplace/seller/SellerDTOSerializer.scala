package core.formatter.marketplace.seller

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ JsonSerializer, SerializerProvider }
import com.lvxingpai.model.geo.GeoEntity
import com.lvxingpai.model.marketplace.seller.BankAccount
import com.lvxingpai.model.misc.PhoneNumber
import core.model.SellerDTO

import scala.collection.JavaConversions._

/**
 * Created by pengyt on 2015/11/3.
 */
class SellerDTOSerializer extends JsonSerializer[SellerDTO] {

  override def serialize(seller: SellerDTO, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    if (seller.sellerId != null)
      gen.writeNumberField("sellerId", seller.sellerId)

    if (seller.avatar != null)
      gen.writeStringField("avatar", seller.avatar)
    if (seller.nickName != null)
      gen.writeStringField("nickName", seller.nickName)
    if (seller.signature != null)
      gen.writeStringField("signature", seller.signature)
    if (seller.userPhone != null)
      gen.writeStringField("userPhone", seller.userPhone)

    gen.writeFieldName("lang")
    gen.writeStartArray()
    if (seller.lang != null) {
      for (l: String <- seller.lang)
        gen.writeString(l)
    }
    gen.writeEndArray()

    // 服务区域，可以是国家，也可以是目的地
    gen.writeFieldName("serviceZones")
    gen.writeStartArray()
    val serviceZones = seller.serviceZones
    if (serviceZones != null && !serviceZones.isEmpty) {
      val retServiceZones = serializers.findValueSerializer(classOf[GeoEntity], null)
      for (serviceZone <- serviceZones) {
        retServiceZones.serialize(serviceZone, gen, serializers)
      }
    }
    gen.writeEndArray()

    gen.writeFieldName("bankAccounts")
    gen.writeStartArray()
    val bankAccounts = seller.bankAccounts
    if (bankAccounts != null && !bankAccounts.isEmpty) {
      val retBankAccounts = serializers.findValueSerializer(classOf[BankAccount], null)
      for (bankAccount <- bankAccounts) {
        retBankAccounts.serialize(bankAccount, gen, serializers)
      }
    }
    gen.writeEndArray()

    if (seller.name != null)
      gen.writeStringField("name", seller.name)

    gen.writeFieldName("email")
    gen.writeStartArray()
    if (seller.email != null) {
      for (e <- seller.email)
        gen.writeString(e)
    }
    gen.writeEndArray()

    gen.writeFieldName("phone")
    gen.writeStartArray()
    val phone = seller.phone
    if (phone != null && !phone.isEmpty) {
      val retPhone = serializers.findValueSerializer(classOf[PhoneNumber], null)
      for (p <- phone) {
        retPhone.serialize(p, gen, serializers)
      }
    }
    gen.writeEndArray()

    if (seller.address != null)
      gen.writeStringField("address", seller.address)

    if (seller.favorCnt != null)
      gen.writeNumberField("favorCnt", seller.favorCnt)

    gen.writeEndObject()
  }
}