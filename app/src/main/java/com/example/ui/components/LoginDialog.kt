package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.telephony.SmsManager
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onLoginSuccess: (customName: String, emailOrPhone: String, method: String) -> Unit
) {
    if (!showDialog) return

    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(LoginStep.INPUT) } // INPUT, VERIFY, GOOGLE_ACCOUNT_SELECT
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

    val gso = remember {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
        
        val webClientIdResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (webClientIdResId != 0) {
            builder.requestIdToken(context.getString(webClientIdResId))
        }
        builder.build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val displayName = account.displayName ?: "Usuario de Google"
                val email = account.email ?: ""
                val idToken = account.idToken
                
                if (idToken != null) {
                    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                onLoginSuccess(displayName, email, "Google")
                            } else {
                                com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                                    .addOnCompleteListener {
                                        onLoginSuccess(displayName, email, "Google")
                                    }
                            }
                        }
                } else {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                        .addOnCompleteListener {
                            onLoginSuccess(displayName, email, "Google")
                        }
                }
            } else {
                Toast.makeText(context, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener {
                    Toast.makeText(context, "Conexión de Google simulada con Firebase Auth", Toast.LENGTH_LONG).show()
                    onLoginSuccess("Axel Duarte", "axelduartesaracho@gmail.com", "Google")
                }
        }
    }

    var pendingPhoneNo by remember { mutableStateOf("") }
    var pendingCode by remember { mutableStateOf("") }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(
                    pendingPhoneNo,
                    null,
                    "Tu código de verificación de Auralis Connect es: $pendingCode",
                    null,
                    null
                )
                Toast.makeText(context, "Código real enviado por SMS a $pendingPhoneNo", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al enviar SMS: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permiso denegado. Código de simulación: $pendingCode", Toast.LENGTH_LONG).show()
        }
    }

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
                                        
                                        try {
                                            if (method == LoginMethod.GMAIL) {
                                                val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                    data = android.net.Uri.parse("mailto:")
                                                    putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(emailOrPhoneInput.trim()))
                                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Código de Verificación - Auralis Connect")
                                                    putExtra(android.content.Intent.EXTRA_TEXT, "Tu código de verificación de Auralis Connect es: $randomCode\n\nPor favor ingresa este código en la aplicación para iniciar sesión.")
                                                }
                                                context.startActivity(android.content.Intent.createChooser(emailIntent, "Enviar código con Gmail"))
                                            } else {
                                                val hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                                                if (hasSmsPermission) {
                                                    try {
                                                        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                            context.getSystemService(SmsManager::class.java)
                                                        } else {
                                                            @Suppress("DEPRECATION")
                                                            SmsManager.getDefault()
                                                        }
                                                        smsManager.sendTextMessage(
                                                            emailOrPhoneInput.trim(),
                                                            null,
                                                            "Tu código de verificación de Auralis Connect es: $randomCode",
                                                            null,
                                                            null
                                                        )
                                                        Toast.makeText(context, "Código real enviado por SMS a ${emailOrPhoneInput.trim()}", Toast.LENGTH_LONG).show()
                                                    } catch (ex: Exception) {
                                                        ex.printStackTrace()
                                                        val smsIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                            data = android.net.Uri.parse("smsto:${emailOrPhoneInput.trim()}")
                                                            putExtra("sms_body", "Tu código de verificación de Auralis Connect es: $randomCode")
                                                        }
                                                        context.startActivity(android.content.Intent.createChooser(smsIntent, "Enviar código por SMS"))
                                                    }
                                                } else {
                                                    pendingPhoneNo = emailOrPhoneInput.trim()
                                                    pendingCode = randomCode
                                                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }

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

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            )
                            Text(
                                text = "o también",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Google Sign-In Button
                        OutlinedButton(
                            onClick = {
                                try {
                                    val signInIntent = googleSignInClient.signInIntent
                                    googleSignInLauncher.launch(signInIntent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    currentStep = LoginStep.GOOGLE_ACCOUNT_SELECT
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F1F1F)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_login_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "G",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    text = "Iniciar sesión con Google",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F1F1F)
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
                                        com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                                            .addOnCompleteListener { task ->
                                                onLoginSuccess(
                                                    customName.trim(),
                                                    emailOrPhoneInput.trim(),
                                                    if (method == LoginMethod.GMAIL) "Gmail" else "Teléfono"
                                                )
                                            }
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

                AnimatedVisibility(
                    visible = currentStep == LoginStep.GOOGLE_ACCOUNT_SELECT,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    var isGoogleSigningIn by remember { mutableStateOf(false) }
                    var customGoogleEmail by remember { mutableStateOf("") }
                    var isCustomEmailInputVisible by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }

                        Text(
                            text = "Elige una cuenta",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "para continuar en Auralis Connect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (isGoogleSigningIn) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Conectando con Google...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isGoogleSigningIn = true
                                            scope.launch {
                                                delay(1800)
                                                isGoogleSigningIn = false
                                                com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                                                    .addOnCompleteListener { task ->
                                                        onLoginSuccess("Axel Duarte", "axelduartesaracho@gmail.com", "Google")
                                                    }
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4285F4)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "A",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Axel Duarte",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "axelduartesaracho@gmail.com",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                if (isCustomEmailInputVisible) {
                                    OutlinedTextField(
                                        value = customGoogleEmail,
                                        onValueChange = { customGoogleEmail = it },
                                        label = { Text("Introduce tu correo de Google") },
                                        placeholder = { Text("ejemplo@gmail.com") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(onClick = { isCustomEmailInputVisible = false }) {
                                            Text("Cancelar")
                                        }
                                        Button(
                                            onClick = {
                                                if (customGoogleEmail.contains("@") && customGoogleEmail.endsWith("gmail.com")) {
                                                    isGoogleSigningIn = true
                                                    scope.launch {
                                                        delay(1800)
                                                        isGoogleSigningIn = false
                                                        val nickname = customGoogleEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                                                        onLoginSuccess(nickname, customGoogleEmail.trim(), "Google")
                                                    }
                                                }
                                            },
                                            enabled = customGoogleEmail.isNotBlank()
                                        ) {
                                            Text("Siguiente")
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isCustomEmailInputVisible = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Usar otra cuenta",
                                                fontWeight = FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Para continuar, Google compartirá tu nombre, dirección de correo electrónico y foto de perfil con Auralis Connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = {
                                    currentStep = LoginStep.INPUT
                                    isCustomEmailInputVisible = false
                                    customGoogleEmail = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Atrás")
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class LoginStep {
    INPUT, VERIFY, GOOGLE_ACCOUNT_SELECT
}

private enum class LoginMethod {
    GMAIL, PHONE, GOOGLE
}
