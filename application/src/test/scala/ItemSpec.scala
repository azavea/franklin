package com.azavea.franklin.datamodel

//import com.azavea.franklin.datamodel.stactypes.Item
import com.azavea.stac4s.StacItem

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import matchers.should.Matchers._


class ItemSpec extends AnyFlatSpec {
  val itemContent = """{"id":"al_m_3008501_ne_16_060_20191109_20200114","bbox":[-85.9375,30.9375,-85.875,31.0],"type":"Feature","links":[{"rel":"self","href":"collections/naip/items/al_m_3008501_ne_16_060_20191109_20200114.json","type":"application/json"}],"assets":{"image":{"href":"https://naipeuwest.blob.core.windows.net/naip/v002/al/2019/al_60cm_2019/30085/m_3008501_ne_16_060_20191109.tif","type":"image/tiff; application=geotiff; profile=cloud-optimized","roles":["data"],"title":"RGBIR COG tile","eo:bands":[{"name":"Red"},{"name":"Green"},{"name":"Blue"},{"name":"NIR","description":"near-infrared"}]},"metadata":{"href":"https://naipeuwest.blob.core.windows.net/naip/v002/al/2019/al_fgdc_2019/30085/m_3008501_ne_16_060_20191109.txt","type":"text/plain","roles":["metadata"],"title":"FGDC Metdata"},"thumbnail":{"href":"https://naipeuwest.blob.core.windows.net/naip/v002/al/2019/al_60cm_2019/30085/m_3008501_ne_16_060_20191109.200.jpg","type":"image/jpeg","roles":["thumbnail"],"title":"Thumbnail"}},"geometry":{"type":"Polygon","coordinates":[[[-85.875,30.9375],[-85.875,31],[-85.9375,31],[-85.9375,30.9375],[-85.875,30.9375]]]},"collection":"naip","properties":{"datetime":"2019-11-09T00:00:00Z","proj:epsg":32616,"providers":[{"url":"https://www.fsa.usda.gov/programs-and-services/aerial-photography/imagery-programs/naip-imagery/","name":"USDA Farm Service Agency","roles":["producer","licensor"]}],"naip:state":"al"},"stac_version":"1.0.0","stac_extensions":["https://stac-extensions.github.io/eo/v1.0.0/schema.json","https://stac-extensions.github.io/projection/v1.0.0/schema.json"]}""".stripMargin

  "An item" should "roundtrip serialize and deserialize" in {
    val parsed = parse(itemContent).getOrElse(Json.Null)
    val encoded = parsed.as[StacItem]
    val roundTrip = encoded match {
      case Right(enc) => enc.asJson.deepDropNullValues
      case Left(err) => throw err
    }
    encoded shouldBe ('right)
    println(roundTrip)
    println(parsed)
    roundTrip should be (parsed)
  }
}