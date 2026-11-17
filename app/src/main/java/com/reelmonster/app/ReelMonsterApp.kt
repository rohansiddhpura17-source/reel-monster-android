package com.reelmonster.app

import android.app.Application
import android.content.Context
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ReelMonsterApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var dbHelper: ReelDbHelper
        private set

    lateinit var preferencesManager: UserPreferencesManager
        private set

    lateinit var repository: ReelRepository
        private set

    companion object {
        private var _instance: ReelMonsterApp? = null
        private var fallbackRepository: ReelRepository? = null

        val instance: ReelMonsterApp
            get() = _instance ?: throw IllegalStateException("ReelMonsterApp not initialized")

        fun getInstanceOrNull(): ReelMonsterApp? = _instance

        @Synchronized
        fun getOrCreateRepository(context: Context): ReelRepository {
            fallbackRepository?.let { return it }
            val app = (context.applicationContext as? ReelMonsterApp) ?: _instance
            if (app != null && app::repository.isInitialized) {
                return app.repository
            }
            val appCtx = context.applicationContext ?: context
            return ReelRepository(
                dbHelper = ReelDbHelper(appCtx),
                preferencesManager = UserPreferencesManager(appCtx),
                preloadOnInit = true
            ).also { fallbackRepository = it }
        }

        internal fun setRepositoryForTesting(repo: ReelRepository?) {
            fallbackRepository = repo
        }
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this

        dbHelper = ReelDbHelper(this)
        preferencesManager = UserPreferencesManager(this)
        repository = ReelRepository(
            dbHelper = dbHelper,
            preferencesManager = preferencesManager,
            preloadOnInit = false
        )
        fallbackRepository = repository
        repository.initializeInBackground(appScope)
    }
}
