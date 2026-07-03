package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onLoginSuccess: (customName: String, emailOrPhone: String, method: String) -> Unit
) {
    if (!showDialog) return

    var currentStep by remember { mutableStateOf(LoginStep.INPUT) } // INPUT, VERIFYing, SUCCESS
    var method by remember { mutableStateOf(LoginMethod.GMAIL) }
    var customName by remember { mutableStateOf("") }
    var emailOrPhoneInput by remember { mutableStateOf("") }
    var verificationCodeInput by remember { mutableStateOf("") }

    // Errors
    var customNameError by remember { mutableStateOf<String?>(null) }
    var emailOrPhoneError by remember { mutableStateOf<String?>(null) }
    var verificationError by remember { mutableStateOf<String?>(null) }

    // Sent code placeholder
    var mockSentCode by remember { mutableStateOf("1234") }
    var isSendingCode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("login_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentStep == LoginStep.VERIFY) "Verificación" else "Iniciar Sesión",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_login_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                )

                AnimatedVisibility(
                    visible = currentStep == LoginStep.INPUT,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Por favor ingresa un nombre personalizado y tu método de acceso preferido para compartir música con tus amigos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 1. Custom Name Field
                        OutlinedTextField(
                            value = customName,
                            onValueChange = {
                                customName = it
                                customNameError = null
                            },
                            label = { Text("Nombre Personalizado") },
                            placeholder = { Text("Ej. Alex, Rockero99...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null
                                )
                            },
                            isError = customNameError != null,
                            supportingText = {
                                customNameError?.let {
                                    Text(text = it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_custom_name_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Select Method
                        Text(
                            text = "Método de Acceso",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            Modifier
                                .selectableGroup()
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Gmail Option
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .selectable(
                                        selected = method == LoginMethod.GMAIL,
                                        onClick = {
                                            method = LoginMethod.GMAIL
                                            emailOrPhoneInput = ""
                                            emailOrPhoneError = null
                                        },
                                        role = Role.RadioButton
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (method == LoginMethod.GMAIL) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = if (method == LoginMethod.GMAIL) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Gmail",
                                        fontWeight = FontWeight.Bold,
                                        color = if (method == LoginMethod.GMAIL) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            // Phone Option
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .selectable(
                                        selected = method == LoginMethod.PHONE,
                                        onClick = {
                                            method = LoginMethod.PHONE
                                            emailOrPhoneInput = ""
                                            emailOrPhoneError = null
                                        },
                                        role = Role.RadioButton
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (method == LoginMethod.PHONE) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (method == LoginMethod.PHONE) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Teléfono",
                                        fontWeight = FontWeight.Bold,
                                        color = if (method == LoginMethod.PHONE) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        // 3. Email or Phone Input
                        val inputLabel = if (method == LoginMethod.GMAIL) "Correo Gmail" else "Número de Teléfono"
                        val inputPlaceholder = if (method == LoginMethod.GMAIL) "ejemplo@gmail.com" else "10 dígitos (Ej. 5512345678)"
                        val inputIcon = if (method == LoginMethod.GMAIL) Icons.Default.AlternateEmail else Icons.Default.PhoneAndroid
                        val keyboardType = if (method == LoginMethod.GMAIL) KeyboardType.Email else KeyboardType.Phone

                        OutlinedTextField(
                            value = emailOrPhoneInput,
                            onValueChange = {
                                emailOrPhoneInput = it
                                emailOrPhoneError = null
                            },
                            label = { Text(inputLabel) },
                            placeholder = { Text(inputPlaceholder) },
                            leadingIcon = { Icon(imageVector = inputIcon, contentDescription = null) },
                            isError = emailOrPhoneError != null,
                            supportingText = {
                                emailOrPhoneError?.let {
                                    Text(text = it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_credential_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                var hasError = false
                                if (customName.trim().length < 2) {
                                    customNameError = "El nombre debe tener al menos 2 caracteres"
                                    hasError = true
                                }
                                if (method == LoginMethod.GMAIL) {
                                    val isGmail = emailOrPhoneInput.contains("@") && emailOrPhoneInput.endsWith("gmail.com")
                                    if (!isGmail) {
                                        emailOrPhoneError = "Introduce una dirección de Gmail válida (@gmail.com)"
                                        hasError = true
                                    }
                                } else {
                                    val cleanPhone = emailOrPhoneInput.filter { it.isDigit() }
                                    if (cleanPhone.length < 8 || cleanPhone.length > 15) {
                                        emailOrPhoneError = "Introduce un número de teléfono válido (8-15 dígitos)"
                                        hasError = true
                                    }
                                }

                                if (!hasError) {
                                    scope.launch {
                                        isSendingCode = true
                                        // Generates a random 4 digit code
                                        val randomCode = (1000..9999).random().toString()
                                        mockSentCode = randomCode
                                        delay(1500) // Realistic delay
                                        isSendingCode = false
                                        currentStep = LoginStep.VERIFY
                                    }
                                }
                            },
                            enabled = !isSendingCode,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_submit_request_code_btn")
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Enviando código...")
                            } else {
                                Text(
                                    "Obtener Código de Verificación",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = currentStep == LoginStep.VERIFY,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 12.dp)
                        )

                        Text(
                            text = "Código enviado a ${if (method == LoginMethod.GMAIL) "tu correo" else "tu número"}.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        // Display the generated mock verification code so the user knows what to type!
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "Código Simulado: $mockSentCode",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Text(
                            text = "Por favor ingresa el código de 4 dígitos para completar el registro de tu cuenta.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = verificationCodeInput,
                            onValueChange = {
                                if (it.length <= 4) {
                                    verificationCodeInput = it
                                    verificationError = null
                                }
                            },
                            label = { Text("Código de 4 dígitos") },
                            placeholder = { Text("0 0 0 0") },
                            leadingIcon = { Icon(imageVector = Icons.Default.LockClock, contentDescription = null) },
                            isError = verificationError != null,
                            supportingText = {
                                verificationError?.let {
                                    Text(text = it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .testTag("login_verification_code_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentStep = LoginStep.INPUT
                                    verificationCodeInput = ""
                                    verificationError = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Atrás")
                            }

                            Button(
                                onClick = {
                                    if (verificationCodeInput == mockSentCode) {
                                        onLoginSuccess(
                                            customName.trim(),
                                            emailOrPhoneInput.trim(),
                                            if (method == LoginMethod.GMAIL) "Gmail" else "Teléfono"
                                        )
                                    } else {
                                        verificationError = "El código ingresado es incorrecto. Prueba con $mockSentCode"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp)
                                    .testTag("login_verify_code_confirm_btn")
                            ) {
                                Text("Verificar e Iniciar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class LoginStep {
    INPUT, VERIFY
}

private enum class LoginMethod {
    GMAIL, PHONE
}
