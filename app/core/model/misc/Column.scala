package core.model.misc

import com.lvxingpai.model.mixin.{ RankEnabled, ImagesEnabled }
import org.bson.types.ObjectId
import org.hibernate.validator.constraints.NotBlank
import org.mongodb.morphia.annotations.{ Entity, Id }

import scala.beans.BeanProperty

/**
 * Created by pengyt on 2015/11/13.
 */
@Entity
class Column extends ImagesEnabled with RankEnabled {

  @Id
  @BeanProperty
  var id: ObjectId = _

  /**
   * 运营位类型
   */
  @NotBlank
  @BeanProperty
  var columnType: String = _

  /**
   * 标题
   */
  @BeanProperty
  var title: String = _

  /**
   * 链接
   */
  @BeanProperty
  var link: String = _
}
