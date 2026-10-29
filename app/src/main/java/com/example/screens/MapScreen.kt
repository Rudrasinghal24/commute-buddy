package com.example.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.components.BottomSheet
import com.example.components.ChatBar
import com.example.components.DELHI_LAT
import com.example.components.DELHI_LNG
import com.example.components.DELHI_ZOOM
import com.example.components.LeafletMapView
import com.example.components.LucideInfoIcon
import com.example.components.LucideXIcon
import com.example.components.MyLocationButton
import com.example.components.RouteCard
import com.example.components.RouteCardSkeleton
import com.example.components.SearchBar
import com.example.components.StepByStepView
import com.example.data.DataLoadState
import com.example.lib.LocalTripContext
import com.example.lib.TripContextProvider
import com.example.ui.theme.MapsAccentBlue
import com.example.ui.theme.MapsBackground
import com.example.ui.theme.MapsBorder
import com.example.ui.theme.MapsSelectedTint
import com.example.ui.theme.MapsSurface
import com.example.ui.theme.MapsTextPrimary
import com.example.ui.theme.MapsTextSecondary

// MapScreen with solid white status bar area, floating SearchBar, LeafletMapView, round 48px MyLocationButton,
// draggable 3-snap-point BottomSheet sitting ABOVE the fixed bottom ChatBar, and full-screen SavedScreen.
@Composable
fun MapScreen(
    modifier: Modifier = Modifier
) {
    TripContextProvider {
        MapScreenContent(modifier = modifier)
    }
}

@Composable
private fun MapScreenContent(
    modifier: Modifier = Modifier
) {
    val tripContext = LocalTripContext.current
    var recenterTrigger by rememberSaveable { mutableIntStateOf(0) }
    var isChatExpanded by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapsBackground)
            .testTag("map_screen")
    ) {
        // Vertical layout: solid white status bar at top, map + route sheet in middle, fixed 48px ChatBar at bottom
        Column(modifier = Modifier.fillMaxSize()) {
            // Solid white (#ffffff) status bar background so map never bleeds behind status bar icons
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MapsSurface)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LeafletMapView(
                    centerLat = DELHI_LAT,
                    centerLng = DELHI_LNG,
                    zoom = DELHI_ZOOM,
                    startPlace = tripContext.startPlace,
                    endPlace = tripContext.endPlace,
                    selectedRoute = tripContext.selectedRoute,
                    allRoutes = tripContext.orderedRoutes,
                    isRoutesSheetOpen = tripContext.isRoutesSheetOpen,
                    recenterTrigger = recenterTrigger,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating SearchBar at the top (16px margin, 48px pill or animated directions card + mode chips)
                SearchBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                )

                // Round "my location" button (white, 48px, soft shadow) on the right side above the bottom area
                MyLocationButton(
                    onClick = { recenterTrigger += 1 },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = if (tripContext.isRoutesSheetOpen) 112.dp else 24.dp
                        )
                )

                // Draggable 3-snap-point BottomSheet sitting directly above the ChatBar
                if (tripContext.isRoutesSheetOpen && !isChatExpanded) {
                    BottomSheet(
                        snapPoint = tripContext.sheetSnapPoint,
                        onSnapPointChange = { tripContext.updateSheetSnapPoint(it) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        val activeStepRoute = tripContext.selectedRoute
                        if (tripContext.isStepByStepOpen && activeStepRoute != null) {
                            StepByStepView(
                                route = activeStepRoute,
                                loadState = tripContext.routesLoadState,
                                onBack = { tripContext.closeStepByStep() },
                                onRetry = { tripContext.triggerRouteSearch() }
                            )
                        } else {
                            RoutesSheetContent()
                        }
                    }
                }
            }

            // Collapsed 48px ChatBar sits below the map & route sheet container
            if (!isChatExpanded) {
                ChatBar(
                    isExpanded = false,
                    onExpandedChange = { isChatExpanded = it }
                )
            }
        }

        // Expanded 70% height ChatBar panel anchored at the bottom
        if (isChatExpanded) {
            ChatBar(
                isExpanded = true,
                onExpandedChange = { isChatExpanded = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Full-screen white Saved screen when opened from the top search pill bookmark icon
        if (tripContext.isSavedScreenOpen) {
            SavedScreen(
                onBack = { tripContext.closeSavedScreen() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RoutesSheetContent() {
    val tripContext = LocalTripContext.current
    val loadState = tripContext.routesLoadState
    val orderedRoutes = tripContext.orderedRoutes
    val recommendedId = tripContext.recommendedRoute?.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${tripContext.selectedMode.label} routes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MapsTextPrimary
                )
                val subtitle = if (tripContext.reachByTime != null) {
                    "Reach by ${tripContext.reachByTime}"
                } else {
                    "Departing now"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MapsTextSecondary
                )
            }

            IconButton(
                onClick = { tripContext.setRoutesSheetOpenState(false) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("close_routes_sheet_button")
            ) {
                LucideXIcon(
                    tint = MapsTextSecondary,
                    iconSize = 24.dp,
                    contentDesc = "Close routes sheet"
                )
            }
        }

        when (loadState) {
            DataLoadState.LOADING -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("routes_skeleton_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(3) {
                        RouteCardSkeleton()
                    }
                }
            }

            DataLoadState.EMPTY -> {
                RoutesEmptyOrErrorBox(
                    isError = false,
                    message = "No routes found",
                    onRetry = { tripContext.triggerRouteSearch() }
                )
            }

            DataLoadState.ERROR -> {
                RoutesEmptyOrErrorBox(
                    isError = true,
                    message = "Unable to load routes",
                    onRetry = { tripContext.triggerRouteSearch() }
                )
            }

            DataLoadState.READY -> {
                if (orderedRoutes.isEmpty()) {
                    RoutesEmptyOrErrorBox(
                        isError = false,
                        message = "No routes found",
                        onRetry = { tripContext.triggerRouteSearch() }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("routes_results_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(orderedRoutes, key = { it.id }) { route ->
                            RouteCard(
                                route = route,
                                allRoutes = orderedRoutes,
                                reachByTime = tripContext.reachByTime,
                                isRecommended = route.id == recommendedId,
                                isSelected = route.id == tripContext.selectedRoute?.id,
                                isBookmarked = tripContext.isCurrentTripBookmarked,
                                onClick = { tripContext.selectRoute(route) },
                                onStartClick = { tripContext.openStepByStep(route) },
                                onBookmarkClick = { tripContext.toggleBookmarkCurrentTrip() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutesEmptyOrErrorBox(
    isError: Boolean,
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MapsSurface,
        border = BorderStroke(1.dp, MapsBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(if (isError) "routes_error_state" else "routes_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LucideInfoIcon(
                tint = MapsTextSecondary,
                iconSize = 24.dp,
                contentDesc = message
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall,
                color = MapsTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = CircleShape,
                border = BorderStroke(1.dp, MapsAccentBlue),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MapsSelectedTint,
                    contentColor = MapsAccentBlue
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("routes_retry_button")
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
