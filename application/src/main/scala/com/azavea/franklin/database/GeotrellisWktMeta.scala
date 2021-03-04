package com.azavea.franklin.database

import com.azavea.stac4s.TwoDimBbox
import doobie._
import doobie.postgres.pgisimplicits._
import doobie.util.invariant.InvalidObjectMapping
import geotrellis.vector._
import geotrellis.vector.io.wkt.WKT
import org.postgis.GeometryBuilder
import org.postgis.PGgeometry

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

trait GeotrellisWktMeta {

  implicit val pgMeta: Meta[PGgeometry] =
    Meta.Advanced.other[PGgeometry]("geometry")

  // Constructor for geometry types via WKT reading/writing
  @SuppressWarnings(Array("AsInstanceOf"))
  private def geometryType[A >: Null <: Geometry: TypeTag](
      implicit A: ClassTag[A]
  ): Meta[Projected[A]] =
    PGgeometryType.timap[Projected[A]](pgGeom => {
      val split = GeometryBuilder.splitSRID(pgGeom.getValue)
      val srid  = split(0).splitAt(5)._2.toInt
      val geom  = WKT.read(split(1))
      try Projected[A](A.runtimeClass.cast(geom).asInstanceOf[A], srid)
      catch {
        case _: ClassCastException =>
          throw InvalidObjectMapping(A.runtimeClass, pgGeom.getGeometry.getClass)
      }
    })(geom => {
      val wkt    = s"SRID=${geom.srid};" + WKT.write(geom)
      val pgGeom = GeometryBuilder.geomFromString(wkt)
      new PGgeometry(pgGeom)
    })

  private val extentToBbox = (extent: Extent) => {
    TwoDimBbox(extent.xmin, extent.ymin, extent.xmax, extent.ymax)
  }

  private val bboxToExtent = (bbox: TwoDimBbox) => {
    Extent(bbox.xmin, bbox.ymin, bbox.xmax, bbox.ymax)
  }

  implicit val TwodimBboxMeta: Meta[TwoDimBbox] =
    GeometryMeta.imap(geom => extentToBbox(geom.extent))(bboxToExtent(_))

  implicit val GeometryMeta: Meta[Geometry] =
    GeometryType.imap(_.geom)(geom => Projected(geom, 4326))

  implicit val GeometryType: Meta[Projected[Geometry]] =
    geometryType[Geometry]

  implicit val GeometryCollectionType: Meta[Projected[GeometryCollection]] =
    geometryType[GeometryCollection]

  implicit val MultiLineStringType: Meta[Projected[MultiLineString]] =
    geometryType[MultiLineString]

  implicit val MultiPolygonType: Meta[Projected[MultiPolygon]] =
    geometryType[MultiPolygon]
  implicit val LineStringType: Meta[Projected[LineString]] = geometryType[LineString]

  implicit val MultiPointType: Meta[Projected[MultiPoint]] =
    geometryType[MultiPoint]

  implicit val PolygonType: Meta[Projected[Polygon]] =
    geometryType[Polygon]

  implicit val PointType: Meta[Projected[Point]] =
    geometryType[Point]

  implicit val ComposedGeomType: Meta[Projected[GeometryCollection]] =
    geometryType[GeometryCollection]

}
