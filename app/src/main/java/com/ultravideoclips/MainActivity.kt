package com.ultravideoclips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBackground = Color(0xFF080D18)
private val CardBackground = Color(0xFF121C2D)
private val AccentCyan = Color(0xFF00D9FF)
private val TextWhite = Color(0xFFF4F8FF)
private val MutedText = Color(0xFF9BAAC0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UltraVideoClipsApp()
        }
    }
}

@Composable
fun UltraVideoClipsApp() {
    var videoUrl by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf("720p") }
    var statusMessage by remember { mutableStateOf("") }

    val qualities = listOf(
        "144p", "240p", "360p", "480p",
        "720p", "1080p", "2K", "4K"
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentCyan,
            background = DarkBackground,
            surface = CardBackground,
            onPrimary = Color.Black,
            onBackground = TextWhite,
            onSurface = TextWhite
        )
    ) {
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

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "ULTRA",
                    color = AccentCyan,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                )

                Text(
                    text = "VIDEO CLIPS",
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "VIDEO DOWNLOADER & CLIP STUDIO",
                    color = MutedText,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(30.dp))

                if (!submitted) {

                    SectionTitle("VIDEO SOURCE")

                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = {
                            videoUrl = it
                            statusMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Paste your permitted video URL",
                                color = MutedText
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Color(0xFF34445C),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = AccentCyan
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {}
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (videoUrl.trim().startsWith("http://") ||
                                videoUrl.trim().startsWith("https://")
                            ) {
                                submitted = true
                                statusMessage = ""
                            } else {
                                statusMessage =
                                    "Please enter a valid video URL."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            "SUBMIT",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (statusMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            statusMessage,
                            color = Color(0xFFFF7777)
                        )
                    }

                } else {

                    SectionTitle("SELECT YOUR MODE")

                    ModeCard(
                        title = "FULL VIDEO",
                        description = "Download the complete video",
                        selected = selectedMode == "FULL VIDEO",
                        onClick = {
                            selectedMode = "FULL VIDEO"
                            statusMessage = ""
                        }
                    )

                    ModeCard(
                        title = "CLIPS",
                        description = "Create multiple short video clips",
                        selected = selectedMode == "CLIPS",
                        onClick = {
                            selectedMode = "CLIPS"
                            statusMessage = ""
                        }
                    )

                    ModeCard(
                        title = "FULL MP3",
                        description = "Extract the complete audio",
                        selected = selectedMode == "FULL MP3",
                        onClick = {
                            selectedMode = "FULL MP3"
                            statusMessage = ""
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (selectedMode == "FULL VIDEO") {

                        SectionTitle("VIDEO QUALITY")

                        Text(
                            text = "Choose your preferred quality",
                            color = MutedText,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        qualities.chunked(2).forEach { rowQualities ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(12.dp)
                            ) {
                                rowQualities.forEach { quality ->
                                    FilterChip(
                                        selected =
                                            selectedQuality == quality,
                                        onClick = {
                                            selectedQuality = quality
                                            statusMessage = ""
                                        },
                                        label = {
                                            Text(
                                                quality,
                                                fontWeight =
                                                    FontWeight.Bold
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor =
                                                    AccentCyan,
                                                selectedLabelColor =
                                                    Color.Black,
                                                labelColor = TextWhite
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = CardBackground
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    "SELECTED QUALITY",
                                    color = MutedText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    selectedQuality,
                                    color = AccentCyan,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                Text(
                                    "Actual availability will be checked " +
                                        "from the video source in the next " +
                                        "backend development step.",
                                    color = MutedText,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                statusMessage =
                                    "Quality selection saved. " +
                                    "Video source detection and downloading " +
                                    "will be connected in the next step."
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                "DOWNLOAD VIDEO",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }

                    } else if (selectedMode == "CLIPS") {

                        FeaturePlaceholder(
                            "CLIPS",
                            "Clip duration, quantity and moment " +
                                "selection will be added in the next step."
                        )

                    } else if (selectedMode == "FULL MP3") {

                        FeaturePlaceholder(
                            "FULL MP3",
                            "Audio quality selection and MP3 " +
                                "processing will be added in the next step."
                        )

                    } else {

                        Text(
                            "Select an option above to continue.",
                            color = MutedText,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    if (statusMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF17283A)
                            )
                        ) {
                            Text(
                                statusMessage,
                                modifier = Modifier.padding(14.dp),
                                color = AccentCyan,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    TextButton(
                        onClick = {
                            submitted = false
                            selectedMode = ""
                            selectedQuality = "720p"
                            statusMessage = ""
                        }
                    ) {
                        Text(
                            "CHANGE VIDEO URL",
                            color = AccentCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    "ULTRA VIDEO CLIPS",
                    color = MutedText,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                Text(
                    "Use media you own or have permission to download.",
                    color = MutedText,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        color = AccentCyan,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@Composable
fun ModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                Color(0xFF183449)
            } else {
                CardBackground
            }
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                AccentCyan
            )
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    title,
                    color = if (selected) AccentCyan else TextWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    description,
                    color = MutedText,
                    fontSize = 12.sp
                )
            }

            Text(
                if (selected) "✓" else "›",
                color = AccentCyan,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FeaturePlaceholder(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                title,
                color = AccentCyan,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                description,
                color = MutedText,
                fontSize = 13.sp
            )
        }
    }
}
