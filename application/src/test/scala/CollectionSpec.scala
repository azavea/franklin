package com.azavea.franklin.datamodel

import com.azavea.franklin.datamodel.stactypes.Collection
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

class CollectionSpec extends AnyFlatSpec {

  val collectionContent =
    """
  |{
  |  "id": "naip",
  |  "type": "Collection",
  |  "links": [
  |    {
  |      "rel": "license",
  |      "href": "https://www.fsa.usda.gov/help/policies-and-links/"
  |    }
  |  ],
  |  "assets": {
  |    "thumbnail": {
  |      "href": "https://ai4edatasetspublicassets.blob.core.windows.net/assets/pc_thumbnails/naip.png",
  |      "title": "Landsat 8 C2",
  |      "media_type": "image/png"
  |    }
  |  },
  |  "extent": {
  |    "spatial": {
  |      "bbox": [
  |        [
  |          -124.784,
  |          24.744,
  |          -66.951,
  |          49.346
  |        ]
  |      ]
  |    },
  |    "temporal": {
  |      "interval": [
  |        [
  |          "2010-01-01T00:00:00Z",
  |          "2019-12-31T00:00:00Z"
  |        ]
  |      ]
  |    }
  |  },
  |  "license": "PDDL-1.0",
  |  "keywords": [
  |    "naip",
  |    "aerial",
  |    "imagery",
  |    "usda",
  |    "afpo",
  |    "agriculture",
  |    "united states"
  |  ],
  |  "providers": [
  |    {
  |      "url": "https://www.fsa.usda.gov/programs-and-services/aerial-photography/imagery-programs/naip-imagery/",
  |      "name": "USDA Farm Service Agency",
  |      "roles": [
  |        "producer",
  |        "licensor"
  |      ]
  |    },
  |    {
  |      "url": "https://www.esri.com/",
  |      "name": "Esri",
  |      "roles": [
  |        "processor"
  |      ]
  |    },
  |    {
  |      "url": "https://planetarycomputer.microsoft.com",
  |      "name": "Microsoft",
  |      "roles": [
  |        "host",
  |        "processor"
  |      ]
  |    }
  |  ],
  |  "summaries": {
  |    "gsd": [
  |      0.6,
  |      1
  |    ],
  |    "eo:bands": [
  |      {
  |        "name": "red",
  |        "common_name": "red",
  |        "description": "visible blue"
  |      },
  |      {
  |        "name": "green",
  |        "common_name": "green",
  |        "description": "visible green"
  |      },
  |      {
  |        "name": "blue",
  |        "common_name": "blue",
  |        "description": "visible blue"
  |      },
  |      {
  |        "name": "nir",
  |        "common_name": "nir",
  |        "description": "near-infrared"
  |      }
  |    ]
  |  },
  |  "description": "The National Agriculture Imagery Program (NAIP) acquires aerial imagery\nduring the agricultural growing seasons in the continental U.S.\n\nNAIP projects are contracted each year based upon available funding and the\nFSA imagery acquisition cycle. Beginning in 2003, NAIP was acquired on\na 5-year cycle. 2008 was a transition year, and a three-year cycle began\nin 2009.\n\nNAIP imagery is acquired at a one-meter ground sample distance (GSD) with a\nhorizontal accuracy that matches within six meters of photo-identifiable\nground control points, which are used during image inspection.\n\nOlder images were collected using 3 bands (Red, Green, and Blue: RGB), but\nnewer imagery is usually collected with an additional near-infrared band\n(RGBN).",
  |  "item_assets": {
  |    "image": {
  |      "type": "image/tiff; application=geotiff; profile=cloud-optimized",
  |      "roles": [
  |        "data"
  |      ],
  |      "title": "RGBIR COG tile",
  |      "eo:bands": [
  |        {
  |          "name": "Red",
  |          "common_name": "red"
  |        },
  |        {
  |          "name": "Green",
  |          "common_name": "green"
  |        },
  |        {
  |          "name": "Blue",
  |          "common_name": "blue"
  |        },
  |        {
  |          "name": "NIR",
  |          "common_name": "nir",
  |          "description": "near-infrared"
  |        }
  |      ]
  |    },
  |    "metadata": {
  |      "type": "text/plain",
  |      "roles": [
  |        "metadata"
  |      ],
  |      "title": "FGDC Metdata"
  |    },
  |    "thumbnail": {
  |      "type": "image/jpeg",
  |      "roles": [
  |        "thumbnail"
  |      ],
  |      "title": "Thumbnail"
  |    }
  |  },
  |  "stac_version": "1.0.0",
  |  "msft:container": "naip",
  |  "stac_extensions": [
  |    "https://stac-extensions.github.io/item-assets/v1.0.0/schema.json"
  |  ],
  |  "msft:storage_account": "naipeuwest",
  |  "msft:short_description": "NAIP provides US-wide, high-resolution aerial imagery.  This dataset includes NAIP images from 2010 to the present."
  |}""".stripMargin

  "A collection" should "roundtrip serialize and deserialize" in {
    val parsed  = parse(collectionContent).getOrElse(Json.Null)
    val encoded = parsed.as[Collection]
    val roundTrip = encoded match {
      case Right(enc) => enc.asJson.deepDropNullValues
      case Left(err)  => throw err
    }
    encoded shouldBe ('right)
    roundTrip should be(parsed)
  }
}
