package com.salihraphics.meow.domain.model

enum class AccountProvider { GOOGLE }

/**
 * Optional account state. Signing in only identifies the student; it never uploads or syncs
 * study data by itself. Sync is a separate, explicit opt-in (see docs/ACCOUNTS_AND_SYNC.md).
 */
sealed interface AccountState {
    /** The default. Every feature works without an account. */
    data object SignedOut : AccountState

    data class SignedIn(
        val provider: AccountProvider,
        /** Stable provider subject ID; never a password or token. */
        val accountId: String,
        val displayName: String?,
        val email: String?,
    ) : AccountState
}
