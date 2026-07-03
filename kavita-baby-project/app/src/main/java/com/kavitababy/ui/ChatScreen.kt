// ChatScreen.kt
package com.kavitababy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavitababy.R
import com.kavitababy.ai.AIRepository
import com.kavitababy.voice.VoiceManager
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(voiceManager: VoiceManager) {
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    val aiRepository = remember { AIRepository() }
    val scope = rememberCoroutineScope()

    fun send(text: String) {
        if (text.isBlank()) return
        messages = messages + Message(text, isUser = true)
        isThinking = true

        scope.launch {
            val response = try {
                aiRepository.getResponse(text)
            } catch (e: Exception) {
                "Sorry jaani, thoda dikkat aa gayi. Dobara try karo."
            }
            messages = messages + Message(response, isUser = false)
            isThinking = false
            voiceManager.speak(response)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Header with Kavita's face
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16213E))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.kavita_face),
                    contentDescription = "Kavita Baby",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "Kavita Baby 💕",
                    color = Color.White,
                    fontSize = 20.sp
                )
                Text(
                    text = when {
                        isListening -> "Sunn rahi hoon... 👂"
                        isThinking -> "Soch rahi hoon... 🤔"
                        else -> "Bolo jaani... 💬"
                    },
                    color = Color(0xFFFF6B9D),
                    fontSize = 14.sp
                )
            }
        }

        // Chat Messages
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                MessageBubble(message)
            }
        }

        // Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Button
            IconButton(
                onClick = {
                    isListening = true
                    voiceManager.startListening(
                        onResult = { text ->
                            isListening = false
                            if (text.isNotBlank()) send(text)
                        },
                        onError = {
                            isListening = false
                        }
                    )
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mic),
                    contentDescription = "Voice",
                    tint = if (isListening) Color.Red else Color(0xFFFF6B9D)
                )
            }

            // Text Input
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Kuch bolo... 💬") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Send Button
            IconButton(
                onClick = {
                    val text = inputText
                    inputText = ""
                    send(text)
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send),
                    contentDescription = "Send",
                    tint = Color(0xFFFF6B9D)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.isUser
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Image(
                painter = painterResource(id = R.drawable.kavita_face),
                contentDescription = "Kavita",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .background(
                    if (isUser) Color(0xFF4A90E2) else Color(0xFFFF6B9D),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

data class Message(val text: String, val isUser: Boolean)
