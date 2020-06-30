import { Map as Mapbox, Style, Layer, AnySourceData } from 'mapbox-gl';
import { FeatureCollection } from 'geojson';

const rasterStyle: Style = {
    'version': 8,
    'sources': {
        'raster-tiles': {
            'type': 'raster',
            'tiles': [
                'https://a.tile.openstreetmap.org/{z}/{x}/{y}.png',
                'https://b.tile.openstreetmap.org/{z}/{x}/{y}.png',
                'https://c.tile.openstreetmap.org/{z}/{x}/{y}.png'
            ],
            'tileSize': 256,
            'attribution':
                '© <a target="_top" rel="noopener" href="http://openstreetmap.org">OpenStreetMap</a>, under <a target="_top" rel="noopener" href="http://creativecommons.org/licenses/by-sa/3.0">CC BY SA</a>'
        }
    },
    'layers': [
        {
            'id': 'simple-tiles',
            'type': 'raster',
            'source': 'raster-tiles',
            'minzoom': 0,
            'maxzoom': 22
        }
    ]
};

export function createMap(): Mapbox {
    return new Mapbox({
        container: 'map',
        style: rasterStyle,
        accessToken: 'pk.eyJ1Ijoibm90dGhhdGJyZWV6eSIsImEiOiJjazllYzVzNHUwMHFqM2Vub2dxaXhrYWoxIn0.O18HVS1poWGYKZhyd-S3IQ',
        attributionControl: true
    });
}

interface Extent {
    xmin: number;
    ymin: number;
    xmax: number;
    ymax: number;
}

export function addGeoJson(map: Mapbox, data: FeatureCollection) {
    let sourceData: AnySourceData = { 'type': 'geojson', 'data': data };

    let layer: Layer = {
        'id': 'collecions',
        'type': 'line',
        'source': 'collections',
        'layout': {},
        'paint': {
            'line-color': '#0BB671',
            'line-opacity': 0.8,
            'line-width': 5
        }
    };

    map.on('load', function() {
        map.addSource('collections', sourceData);
        map.addLayer(layer);
    });

}

export function fitToBounds(map: Mapbox, extent: Extent) {
    map.on('load', function() {
        map.fitBounds([extent.xmin, extent.ymin, extent.xmax, extent.ymax], { padding: 20 });
    });
}

interface CollectionExtents {
    [collection: string]: Extent | null;
}

export function addEventListener(map: Mapbox, collectionExtents: CollectionExtents) {
    let items = document.querySelectorAll('.collection-item');
    items.forEach(function(item) {
        let collectionId = item.getAttribute('data-collection') || 'collection';
        let extent = collectionExtents[collectionId];
        item.addEventListener('click', function() {
            if (extent) {
                map.fitBounds([extent.xmin, extent.ymin, extent.xmax, extent.ymax], { padding: 20 });
            }
        });
    });
}
