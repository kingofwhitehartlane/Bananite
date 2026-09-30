package ir.mums.stufood.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.StufoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ReceiveExchangeVM"
private const val FRIENDLY_ERROR = "Something went wrong. Please try again in a moment."

class ReceiveExchangeViewModel(
    private val repo: StufoodRepository = BananiteApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceiveExchangeUiState>(ReceiveExchangeUiState.Idle)
    val uiState: StateFlow<ReceiveExchangeUiState> = _uiState

    // Same nonce trick as ReservationViewModel so repeated identical errors still show.
    data class ErrorEvent(val message: String, val id: Long = System.nanoTime())
    private val _errorMessage = MutableStateFlow<ErrorEvent?>(null)
    val errorMessage: StateFlow<ErrorEvent?> = _errorMessage

    val hapticFeedbackEnabled: StateFlow<Boolean> =
        BananiteApp.instance.userPrefs.hapticFeedbackEnabled
            .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun load() {
        _uiState.value = ReceiveExchangeUiState.Loading
        viewModelScope.launch {
            try {
                // TODO: replace with the real repo call once the page is ready, e.g.
                // val offers = repo.fetchReceivableExchanges()
                val offers = emptyList<ExchangeOffer>()
                _uiState.value = ReceiveExchangeUiState.Ready(offers)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load exchange offers", t)
                _errorMessage.value = ErrorEvent(FRIENDLY_ERROR)
                _uiState.value = ReceiveExchangeUiState.Idle
            }
        }
    }

    fun receive(offer: ExchangeOffer) {
        // TODO: repo.receiveExchange(offer) -> then reload
    }
}

/** Placeholder model, reshape it to match whatever the site's page actually exposes. */
data class ExchangeOffer(
    val id: String,
    val foodName: String,
    val cafeteria: String?,
    val priceToman: String?
)

sealed class ReceiveExchangeUiState {
    object Idle : ReceiveExchangeUiState()
    object Loading : ReceiveExchangeUiState()
    data class Ready(val offers: List<ExchangeOffer>) : ReceiveExchangeUiState()
}