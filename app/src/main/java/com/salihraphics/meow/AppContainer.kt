package com.salihraphics.meow

import android.content.Context
import com.salihraphics.meow.data.account.AccountRepository
import com.salihraphics.meow.data.account.LocalOnlyAccountRepository
import com.salihraphics.meow.data.preferences.DataStoreUserPreferencesRepository
import com.salihraphics.meow.data.preferences.UserPreferencesRepository

/** Manual dependency wiring. Small on purpose; swap for Hilt only if it starts to hurt. */
class AppContainer(context: Context) {
    val userPreferencesRepository: UserPreferencesRepository =
        DataStoreUserPreferencesRepository(context)

    val accountRepository: AccountRepository = LocalOnlyAccountRepository()
}
