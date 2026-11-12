package com.nousresearch.hermesmobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.sms.AndroidSmsPlatform
import com.nousresearch.hermesmobile.sms.SmsSendResult
import com.nousresearch.hermesmobile.sms.SmsSendService
import com.nousresearch.hermesmobile.ui.components.HermesTopBar

/** Local hardware test harness for the native sender; sends only after an explicit button tap. */
@Composable
fun SmsTestScreen(onMenu: () -> Unit) {
    val context = LocalContext.current
    var recipient by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var statusResource by remember { mutableStateOf<Int?>(null) }
    var queuedPartCount by remember { mutableStateOf(0) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        statusResource = if (granted) null else R.string.sms_permission_denied
    }
    val service = remember(context) { SmsSendService(AndroidSmsPlatform(context)) }

    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            title = stringResource(R.string.sms_title),
            leading = {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Default.Menu, stringResource(R.string.a21_menu))
                }
            }
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.sms_test_warning), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it; statusResource = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sms_recipient)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it; statusResource = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sms_message)) },
                minLines = 3,
                maxLines = 8
            )
            if (!permissionGranted) {
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.SEND_SMS) }) {
                    Text(stringResource(R.string.sms_allow_permission))
                }
            }
            Button(
                onClick = {
                    val result = service.send(recipient, message)
                    queuedPartCount = (result as? SmsSendResult.Queued)?.partCount ?: 0
                    statusResource = when (result) {
                        is SmsSendResult.Queued -> R.string.sms_queued
                        SmsSendResult.InvalidRecipient -> R.string.sms_invalid_recipient
                        SmsSendResult.EmptyMessage -> R.string.sms_empty_message
                        SmsSendResult.UnsupportedDevice -> R.string.sms_unsupported_device
                        SmsSendResult.PermissionRequired -> R.string.sms_permission_denied
                        SmsSendResult.MessageTooLong -> R.string.sms_message_too_long
                        SmsSendResult.SendFailed -> R.string.sms_send_failed
                    }
                },
                enabled = permissionGranted && recipient.isNotBlank() && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sms_send_now))
            }
            statusResource?.let { resourceId ->
                Spacer(Modifier.height(2.dp))
                val text = if (resourceId == R.string.sms_queued) stringResource(resourceId, queuedPartCount)
                    else stringResource(resourceId)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
