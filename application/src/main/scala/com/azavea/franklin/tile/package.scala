package com.azavea.franklin

import com.amazonaws.services.s3.AmazonS3URI
import geotrellis.store.s3.util.S3RangeReader
import geotrellis.util.{FileRangeReader, StreamingByteReader}

import java.net.URI
import java.nio.file.Paths

import geotrellis.util.RangeReader

import scalaj.http.Http
import org.log4s._

import java.net.{URI, URL}
import scala.util.Try

class HttpRangeReader(url: URL, useHeadRequest: Boolean) extends RangeReader {
  @transient private[this] lazy val logger = getLogger

  val request = Http(url.toString)

  val totalLength: Long = {
    val headers = if (useHeadRequest) {
      request.method("HEAD").asString
    } else {
      request.method("GET").execute { _ => "" }
    }
    val contentLength = headers
      .header("Content-Length")
      .flatMap({ cl => Try(cl.toLong).toOption }) match {
      case Some(num) => num
      case None      => -1L
    }
    headers.throwError

    /**
      * "The Accept-Ranges response HTTP header is a marker used by the server
      *  to advertise its support of partial requests. The value of this field
      *  indicates the unit that can be used to define a range."
      * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Ranges
      */
    require(
      headers.header("Accept-Ranges") == Some("bytes"),
      "Server doesn't support ranged byte reads"
    )

    require(
      contentLength > 0,
      "Server didn't provide (required) \"Content-Length\" headers, unable to do range-based read"
    )

    contentLength
  }

  def readClippedRange(start: Long, length: Int): Array[Byte] = {
    val res = request
      .method("GET")
      .header("Range", s"bytes=${start}-${start + length}")
      .asBytes

    /**
      * "If the byte-range-set is unsatisfiable, the server SHOULD return
      *  a response with a status of 416 (Requested range not satisfiable).
      *  Otherwise, the server SHOULD return a response with a status of 206
      *  (Partial Content) containing the satisfiable ranges of the entity-body."
      * https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
      */
    require(
      res.code != 416,
      s"Server unable to generate the byte range between ${start} and ${start + length}"
    )

    if (res.code != 206)
      logger.info(
        "Server responded to range request with HTTP code other than PARTIAL_RESPONSE (206)"
      )

    res.body
  }

}

/**
  * This class extends [[RangeReader]] by reading chunks out of a GeoTiff at the
  * specified HTTP location.
  *
  * @throws [[HttpStatusException]] if the HTTP response code is 4xx or 5xx
  *
  * @param url: A [[URL]] pointing to the desired GeoTiff.
  */

/** The companion object of [[HttpRangeReader]] */
object HttpRangeReader {

  def apply(address: String): HttpRangeReader = apply(new URL(address))

  def apply(uri: URI): HttpRangeReader = apply(uri.toURL)

  /**
    * Returns a new instance of HttpRangeReader.
    *
    * @param url: A [[URL]] pointing to the desired GeoTiff.
    * @return A new instance of HttpRangeReader.
    */
  def apply(url: URL): HttpRangeReader = new HttpRangeReader(url, true)

  /**
    * Returns a new instance of HttpRangeReader which does not use HEAD
    * to determine the totalLength.
    *
    * @param url: A [[URL]] pointing to the desired GeoTiff.
    * @return A new instance of HttpRangeReader.
    */
  def withoutHeadRequest(url: URL): HttpRangeReader = new HttpRangeReader(url, false)

  def withoutHeadRequest(address: String): HttpRangeReader = withoutHeadRequest(new URL(address))

  def withoutHeadRequest(uri: URI): HttpRangeReader = withoutHeadRequest(uri.toURL)
}

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

      case "https" =>
        HttpRangeReader(javaURI)

      case scheme =>
        throw new IllegalArgumentException(s"Unable to read scheme $scheme at $uri")
    }
    new StreamingByteReader(rr, 128000)
  }

}
