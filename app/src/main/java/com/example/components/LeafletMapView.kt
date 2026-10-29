package com.example.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.data.DelhiPlace
import com.example.lib.Route
import com.example.ui.theme.MapsBackground
import com.example.ui.theme.MapsSurface
import com.example.ui.theme.MapsTextSecondary
import com.example.ui.theme.RobotoFontFamily

const val DELHI_LAT = 28.6139
const val DELHI_LNG = 77.2090
const val DELHI_ZOOM = 12

private const val MAPBOX_TOKEN_PLACEHOLDER = "YOUR_MAPBOX_ACCESS_TOKEN"

// Full-screen Leaflet map centered on Delhi (28.6139, 77.2090) zoom 12.
// Reads BuildConfig.MAPBOX_ACCESS_TOKEN injected via Secrets Gradle Plugin (.env / .env.example).
// Uses Mapbox Streets v12 tiles when MAPBOX_ACCESS_TOKEN is configured in the AI Studio Secrets panel,
// and falls back seamlessly to CARTO Voyager OpenStreetMap tiles if unconfigured or on tile error.
// Fits route bounds inside the visible viewport area between the top directions card and the bottom sheet.
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapView(
    centerLat: Double = DELHI_LAT,
    centerLng: Double = DELHI_LNG,
    zoom: Int = DELHI_ZOOM,
    startPlace: DelhiPlace? = null,
    endPlace: DelhiPlace? = null,
    selectedRoute: Route? = null,
    allRoutes: List<Route> = emptyList(),
    isRoutesSheetOpen: Boolean = false,
    recenterTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val pageLoaded = remember { mutableStateOf(false) }

    val mapboxToken = remember {
        val token = BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
        if (token.isEmpty() || token == MAPBOX_TOKEN_PLACEHOLDER) {
            Log.i("LeafletMapView", "MAPBOX_ACCESS_TOKEN not configured in Secrets panel; using OpenStreetMap Voyager fallback.")
            ""
        } else {
            token
        }
    }

    val leafletHtml = remember(centerLat, centerLng, zoom, mapboxToken) {
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <style>
                * {
                    box-sizing: border-box;
                    margin: 0;
                    padding: 0;
                    user-select: none;
                    -webkit-user-select: none;
                    -webkit-tap-highlight-color: transparent;
                }
                html, body {
                    width: 100%;
                    height: 100%;
                    background: #f1f3f4;
                    font-family: Roboto, sans-serif;
                    overflow: hidden;
                }
                #map {
                    width: 100%;
                    height: 100%;
                    background: #f1f3f4;
                }
                .leaflet-control-attribution {
                    display: none !important;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
                var DELHI_CENTER = [$centerLat, $centerLng];
                var DEFAULT_ZOOM = $zoom;
                var MAPBOX_TOKEN = "$mapboxToken";
                var map = null;
                var startMarker = null;
                var endMarker = null;
                var routeLayers = [];

                function addFallbackTiles() {
                    if (!map) return;
                    var voyagerTiles = L.tileLayer(
                        'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
                        {
                            subdomains: 'abcd',
                            maxZoom: 19
                        }
                    );
                    var hotFallbackSwitched = false;
                    voyagerTiles.on('tileerror', function() {
                        if (!hotFallbackSwitched && map) {
                            hotFallbackSwitched = true;
                            L.tileLayer(
                                'https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png',
                                { maxZoom: 19 }
                            ).addTo(map);
                        }
                    });
                    voyagerTiles.addTo(map);
                }

                function initMap() {
                    if (typeof L === 'undefined') return;
                    map = L.map('map', {
                        center: DELHI_CENTER,
                        zoom: DEFAULT_ZOOM,
                        zoomControl: false,
                        attributionControl: false
                    });

                    if (MAPBOX_TOKEN && MAPBOX_TOKEN.length > 0) {
                        var mapboxTiles = L.tileLayer(
                            'https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/512/{z}/{x}/{y}@2x?access_token=' + encodeURIComponent(MAPBOX_TOKEN),
                            {
                                tileSize: 512,
                                zoomOffset: -1,
                                maxZoom: 19
                            }
                        );
                        var fallbackSwitched = false;
                        mapboxTiles.on('tileerror', function() {
                            if (!fallbackSwitched && map) {
                                fallbackSwitched = true;
                                map.removeLayer(mapboxTiles);
                                addFallbackTiles();
                            }
                        });
                        mapboxTiles.addTo(map);
                    } else {
                        addFallbackTiles();
                    }
                }

                window.recenterToDelhi = function() {
                    if (map) {
                        map.setView(DELHI_CENTER, DEFAULT_ZOOM, {
                            animate: true,
                            duration: 0.2
                        });
                    }
                };

                window.updateTripMarkers = function(hasStart, sLat, sLng, sName, hasEnd, eLat, eLng, eName, selectedPath, altPaths, isSheetOpen) {
                    if (!map || typeof L === 'undefined') return;

                    if (startMarker) {
                        map.removeLayer(startMarker);
                        startMarker = null;
                    }
                    if (endMarker) {
                        map.removeLayer(endMarker);
                        endMarker = null;
                    }
                    for (var i = 0; i < routeLayers.length; i++) {
                        map.removeLayer(routeLayers[i]);
                    }
                    routeLayers = [];

                    // Draw alternate routes first (lighter blue with white outline, like Google Maps)
                    if (Array.isArray(altPaths)) {
                        for (var a = 0; a < altPaths.length; a++) {
                            var altPts = altPaths[a];
                            if (Array.isArray(altPts) && altPts.length >= 2) {
                                var altOutline = L.polyline(altPts, {
                                    color: '#ffffff',
                                    weight: 8,
                                    opacity: 0.9,
                                    lineCap: 'round',
                                    lineJoin: 'round'
                                }).addTo(map);
                                var altLine = L.polyline(altPts, {
                                    color: '#8ab4f8',
                                    weight: 5,
                                    opacity: 0.95,
                                    lineCap: 'round',
                                    lineJoin: 'round'
                                }).addTo(map);
                                routeLayers.push(altOutline);
                                routeLayers.push(altLine);
                            }
                        }
                    }

                    // Draw selected route on top (#1a73e8 width 6 with #ffffff 2px outline under it = weight 10)
                    var primaryPolyline = null;
                    if (Array.isArray(selectedPath) && selectedPath.length >= 2) {
                        var selOutline = L.polyline(selectedPath, {
                            color: '#ffffff',
                            weight: 10,
                            opacity: 1.0,
                            lineCap: 'round',
                            lineJoin: 'round'
                        }).addTo(map);

                        primaryPolyline = L.polyline(selectedPath, {
                            color: '#1a73e8',
                            weight: 6,
                            opacity: 1.0,
                            lineCap: 'round',
                            lineJoin: 'round'
                        }).addTo(map);

                        routeLayers.push(selOutline);
                        routeLayers.push(primaryPolyline);
                    }

                    if (hasStart) {
                        startMarker = L.circleMarker([sLat, sLng], {
                            radius: 8,
                            color: '#ffffff',
                            weight: 2,
                            fillColor: '#1a73e8',
                            fillOpacity: 1.0
                        }).addTo(map).bindTooltip(sName, { direction: 'top' });
                    }

                    if (hasEnd) {
                        endMarker = L.circleMarker([eLat, eLng], {
                            radius: 8,
                            color: '#ffffff',
                            weight: 2,
                            fillColor: '#d93025',
                            fillOpacity: 1.0
                        }).addTo(map).bindTooltip(eName, { direction: 'top' });
                    }

                    // Compute viewport-aware padding so markers & route fit cleanly between top bar and bottom sheet
                    var mapH = map.getSize().y || 720;
                    var topPad = isSheetOpen ? Math.round(mapH * 0.24) : 140;
                    var bottomPad = isSheetOpen ? Math.round(mapH * 0.50) : 72;

                    if (primaryPolyline) {
                        map.fitBounds(primaryPolyline.getBounds(), {
                            paddingTopLeft: [28, topPad],
                            paddingBottomRight: [28, bottomPad],
                            maxZoom: 14,
                            animate: true,
                            duration: 0.2
                        });
                    } else if (hasStart && hasEnd) {
                        var bounds = L.latLngBounds([ [sLat, sLng], [eLat, eLng] ]);
                        map.fitBounds(bounds, {
                            paddingTopLeft: [28, topPad],
                            paddingBottomRight: [28, bottomPad],
                            maxZoom: 14,
                            animate: true,
                            duration: 0.2
                        });
                    } else if (hasStart) {
                        map.setView([sLat, sLng], 13, { animate: true, duration: 0.2 });
                    } else if (hasEnd) {
                        map.setView([eLat, eLng], 13, { animate: true, duration: 0.2 });
                    }
                };

                initMap();
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    fun syncMarkersToWebView(webView: WebView?) {
        if (webView == null) return
        val hasStart = startPlace != null
        val sLat = startPlace?.lat ?: 0.0
        val sLng = startPlace?.lng ?: 0.0
        val sName = (startPlace?.name ?: "").replace("'", "\\'")

        val hasEnd = endPlace != null
        val eLat = endPlace?.lat ?: 0.0
        val eLng = endPlace?.lng ?: 0.0
        val eName = (endPlace?.name ?: "").replace("'", "\\'")

        val selectedPathJson = selectedRoute?.path?.joinToString(prefix = "[", postfix = "]") { (lat, lng) ->
            "[$lat,$lng]"
        } ?: "[]"

        val altRoutesJson = allRoutes
            .filter { it.id != selectedRoute?.id }
            .joinToString(prefix = "[", postfix = "]") { route ->
                route.path.joinToString(prefix = "[", postfix = "]") { (lat, lng) ->
                    "[$lat,$lng]"
                }
            }

        val js = "window.updateTripMarkers && window.updateTripMarkers($hasStart, $sLat, $sLng, '$sName', $hasEnd, $eLat, $eLng, '$eName', $selectedPathJson, $altRoutesJson, $isRoutesSheetOpen);"
        webView.evaluateJavascript(js, null)
    }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            webViewRef.value?.evaluateJavascript("window.recenterToDelhi && window.recenterToDelhi();", null)
        }
    }

    LaunchedEffect(startPlace, endPlace, selectedRoute, allRoutes, isRoutesSheetOpen, pageLoaded.value) {
        if (pageLoaded.value) {
            syncMarkersToWebView(webViewRef.value)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapsBackground)
            .testTag("leaflet_map_container")
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("leaflet_webview"),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isLongClickable = false
                    setOnLongClickListener { true }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            pageLoaded.value = true
                            syncMarkersToWebView(view)
                        }
                    }
                    loadDataWithBaseURL(
                        "https://app.commutebuddy.local/",
                        leafletHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webViewRef.value = this
                }
            },
            update = { webView ->
                webViewRef.value = webView
            }
        )

        // Single clean attribution at bottom-right (8px grid padding)
        Text(
            text = if (mapboxToken.isNotEmpty()) "© Mapbox © OpenStreetMap" else "© OpenStreetMap contributors",
            fontFamily = RobotoFontFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MapsTextSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(MapsSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("osm_attribution")
        )
    }
}
