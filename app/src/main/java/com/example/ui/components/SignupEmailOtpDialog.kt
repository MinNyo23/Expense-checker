package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.CoupleExpenseViewModel

@Composable
fun SignupEmailOtpDialog(
    viewModel: CoupleExpenseViewModel,
    email: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Verify your email") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "We sent a six-digit verification code to $email. Enter it to finish creating your account.",
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it.filter(Char::isDigit).take(6)
                        feedback = null
                    },
                    label = { Text("Verification code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                feedback?.let {
                    Text(
                        text = it,
                        color = if (isError) androidx.compose.material3.MaterialTheme.colorScheme.error
                        else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isBusy = true
                    feedback = null
                    viewModel.verifySignupOtp(email, token) { success, message ->
                        isBusy = false
                        if (success) onVerified() else {
                            isError = true
                            feedback = message
                        }
                    }
                },
                enabled = !isBusy && token.length == 6,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                if (isBusy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                else Text("Verify")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    isBusy = true
                    viewModel.resendSignupOtp(email) { success, message ->
                        isBusy = false
                        isError = !success
                        feedback = message
                    }
                },
                enabled = !isBusy
            ) {
                Text("Resend code")
            }
        }
    )
}
