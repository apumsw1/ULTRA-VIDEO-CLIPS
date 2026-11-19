package com.ultravideoclips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBackground = Color(0xFF090D18)
private val PanelColor = Color(0xFF151D30)
private val AccentColor = Color(0xFF00D5FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                UltraVideoClipsApp()
            }
        }
    }
}

@Composable
fun UltraVideoClipsApp() {
    var videoLink by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            Text(
                text = "ULTRA",
                color = AccentColor,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "VIDEO CLIPS",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "VIDEO • CLIPS • AUDIO",
                color = Color.LightGray,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(35.dp))

            Text(
                text = "Paste Your Video Link",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = videoLink,
                onValueChange = {
                    videoLink = it
                    submitted = false
                    selectedMode = ""
                    message = ""
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Paste a permitted video URL")
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = Color.Gray,
                    focusedPlaceholderColor = Color.Gray,
                    unfocusedPlaceholderColor = Color.Gray,
                    cursorColor = AccentColor
                )
            )

            Spacer(Modifier.height(15.dp))

            Button(
                onClick = {
                    if (videoLink.isBlank()) {
                        message = "Please paste a video link first."
                    } else {
                        submitted = true
                        message = "Link received. Select an option below."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    "SUBMIT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(message, color = AccentColor, fontSize = 13.sp)
            }

            if (submitted) {
                Spacer(Modifier.height(30.dp))

                Text(
                    "CHOOSE YOUR MODE",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(18.dp))

                ModeCard(
                    title = "FULL VIDEO",
                    description = "Download a complete video from an authorized source.",
                    symbol = "▶",
                    selected = selectedMode == "FULL VIDEO",
                    onClick = { selectedMode = "FULL VIDEO" }
                )

                ModeCard(
                    title = "CLIPS",
                    description = "Prepare separate highlight clips from permitted media.",
                    symbol = "✂",
                    selected = selectedMode == "CLIPS",
                    onClick = { selectedMode = "CLIPS" }
                )

                ModeCard(
                    title = "FULL MP3",
                    description = "Extract the complete audio from media you may process.",
                    symbol = "♫",
                    selected = selectedMode == "FULL MP3",
                    onClick = { selectedMode = "FULL MP3" }
                )

                if (selectedMode.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "$selectedMode selected",
                        color = AccentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Processing features will be connected in the next development step.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(35.dp))

            HorizontalDivider(color = Color.DarkGray)

            Spacer(Modifier.height(15.dp))

            Text(
                "ULTRA VIDEO CLIPS",
                color = Color.Gray,
                fontSize = 11.sp
            )
            Text(
                "Use only media you own or have permission to download and edit.",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ModeCard(
    title: String,
    description: String,
    symbol: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF17374A) else PanelColor
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, AccentColor)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                symbol,
                color = AccentColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.width(18.dp))

            Column {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    description,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
