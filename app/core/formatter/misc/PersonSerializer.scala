package core.formatter.misc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ JsonSerializer, SerializerProvider }
import com.lvxingpai.model.account.Gender
import com.lvxingpai.model.misc.{ IdProof, PhoneNumber }
import core.model.trade.order.Person

/**
 * Created by pengyt on 2015/11/17.
 */
class PersonSerializer extends JsonSerializer[Person] {

  override def serialize(person: Person, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    gen.writeStringField("surname", person.surname)
    gen.writeStringField("givenName", person.givenName)
    gen.writeStringField("gender", if (person.gender == Gender.Male) "male" else "female")
    gen.writeStringField("birthday", person.birthday.toString)
    if (person.fullName != null)
      gen.writeStringField("fullName", person.fullName)

    gen.writeFieldName("idProof")
    val idProof = person.idProof
    if (idProof != null) {
      val retIdProof = serializers.findValueSerializer(classOf[IdProof], null)
      retIdProof.serialize(idProof, gen, serializers)
    }

    gen.writeFieldName("tel")
    val tel = person.tel
    if (tel != null) {
      val retTel = serializers.findValueSerializer(classOf[PhoneNumber], null)
      retTel.serialize(tel, gen, serializers)
    }
    gen.writeStringField("email", person.email)
    gen.writeEndObject()
  }
}
