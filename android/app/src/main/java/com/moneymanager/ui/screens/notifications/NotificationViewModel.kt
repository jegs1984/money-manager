package com.moneymanager.ui.screens.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.usecase.ParseBankNotificationUseCase
import com.moneymanager.domain.usecase.ParsedTransaction
import com.moneymanager.notifications.util.NotificationPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class NotificationUiState(
    val permissionGranted: Boolean     = false,
    val recentTransaction: ParsedTransaction? = null,
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parseNotification: ParseBankNotificationUseCase,
) : ViewModel() {

    private val _permission = MutableStateFlow(NotificationPermissionHelper.isGranted(context))

    /**
     * Latest parsed transaction from the bank notification stream.
     *
     * WhileSubscribed(5_000): keeps the upstream SharedFlow subscription alive for 5 s
     * after the last UI subscriber disappears — survives brief recompositions without
     * missing in-flight emissions.
     */
    val latestTransaction: StateFlow<ParsedTransaction?> =
        parseNotification()
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val permissionGranted: StateFlow<Boolean> = _permission

    /** Call from UI's onResume / LaunchedEffect to re-check after settings round-trip. */
    fun refreshPermission() {
        _permission.update { NotificationPermissionHelper.isGranted(context) }
    }

    fun openPermissionSettings() {
        NotificationPermissionHelper.openSettings(context)
    }
}
