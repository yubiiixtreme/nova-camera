package com.novacamera.presentation.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.novacamera.presentation.camera.VaultViewModel
import com.novacamera.security.BiometricGate
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit, vm: VaultViewModel = hiltViewModel()) {
    val unlocked by vm.unlocked.collectAsState()
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("Private Vault") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } })
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            if (!unlocked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vault locked — authenticate to view", modifier = Modifier.padding(bottom = 12.dp))
                    Button(onClick = {
                        val activity = context as? FragmentActivity ?: return@Button
                        // Resolve BiometricGate via entry point ( composable has no field injection).
                        val entry = EntryPointAccessors.fromApplication(
                            context.applicationContext, VaultEntryPoint::class.java,
                        )
                        entry.gate().authenticate(activity, onSuccess = { vm.setUnlocked(true) }, onError = {})
                    }) { Text("Unlock with biometrics") }
                }
            } else {
                Text("Vault is unlocked. Encrypted files are decrypted on demand only.")
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface VaultEntryPoint {
    fun gate(): BiometricGate
}
