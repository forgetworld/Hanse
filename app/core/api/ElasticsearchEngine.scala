package core.api

import com.lvxingpai.model.marketplace.product.Commodity
import com.lvxingpai.model.misc.ImageItem
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ ElasticClient, HitAs, RichSearchHit }

import scala.concurrent.Future

/**
 * Created by zephyre on 1/30/16.
 */
class ElasticsearchEngine(uri: String) extends SearchEngine {

  lazy val client = ElasticClient.transport(uri)

  implicit object CommodityHitAs extends HitAs[Commodity] {
    def asImageItem(source: Map[String, Any]): Option[ImageItem] = {
      None
    }

    override def as(hit: RichSearchHit): Commodity = {
      val source = hit.sourceAsMap

      val commodity = new Commodity
      commodity.commodityId = source("commodityId").asInstanceOf[Number].longValue()
      commodity.title = source.getOrElse("title", "").toString
      commodity.rating = source.getOrElse("rating", 0).asInstanceOf[Number].doubleValue()
      commodity.salesVolume = source.getOrElse("salesVolume", 0).asInstanceOf[Number].intValue()
      commodity.marketPrice = source.getOrElse("marketPrice", 0).asInstanceOf[Number].intValue()
      commodity.price = source.getOrElse("price", 0).asInstanceOf[Number].intValue()

      commodity
    }
  }

  /**
   * 搜索商品: 综合排序
   * @param q 搜索的关键词
   */
  override def overallCommodities(q: Option[String] = None): Future[Seq[Commodity]] = {
    val s = {
      val head = search in "marketplace" / "commodity"
      if (q.nonEmpty) {
        head query {
          matchQuery("title", q.get)
        }
      } else {
        head
      }
    }
    import scala.concurrent.ExecutionContext.Implicits.global

    client.execute(s) map (_.as[Commodity])
  }
}