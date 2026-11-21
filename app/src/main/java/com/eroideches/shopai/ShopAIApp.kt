package com.eroideches.shopai

import android.app.Application
import com.eroideches.shopai.data.AppDatabase
import com.eroideches.shopai.data.RulesRepository
import com.eroideches.shopai.data.SavedRepository
import com.eroideches.shopai.data.SecureKeyStore
import com.eroideches.shopai.data.SettingsRepository
import com.eroideches.shopai.net.Http
import com.eroideches.shopai.net.WebViewFetcher
import com.eroideches.shopai.search.SearchEngine
import com.eroideches.shopai.sites.ApiClients
import com.eroideches.shopai.sites.SiteSearcher
import com.eroideches.shopai.work.Notifier
import com.eroideches.shopai.work.PriceCheckScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Dipendenze dell'app, create una volta sola (niente framework di DI: è un'app personale). */
class AppGraph(app: Application) {
    val cacheDir: java.io.File = app.cacheDir
    @Volatile var lastZip: String = "20096"
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = Http(app)
    val db = AppDatabase.create(app)
    val settings = SettingsRepository(app)
    val keys = SecureKeyStore(app)
    val rules = RulesRepository(app, http.plain)
    val webviews = WebViewFetcher(app)
    val api = ApiClients(http, keys)
    val searcher = SiteSearcher(app, http, webviews, api)
    val engine = SearchEngine(searcher, db)
    val saved = SavedRepository(db)
    val notifier = Notifier(app)
    /** Offerta aperta nel dettaglio (passata tra le schermate senza serializzarla nell'URL). */
    @Volatile var openOffer: com.eroideches.shopai.core.model.Offer? = null
    @Volatile var openQuery: String = ""
}

class ShopAIApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.notifier.createChannel()
        graph.scope.launch {
            graph.rules.refresh()
            val s = graph.settings.current()
            PriceCheckScheduler.schedule(this@ShopAIApp, s.checkHours, s.wifiOnly)
        }
    }
}
