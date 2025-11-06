package app.insidepacer.backup.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.insidepacer.R

@Composable
fun BackupSettingsCard(viewModel: BackupSettingsViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is BackupSettingsViewModel.BackupUiEvent.Message -> event.text
                is BackupSettingsViewModel.BackupUiEvent.Error -> event.text
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Account & Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (uiState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(uiState.privacyMessage, style = MaterialTheme.typography.bodySmall)
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(uiState.lastBackupText, style = MaterialTheme.typography.bodyMedium)
            Text(uiState.lastRestoreText, style = MaterialTheme.typography.bodyMedium)
            if (uiState.isSignedIn) {
                Text(
                    text = context.getString(R.string.backup_signed_in_status, uiState.accountEmail ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!uiState.isSignedIn) {
                    Button(
                        onClick = { viewModel.onSignInClicked() },
                        enabled = uiState.canSignIn,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = context.getString(R.string.backup_sign_in))
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.onBackupNowClicked() },
                        enabled = uiState.canBackupNow,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = context.getString(R.string.backup_now))
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.onRestoreClicked() },
                    enabled = uiState.canRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = context.getString(R.string.backup_restore))
                }
            }
            if (uiState.isSignedIn) {
                TextButton(
                    onClick = { viewModel.onSignOutClicked() },
                    enabled = uiState.canSignOut,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = context.getString(R.string.backup_sign_out))
                }
            }
        }
    }
}
