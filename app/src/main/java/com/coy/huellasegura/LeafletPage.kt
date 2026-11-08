package com.coy.huellasegura

/**
 * Página del mapa (Leaflet + OpenStreetMap) que usan las pantallas de mapa del personal.
 * Desde la app se llama a `setMarkers(lista, ajustar)` con puntos {id, lat, lon, color, sel}; al tocar un punto o el mapa
 * se avisa a la app con `Android.onMarker(id)` y `Android.onMapClick()`. Necesita internet.
 */
object LeafletPage {

    const val BASE_URL = "https://unpkg.com/"

    val HTML: String = """
        <!doctype html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <style>html,body,#map{height:100%;margin:0;padding:0;}</style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <script>
          var map = L.map('map', {zoomControl:true}).setView([5.0221, -74.0058], 14);
          L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 19, attribution: '&copy; OpenStreetMap'}).addTo(map);
          var layer = L.layerGroup().addTo(map);
          map.on('click', function() { Android.onMapClick(); });
          function setMarkers(list, fit) {
            layer.clearLayers();
            map.invalidateSize();
            list.forEach(function(m) {
              var mk = L.circleMarker([m.lat, m.lon], {radius: m.sel ? 15 : 11, color: m.sel ? '#000' : '#fff', weight: m.sel ? 3 : 2, fillColor: m.color, fillOpacity: 0.95}).addTo(layer);
              mk.on('click', function(e) { L.DomEvent.stopPropagation(e); Android.onMarker(m.id); });
              if (m.sel) { map.panTo([m.lat, m.lon]); }
            });
            if (fit && list.length) {
              map.fitBounds(L.latLngBounds(list.map(function(m) { return [m.lat, m.lon]; })), {padding: [48, 48], maxZoom: 16});
            }
          }
        </script>
        </body>
        </html>
    """.trimIndent()
}
