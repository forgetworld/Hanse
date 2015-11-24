package core.formatter.misc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ JsonSerializer, SerializerProvider }
import com.lvxingpai.model.account.{ Gender, RealNameInfo }
import com.lvxingpai.model.misc.PhoneNumber

/**
 * Created by pengyt on 2015/11/17.
 */
class RealNameInfoSerializer extends JsonSerializer[RealNameInfo] {

  override def serialize(person: RealNameInfo, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeStartObject()
    gen.writeStringField("surname", Option(person.surname) getOrElse "")
    gen.writeStringField("givenName", Option(person.givenName) getOrElse "")
    if (person.gender != null)
      gen.writeStringField("gender", if (person.gender == Gender.Male) "male" else "female")
    else gen.writeStringField("gender", "male")
    gen.writeStringField("birthday", Option(person.birthday.toString) getOrElse "")
    // gen.writeStringField("fullName", Option(person.fullName) getOrElse "")

    gen.writeFieldName("idProof")
    //    val idProof = person.idProof
    //    if (idProof != null) {
    //      val retIdProof = serializers.findValueSerializer(classOf[IdProof], null)
    //      retIdProof.serialize(idProof, gen, serializers)
    //    }

    gen.writeFieldName("tel")
    val tel = person.tel
    if (tel != null) {
      val retTel = serializers.findValueSerializer(classOf[PhoneNumber], null)
      retTel.serialize(tel, gen, serializers)
    }
    gen.writeStringField("email", Option(person.email) getOrElse "")
    gen.writeEndObject()
  }
}