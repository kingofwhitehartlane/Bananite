package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.ui.components.HapticType
import ir.mums.stufood.ui.components.LoadingDots
import ir.mums.stufood.ui.components.rememberHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveExchangeScreen(
    onBack: () -> Unit,
    vm: ReceiveExchangeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val error by vm.errorMessage.collectAsState()
    val hapticEnabled by vm.hapticFeedbackEnabled.collectAsState()
    val haptic = rememberHapticFeedback(enabled = hapticEnabled)
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(error) { error?.let { snackbarHost.showSnackbar(it.message) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Exchange Food") },
                navigationIcon = {
                    IconButton(onClick = { haptic(HapticType.CLICK); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                ReceiveExchangeUiState.Idle,
                ReceiveExchangeUiState.Loading -> LoadingDots()
                is ReceiveExchangeUiState.Ready -> {
                    if (s.offers.isEmpty()) {
                        Text(
                            "No exchange food available right now.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // TODO: LazyColumn of offer cards
                    }
                }
            }
        }
    }
}