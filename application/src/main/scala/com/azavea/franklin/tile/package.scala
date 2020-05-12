package com.azavea.franklin

import com.amazonaws.services.s3.AmazonS3URI
import geotrellis.store.s3.util.S3RangeReader
import geotrellis.util.{FileRangeReader, StreamingByteReader}

import java.net.URI
import java.nio.file.Paths

package object tile {

  /** Replicates byte reader functionality in GeoTrellis that we don't get
    * access to
    *
    * @param uri
    * @return
    */
  def getByteReader(uri: String): StreamingByteReader = {

    val javaURI = new URI(uri)
    val rr = javaURI.getScheme match {
      case null =>
        FileRangeReader(Paths.get(uri).toFile)

      case "file" =>
        FileRangeReader(Paths.get(javaURI).toFile)

      case "s3" =>
        val s3Uri = new AmazonS3URI(java.net.URLDecoder.decode(uri, "UTF-8"))
        S3RangeReader(s3Uri.getURI)

      case scheme =>
        throw new IllegalArgumentException(s"Unable to read scheme $scheme at $uri")
    }
    new StreamingByteReader(rr, 128000)
  }

}
