package ir.mums.stufood.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.ui.components.LoadingDots

/**
 * Login screen.
 *
 * Captcha row: image + reload + a narrow 4-char code field, all inline, ~10% bigger
 * than the previous pass for the image/reload button, with the image itself given a
 * border and a slight zoom/crop to eat the few pixels of blank margin the site's
 * captcha images tend to have.
 *
 * `.imePadding()` on the scrolling container plus `Arrangement.Center` (rather than
 * pinning to the very center of the *whole* screen) means the card rides up with the
 * keyboard instead of being covered by it.
 *
 * The `LaunchedEffect(Unit)` below is what makes sure a fresh captcha is fetched
 * every time this screen is actually entered — including right after logging out,
 * not just on the very first cold-start composition (see the doc comment on
 * [LoginViewModel] for why this can't live in the ViewModel's `init` alone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    vm: LoginViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        vm.loadLoginPage()
    }

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
                title = { Text("Bananite · Login") },
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
                .imePadding() // pushes content up above the keyboard instead of hiding behind it
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(8.dp)
                    // Extra bottom padding shifts the visual center of the centered
                    // card upward, so it sits a bit higher even before the keyboard
                    // opens, and further above it once it does.
                    .padding(bottom = 60.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Bananite",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Log in to reserve meals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))

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

                    // ---- Captcha row: image | reload | code field that fills the
                    // rest of the row, so it lines up with the fields above it ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(58.dp) // ~48dp -> ~10% bigger, then a bit more for the border
                                .width(116.dp) // ~96dp -> ~10% bigger, then a bit more for the border
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            when (state) {
                                is LoginUiState.Loading, LoginUiState.Submitting -> {
                                    LoadingDots(dotSize = 6.dp)
                                }
                                is LoginUiState.PageReady -> {
                                    val bitmap = (state as LoginUiState.PageReady).captcha
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Captcha image",
                                            contentScale = ContentScale.Crop,
                                            // Crops the blank margin the site's captcha
                                            // images tend to have around the actual code.
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .scale(1.18f)
                                        )
                                    } else {
                                        Text(
                                            "Failed",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                LoginUiState.Success -> {}
                            }
                        }

                        IconButton(
                            onClick = { vm.loadLoginPage() },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(46.dp) // bumped up again — a bit bigger to tap
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh captcha",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        OutlinedTextField(
                            value = captcha,
                            onValueChange = { if (it.length <= 4) vm.updateCaptcha(it) },
                            label = { Text("Captcha") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            // Fills whatever's left in the row — matches the full
                            // width of the fields above instead of looking cramped.
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // Change to Arrangement.Center if you want the entire group centered on screen
                        horizontalArrangement = Arrangement.Start, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = rememberMe,
                                onValueChange = vm::updateRememberMe,
                                role = Role.Checkbox
                            )
                            .padding(horizontal = 10.dp) // Adds horizontal spacing only (no vertical extra)
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = null // Set to null so the Row handles the touch state
                        )
                        Spacer(modifier = Modifier.width(12.dp)) // Horizontal space between checkmark & label
                        Text("Remember me on this device")
                    }

                    Spacer(Modifier.height(2.dp))
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
