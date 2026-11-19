package com.ultravideoclips

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

private const val API_BASE =
    "https://ultra-video-clips-api.ultraloader.workers.dev"

private val DarkBackground = Color(0xFF080D18)
private val CardBackground = Color(0xFF121C2D)
private val AccentCyan = Color(0xFF00D9FF)
private val TextWhite = Color(0xFFF4F8FF)
private val MutedText = Color(0xFF9BAAC0)

data class InspectionResult(
    val success: Boolean,
    val message: String,
    val contentType: String = "",
    val fileSize: String = "",
    val qualityStatus: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltraVideoClipsApp()
        }
    }
}

private fun inspectVideoUrl(videoUrl: String): InspectionResult {
    var connection: HttpURLConnection? = null

    return try {
        connection = (URL(
            "$API_BASE/api/video/inspect"
        ).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty(
                "Content-Type",
                "application/json"
            )
            setRequestProperty(
                "Accept",
                "application/json"
            )
        }

        val requestBody = JSONObject().apply {
            put("url", videoUrl)
        }

        OutputStreamWriter(
            connection.outputStream,
            Charsets.UTF_8
        ).use {
            it.write(requestBody.toString())
        }

        val responseCode = connection.responseCode

        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val responseText = stream?.let {
            BufferedReader(
                InputStreamReader(it, Charsets.UTF_8)
            ).use { reader ->
                reader.readText()
            }
        } ?: ""

        val json = JSONObject(responseText)
        val ok = json.optBoolean("ok", false)

        if (!ok || responseCode !in 200..299) {
            InspectionResult(
                success = false,
                message = json.optString(
                    "error",
                    "Server returned HTTP $responseCode."
                )
            )
        } else {
            val source = json.optJSONObject("source")
            val quality = json.optJSONObject(
                "qualityDetection"
            )

            val type = source?.optString(
                "contentType",
                "Unknown"
            ) ?: "Unknown"

            val bytes = source?.optLong(
                "contentLengthBytes",
                -1L
            ) ?: -1L

            val size = if (bytes >= 0L) {
                String.format(
                    "%.2f MB",
                    bytes / (1024.0 * 1024.0)
                )
            } else {
                "Unknown"
            }

            InspectionResult(
                success = true,
                message = "Video source verified by Cloudflare API.",
                contentType = type,
                fileSize = size,
                qualityStatus = quality?.optString(
                    "status",
                    "unknown"
                ) ?: "unknown"
            )
        }
    } catch (e: Exception) {
        InspectionResult(
            success = false,
            message = e.localizedMessage
                ?: "Could not connect to the API."
        )
    } finally {
        connection?.disconnect()
    }
}

@Composable
fun UltraVideoClipsApp() {
    var videoUrl by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf("720p") }
    var statusMessage by remember { mutableStateOf("") }
    var isInspecting by remember { mutableStateOf(false) }
    var inspection by remember {
        mutableStateOf<InspectionResult?>(null)
    }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

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
                Spacer(Modifier.height(18.dp))

                Text(
                    "ULTRA",
                    color = AccentCyan,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                )

                Text(
                    "VIDEO CLIPS",
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "VIDEO DOWNLOADER & CLIP STUDIO",
                    color = MutedText,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )

                Spacer(Modifier.height(30.dp))

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
                                "Paste permitted direct video URL",
                                color = MutedText
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor =
                                Color(0xFF34445C),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = AccentCyan
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        )
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val cleanUrl = videoUrl.trim()

                            if (!cleanUrl.startsWith("https://")) {
                                statusMessage =
                                    "Please enter a valid HTTPS video URL."
                            } else if (!isInspecting) {
                                isInspecting = true
                                statusMessage =
                                    "Connecting to Cloudflare API..."

                                Thread {
                                    val result =
                                        inspectVideoUrl(cleanUrl)

                                    mainHandler.post {
                                        isInspecting = false
                                        inspection = result

                                        if (result.success) {
                                            submitted = true
                                            selectedMode = ""
                                            statusMessage =
                                                result.message
                                        } else {
                                            statusMessage =
                                                result.message
                                        }
                                    }
                                }.start()
                            }
                        },
                        enabled = !isInspecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isInspecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("CHECKING VIDEO...")
                        } else {
                            Text(
                                "SUBMIT",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (statusMessage.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            statusMessage,
                            color = Color(0xFFFF7777),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    SectionTitle("SELECT YOUR MODE")

                    inspection?.let { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF123329)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                Modifier.padding(14.dp)
                            ) {
                                Text(
                                    "✓ VIDEO SOURCE VERIFIED",
                                    color = Color(0xFF69E6A5),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Content type: ${result.contentType}",
                                    color = TextWhite,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "File size: ${result.fileSize}",
                                    color = TextWhite,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "Quality detection: ${result.qualityStatus}",
                                    color = MutedText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    ModeCard(
                        "FULL VIDEO",
                        "Download the complete video",
                        selectedMode == "FULL VIDEO"
                    ) {
                        selectedMode = "FULL VIDEO"
                        statusMessage = ""
                    }

                    ModeCard(
                        "CLIPS",
                        "Create multiple short video clips",
                        selectedMode == "CLIPS"
                    ) {
                        selectedMode = "CLIPS"
                        statusMessage = ""
                    }

                    ModeCard(
                        "FULL MP3",
                        "Extract the complete audio",
                        selectedMode == "FULL MP3"
                    ) {
                        selectedMode = "FULL MP3"
                        statusMessage = ""
                    }

                    Spacer(Modifier.height(20.dp))

                    if (selectedMode == "FULL VIDEO") {
                        SectionTitle("VIDEO QUALITY")

                        Text(
                            "Choose a preferred quality. Actual source " +
                                "availability is not detected yet.",
                            color = MutedText,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(14.dp))

                        qualities.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { quality ->
                                    FilterChip(
                                        selected =
                                            selectedQuality == quality,
                                        onClick = {
                                            selectedQuality = quality
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
                                            FilterChipDefaults
                                                .filterChipColors(
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

                        Spacer(Modifier.height(16.dp))

                        FeaturePlaceholder(
                            "PROCESSOR NOT CONNECTED",
                            "Cloudflare has verified the video URL. " +
                                "Actual quality detection and downloading " +
                                "require the separate FFmpeg processing service."
                        )
                    } else if (selectedMode == "CLIPS") {
                        FeaturePlaceholder(
                            "CLIPS PROCESSING",
                            "The video source is verified. Clip extraction " +
                                "will be enabled after the FFmpeg and AI " +
                                "processing service is connected."
                        )
                    } else if (selectedMode == "FULL MP3") {
                        FeaturePlaceholder(
                            "MP3 PROCESSING",
                            "Audio extraction will be enabled after " +
                                "the media-processing service is connected."
                        )
                    } else {
                        Text(
                            "Select an option above to continue.",
                            color = MutedText
                        )
                    }

                    if (statusMessage.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            statusMessage,
                            color = AccentCyan,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    TextButton(
                        onClick = {
                            submitted = false
                            selectedMode = ""
                            selectedQuality = "720p"
                            statusMessage = ""
                            inspection = null
                        }
                    ) {
                        Text(
                            "CHANGE VIDEO URL",
                            color = AccentCyan
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

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

                Spacer(Modifier.height(12.dp))
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
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (selected) AccentCyan else TextWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(5.dp))
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
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                color = AccentCyan,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                description,
                color = MutedText,
                fontSize = 13.sp
            )
        }
    }
}
