package com.example.lib

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.components.SheetSnapPoint
import com.example.data.DataLoadState
import com.example.data.DelhiPlace

// Single-select travel modes ("Mix" is default and allows switching vehicles)
enum class TravelMode(val label: String) {
    MIX("Mix"),
    METRO("Metro"),
    BUS("Bus"),
    TRAIN("Train"),
    CAR("Car"),
    BIKE("Bike"),
    WALK("Walk")
}

// Context holder for trip start/end places, travel mode, Reach by time, generated routes, and saved routes
@Stable
class TripStateHolder(
    private val appContext: Context? = null
) {
    var startPlace by mutableStateOf<DelhiPlace?>(null)
        private set

    var endPlace by mutableStateOf<DelhiPlace?>(null)
        private set

    var selectedMode by mutableStateOf(TravelMode.MIX)
        private set

    var reachByTime by mutableStateOf<String?>(null)
        private set

    var isRoutesSheetOpen by mutableStateOf(false)
        private set

    var sheetSnapPoint by mutableStateOf(SheetSnapPoint.HALF)
        private set

    var routeSearchCount by mutableIntStateOf(0)
        private set

    var routes by mutableStateOf<List<Route>>(emptyList())
        private set

    var recommendedRoute by mutableStateOf<Route?>(null)
        private set

    var selectedRoute by mutableStateOf<Route?>(null)
        private set

    var isStepByStepOpen by mutableStateOf(false)
        private set

    var isSavedScreenOpen by mutableStateOf(false)
        private set

    var savedRoutes by mutableStateOf<List<SavedRouteEntry>>(
        appContext?.let { SavedRoutesStore.loadAll(it) } ?: emptyList()
    )
        private set

    var routesLoadState by mutableStateOf(DataLoadState.READY)
        private set

    val hasBothPlaces: Boolean
        get() = startPlace != null && endPlace != null

    val isCurrentTripBookmarked: Boolean
        get() {
            val s = startPlace ?: return false
            val e = endPlace ?: return false
            val id = SavedRoutesStore.buildId(s, e, selectedMode)
            return savedRoutes.any { it.id == id }
        }

    // Ordered routes with the Recommended route placed first
    val orderedRoutes: List<Route>
        get() {
            val recId = recommendedRoute?.id ?: return routes
            val rec = routes.find { it.id == recId } ?: return routes
            return listOf(rec) + routes.filter { it.id != recId }
        }

    private fun persistSavedRoutes(updated: List<SavedRouteEntry>) {
        savedRoutes = updated
        appContext?.let { SavedRoutesStore.saveAll(it, updated) }
    }

    fun toggleBookmarkCurrentTrip() {
        val s = startPlace ?: return
        val e = endPlace ?: return
        val id = SavedRoutesStore.buildId(s, e, selectedMode)
        val exists = savedRoutes.any { it.id == id }
        val next = if (exists) {
            savedRoutes.filterNot { it.id == id }
        } else {
            listOf(
                SavedRouteEntry(
                    id = id,
                    start = s,
                    end = e,
                    vehicle = selectedMode
                )
            ) + savedRoutes
        }
        persistSavedRoutes(next)
    }

    fun removeSavedRoute(id: String) {
        persistSavedRoutes(savedRoutes.filterNot { it.id == id })
    }

    fun restoreSavedRoute(entry: SavedRouteEntry, index: Int) {
        val filtered = savedRoutes.filterNot { it.id == entry.id }.toMutableList()
        val targetIndex = index.coerceIn(0, filtered.size)
        filtered.add(targetIndex, entry)
        persistSavedRoutes(filtered)
    }

    fun openSavedScreen() {
        isSavedScreenOpen = true
    }

    fun closeSavedScreen() {
        isSavedScreenOpen = false
    }

    // Fills the trip, runs a fresh search, and returns to the map
    fun checkSavedRouteNow(entry: SavedRouteEntry) {
        startPlace = entry.start
        endPlace = entry.end
        selectedMode = entry.vehicle
        isSavedScreenOpen = false
        isStepByStepOpen = false
        triggerRouteSearch()
    }

    fun updateStart(place: DelhiPlace?) {
        startPlace = place
        isStepByStepOpen = false
        if (place == null) {
            isRoutesSheetOpen = false
            routes = emptyList()
            recommendedRoute = null
            selectedRoute = null
        } else if (endPlace != null) {
            triggerRouteSearch()
        }
    }

    fun updateEnd(place: DelhiPlace?) {
        endPlace = place
        isStepByStepOpen = false
        if (place == null) {
            isRoutesSheetOpen = false
            routes = emptyList()
            recommendedRoute = null
            selectedRoute = null
        } else if (startPlace != null) {
            triggerRouteSearch()
        }
    }

    fun swapPlaces() {
        val temp = startPlace
        startPlace = endPlace
        endPlace = temp
        isStepByStepOpen = false
        if (hasBothPlaces) {
            triggerRouteSearch()
        }
    }

    // Selecting a chip triggers route search automatically (no Find button)
    fun selectTravelMode(mode: TravelMode) {
        selectedMode = mode
        isStepByStepOpen = false
        if (hasBothPlaces) {
            triggerRouteSearch()
        }
    }

    fun updateReachByTime(time: String?) {
        reachByTime = time
        isStepByStepOpen = false
        if (hasBothPlaces) {
            triggerRouteSearch()
        }
    }

    fun selectRoute(route: Route) {
        selectedRoute = route
    }

    fun openStepByStep(route: Route) {
        selectedRoute = route
        isStepByStepOpen = true
    }

    fun closeStepByStep() {
        isStepByStepOpen = false
    }

    fun updateSheetSnapPoint(snap: SheetSnapPoint) {
        sheetSnapPoint = snap
    }

    fun setRoutesSheetOpenState(open: Boolean) {
        isRoutesSheetOpen = open
        if (!open) {
            isStepByStepOpen = false
        } else if (sheetSnapPoint == SheetSnapPoint.PEEK) {
            sheetSnapPoint = SheetSnapPoint.HALF
        }
    }

    fun triggerRouteSearch() {
        isRoutesSheetOpen = true
        isStepByStepOpen = false
        sheetSnapPoint = SheetSnapPoint.HALF
        routeSearchCount += 1
    }

    internal fun onSearchLoading() {
        routesLoadState = DataLoadState.LOADING
    }

    internal fun onSearchSuccess(result: List<Route>) {
        routes = result
        val rec = Recommend.recommendRoute(result)
        recommendedRoute = rec
        selectedRoute = rec ?: result.firstOrNull()
        routesLoadState = if (result.isEmpty()) DataLoadState.EMPTY else DataLoadState.READY
    }
}

val LocalTripContext = compositionLocalOf<TripStateHolder> {
    error("LocalTripContext not provided")
}

@Composable
fun TripContextProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val holder = remember(context) { TripStateHolder(context) }
    val start = holder.startPlace
    val end = holder.endPlace
    val vehicle = holder.selectedMode
    val reachBy = holder.reachByTime
    val searchCount = holder.routeSearchCount

    LaunchedEffect(start, end, vehicle, reachBy, searchCount) {
        if (start != null && end != null && searchCount > 0) {
            holder.onSearchLoading()
            val generated = getRoutes(
                start = start,
                end = end,
                vehicle = vehicle,
                reachBy = reachBy
            )
            holder.onSearchSuccess(generated)
        }
    }

    CompositionLocalProvider(LocalTripContext provides holder) {
        content()
    }
}
