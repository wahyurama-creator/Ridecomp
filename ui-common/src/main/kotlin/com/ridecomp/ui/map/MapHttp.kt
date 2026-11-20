package com.ridecomp.ui.map

import android.net.TrafficStats
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

/**
 * MapLibre's HTTP client with tagged sockets (D-132): its default client opens untagged sockets, which
 * StrictMode reports (STB-01) on every online map. OkHttp creates a socket before connecting it (to set its
 * timeout), so the tag is set on the worker threads themselves: every socket they create carries it. Same
 * OkHttp version MapLibre ships; pool and per-host limit as MapLibre's and OkHttp's defaults.
 */
internal object MapHttp {
    /** Traffic stats tag of map tile requests (RideComp's own tags are in `:platform` TrafficTags). */
    private const val TRAFFIC_TAG = 0x5243_0004

    /** MapLibre's default per-host limit. */
    private const val MAX_REQUESTS_PER_HOST = 20

    /** OkHttp's default idle time of a worker thread. */
    private const val KEEP_ALIVE_S = 60L
    private const val THREAD_NAME = "RideComp map http"

    @Volatile
    private var isInstalled = false

    /** Gives MapLibre the client once per process; call after `MapLibre.getInstance`. */
    fun install() {
        if (isInstalled) return
        val executor = ThreadPoolExecutor(
            0,
            Int.MAX_VALUE,
            KEEP_ALIVE_S,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            TaggedThreads,
        )
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher(executor).apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
            .build()
        HttpRequestUtil.setOkHttpClient(client)
        isInstalled = true
    }

    /** Worker threads whose sockets are all tagged as map traffic. */
    private object TaggedThreads : ThreadFactory {
        override fun newThread(task: Runnable): Thread = Thread(
            {
                TrafficStats.setThreadStatsTag(TRAFFIC_TAG)
                task.run()
            },
            THREAD_NAME,
        ).apply { isDaemon = true }
    }
}
