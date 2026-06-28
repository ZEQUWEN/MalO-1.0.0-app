package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PaywallDialog(
    onDismiss: () -> Unit,
    onProPurchased: () -> Unit
) {
    var step by remember { mutableStateOf(0) } // 0: Select, 1: Loading, 2: Success, 3: Error
    val scpSurface = Color(0xFF161616)
    val scpNeonPurple = Color(0xFFBB86FC)
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (step == 0 || step >= 2) onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = scpSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, scpNeonPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == 0) {
                    Text(
                        text = "Улучши связь с MalO",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Base Plan
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Тариф Base", color = Color.LightGray, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("Бесплатно", color = Color.Gray, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                            Text("• Общение через локальную базу\n• Базовые уведомления", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Pro Plan
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(1.dp, scpNeonPurple, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Тариф Pro", color = scpNeonPurple, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("$5 / месяц", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                            Text("• Все функции Base\n• Полноценный API Gemini\n• Распознавание файлов и голоса\n• Генерация жутких фото MalO", color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Text("Выберите способ оплаты:", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                step = 1
                                coroutineScope.launch {
                                    delay(2500)
                                    if (kotlin.random.Random.nextFloat() < 0.2f) {
                                        step = 3 // Error
                                    } else {
                                        step = 2 // Success
                                        delay(1500)
                                        onProPurchased()
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
                        ) {
                            Text("Карта", color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                step = 1
                                coroutineScope.launch {
                                    delay(2500)
                                    if (kotlin.random.Random.nextFloat() < 0.2f) {
                                        step = 3 // Error
                                    } else {
                                        step = 2 // Success
                                        delay(1500)
                                        onProPurchased()
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7931A))
                        ) {
                            Text("Крипта (OKX/Bybit)", color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                } else if (step == 1) {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(color = scpNeonPurple)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Обработка транзакции...", color = Color.White, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(32.dp))
                } else if (step == 2) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF03DAC5), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Оплата успешно прошла!\nДоступ к Pro открыт.", color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 32.dp))
                } else if (step == 3) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Icon(Icons.Default.Close, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Ошибка при оплате.", color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 32.dp))
                    TextButton(onClick = { step = 0 }) {
                        Text("Попробовать снова", color = scpNeonPurple, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
