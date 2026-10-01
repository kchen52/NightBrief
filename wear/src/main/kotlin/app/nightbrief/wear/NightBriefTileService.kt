package app.nightbrief.wear

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class NightBriefTileService : TileService(), DataClient.OnDataChangedListener {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        runCatching { Wearable.getDataClient(this).addListener(this) }
    }

    override fun onDestroy() {
        runCatching { Wearable.getDataClient(this).removeListener(this) }
        executor.shutdown()
        super.onDestroy()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.close()
        getUpdater(this).requestUpdate(NightBriefTileService::class.java)
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        Futures.submit(
            Callable { NightBriefTile.build(this, WearGlanceLoader.load(this)) },
            executor,
        )

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<androidx.wear.protolayout.ResourceBuilders.Resources> =
        Futures.immediateFuture(NightBriefTile.resources())
}
