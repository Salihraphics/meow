package com.salihraphics.meow.data.account

import com.salihraphics.meow.domain.model.AccountState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AccountRepository {
    val account: Flow<AccountState>

    /**
     * Whether a real sign-in flow is wired up. While false, the UI must not offer a sign-in
     * button, only explain that it is coming.
     */
    val isSignInAvailable: Boolean
}

/**
 * The current implementation: MEOW is local-only. Google sign-in (Credential Manager) will
 * replace this once an OAuth client is configured for the project.
 */
class LocalOnlyAccountRepository : AccountRepository {
    override val account: Flow<AccountState> = flowOf(AccountState.SignedOut)
    override val isSignInAvailable: Boolean = false
}
