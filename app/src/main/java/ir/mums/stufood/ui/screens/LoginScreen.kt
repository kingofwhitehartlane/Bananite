package ir.mums.stufood.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Login screen.
 *
 * Layout: a vertically scrolling card containing username / password / captcha image
 * (with refresh button) / captcha text field / remember-me checkbox / submit button.
 *
 * The captcha image is rendered from the bytes the repository fetched — the user solves
 * it by typing into the captcha field, no AI involved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    vm: LoginViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val username by vm.username.collectAsState()
    val password by vm.password.collectAsState()
    val captcha by vm.captcha.collectAsState()
    val rememberMe by vm.rememberMe.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHost.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StuFood · Login") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "MUMS Student Food",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Log in to reserve meals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = vm::updateUsername,
                        label = { Text("Student ID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    var showPass by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = vm::updatePassword,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    imageVector = if (showPass) Icons.Default.VisibilityOff
                                                  else Icons.Default.Visibility,
                                    contentDescription = if (showPass) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---- Captcha row ----
                    Text("Captcha", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 140.dp, height = 50.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            when (state) {
                                is LoginUiState.Loading, LoginUiState.Submitting -> {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                                is LoginUiState.PageReady -> {
                                    val bitmap = (state as LoginUiState.PageReady).captcha
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Captcha image",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            "Failed to load",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                LoginUiState.Success -> {}
                            }
                        }

                        IconButton(onClick = { vm.loadLoginPage() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh captcha")
                        }
                    }

                    OutlinedTextField(
                        value = captcha,
                        onValueChange = vm::updateCaptcha,
                        label = { Text("Captcha answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = vm::updateRememberMe
                        )
                        Text("Remember me on this device")
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.submit(onLoggedIn) },
                        enabled = state !is LoginUiState.Submitting &&
                                  state !is LoginUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (state is LoginUiState.Submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Logging in…")
                        } else {
                            Text("Log in")
                        }
                    }
                }
            }
        }
    }
}
