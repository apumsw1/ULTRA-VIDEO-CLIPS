package com.ultravideoclips

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val MAX_DOWNLOAD_BYTES =
    500L * 1024L * 1024L

private const val MAX_REDIRECTS = 5

private val DarkBackground = Color(0xFF080D18)
private val CardBackground = Color(0xFF121C2D)
private val AccentCyan = Color(0xFF00D9FF)
private val TextWhite = Color(0xFFF4F8FF)
private val MutedText = Color(0xFF9BAAC0)
private val SuccessGreen = Color(0xFF69E6A5)

data class InspectionResult(
    val success: Boolean,
    val isYouTube: Boolean = false,
    val message: String = ""
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UltraVideoClipsApp()
        }
    }
}

private fun isYouTubeUrl(input: String): Boolean {
    return try {
        val url = URL(input)
        val host = url.host.lowercase().removePrefix("www.")

        host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtu.be" ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    } catch (_: Exception) {
        false
    }
}

private fun isSafeHttpsUrl(input: String): Boolean {
    return try {
        val url = URL(input)

        if (url.protocol.lowercase() != "https") {
            return false
        }

        if (
            url.userInfo != null ||
            (url.port != -1 && url.port != 443)
        ) {
            return false
        }

        val host = url.host.lowercase()

        if (
            host.isBlank() ||
            host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.contains(":") ||
            host.matches(
                Regex("""\d{1,3}(\.\d{1,3}){3}""")
            )
        ) {
            return false
        }

        true
    } catch (_: Exception) {
        false
    }
}

private fun isSupportedVideoType(type: String?): Boolean {
    val contentType = type
        ?.substringBefore(";")
        ?.trim()
        ?.lowercase()
        ?: return false

    return contentType.startsWith("video/") ||
        contentType == "application/mp4" ||
        contentType == "application/octet-stream" ||
        contentType == "binary/octet-stream"
}

private fun inspectDirectVideoUrl(
    input: String
): InspectionResult {

    if (isYouTubeUrl(input)) {
        return InspectionResult(
            success = false,
            isYouTube = true,
            message = "YouTube media downloading is not supported. Select an authorized local video or direct video URL."
        )
    }

    if (!isSafeHttpsUrl(input)) {
        return InspectionResult(
            success = false,
            message = "Enter a valid public HTTPS video URL."
        )
    }

    var currentUrl = input

    repeat(MAX_REDIRECTS + 1) { redirectCount ->

        if (!isSafeHttpsUrl(currentUrl)) {
            return InspectionResult(
                false,
                message = "Unsafe redirect URL."
            )
        }

        val connection =
            URL(currentUrl).openConnection() as HttpURLConnection

        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.setRequestProperty(
                "Range",
                "bytes=0-0"
            )

            connection.setRequestProperty(
                "User-Agent",
                "ULTRA-VIDEO-CLIPS-Android"
            )

            val code = connection.responseCode

            if (code in 300..399) {
                if (redirectCount >= MAX_REDIRECTS) {
                    return InspectionResult(
                        false,
                        message = "Too many redirects."
                    )
                }

                val location =
                    connection.getHeaderField("Location")
                        ?: return InspectionResult(
                            false,
                            message = "Invalid redirect response."
                        )

                currentUrl =
                    URL(URL(currentUrl), location).toString()

                return@repeat
            }

            if (code !in 200..299 && code != 206) {
                return InspectionResult(
                    false,
                    message = "Video URL returned HTTP $code."
                )
            }

            if (!isSupportedVideoType(connection.contentType)) {
                return InspectionResult(
                    false,
                    message = "This URL does not appear to contain a supported video file."
                )
            }

            return InspectionResult(
                success = true,
                message = "Direct video URL verified successfully."
            )

        } catch (e: Exception) {
            return InspectionResult(
                false,
                message = e.localizedMessage
                    ?: "Could not inspect video URL."
            )
        } finally {
            connection.disconnect()
        }
    }

    return InspectionResult(
        false,
        message = "Unable to verify video URL."
    )
}

private fun downloadVideo(
    context: Context,
    inputUrl: String
): File {

    var currentUrl = inputUrl

    repeat(MAX_REDIRECTS + 1) { redirectCount ->

        if (!isSafeHttpsUrl(currentUrl)) {
            throw Exception("Unsafe or invalid HTTPS URL.")
        }

        val connection =
            URL(currentUrl).openConnection() as HttpURLConnection

        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20000
            connection.readTimeout = 60000

            connection.setRequestProperty(
                "User-Agent",
                "ULTRA-VIDEO-CLIPS-Android"
            )

            val code = connection.responseCode

            if (code in 300..399) {

                if (redirectCount >= MAX_REDIRECTS) {
                    throw Exception("Too many redirects.")
                }

                val location =
                    connection.getHeaderField("Location")
                        ?: throw Exception("Invalid redirect.")

                currentUrl =
                    URL(URL(currentUrl), location).toString()

                return@repeat
            }

            if (code !in 200..299) {
                throw Exception("Download failed: HTTP $code")
            }

            if (!isSupportedVideoType(connection.contentType)) {
                throw Exception("URL does not contain a supported video.")
            }

            if (
                connection.contentLengthLong > MAX_DOWNLOAD_BYTES
            ) {
                throw Exception("Video exceeds the 500 MB limit.")
            }

            val output = File(
                context.cacheDir,
                "source_${UUID.randomUUID()}.mp4"
            )

            try {
                connection.inputStream.use { input ->
                    FileOutputStream(output).use { out ->

                        val buffer = ByteArray(64 * 1024)
                        var total = 0L

                        while (true) {
                            val count = input.read(buffer)

                            if (count == -1) break

                            total += count

                            if (total > MAX_DOWNLOAD_BYTES) {
                                throw Exception(
                                    "Video exceeds the 500 MB limit."
                                )
                            }

                            out.write(buffer, 0, count)
                        }
                    }
                }

                if (output.length() == 0L) {
                    throw Exception("Downloaded video is empty.")
                }

                return output

            } catch (e: Exception) {
                output.delete()
                throw e
            }

        } finally {
            connection.disconnect()
        }
    }

    throw Exception("Unable to download video.")
}

private fun saveToGallery(
    context: Context,
    source: File,
    audioOnly: Boolean
): String {

    val extension = if (audioOnly) "m4a" else "mp4"

    if (Build.VERSION.SDK_INT < 29) {

        val folder = File(
            context.getExternalFilesDir(null),
            "ULTRA-VIDEO-CLIPS"
        )

        if (!folder.exists()) {
            folder.mkdirs()
        }

        val destination = File(
            folder,
            "ULTRA_${System.currentTimeMillis()}.$extension"
        )

        source.copyTo(destination, overwrite = true)

        return destination.absolutePath
    }

    val mime = if (audioOnly) {
        "audio/mp4"
    } else {
        "video/mp4"
    }

    val collection = if (audioOnly) {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val relativePath = if (audioOnly) {
        "Music/ULTRA VIDEO CLIPS"
    } else {
        "Movies/ULTRA VIDEO CLIPS"
    }

    val values = ContentValues().apply {
        put(
            MediaStore.MediaColumns.DISPLAY_NAME,
            "ULTRA_${System.currentTimeMillis()}.$extension"
        )

        put(MediaStore.MediaColumns.MIME_TYPE, mime)

        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            relativePath
        )

        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val uri = context.contentResolver.insert(
        collection,
        values
    ) ?: throw Exception("Could not create Gallery file.")

    try {

        context.contentResolver.openOutputStream(uri)?.use { output ->

            source.inputStream().use { input ->
                input.copyTo(output)
            }

        } ?: throw Exception("Could not write output file.")

        val completed = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }

        context.contentResolver.update(
            uri,
            completed,
            null,
            null
        )

        return uri.toString()

    } catch (e: Exception) {
        context.contentResolver.delete(uri, null, null)
        throw e
    }
}

@Composable
fun UltraVideoClipsApp() {

    val context = LocalContext.current

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    var sourceUrl by remember {
        mutableStateOf("")
    }

    var sourceUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var sourceName by remember {
        mutableStateOf("")
    }

    var selectedMode by remember {
        mutableStateOf("FULL VIDEO")
    }

    var startSeconds by remember {
        mutableStateOf("0")
    }

    var endSeconds by remember {
        mutableStateOf("30")
    }

    var inspection by remember {
        mutableStateOf<InspectionResult?>(null)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var status by remember {
        mutableStateOf("Select a video to begin.")
    }

    var outputLocation by remember {
        mutableStateOf("")
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->

        if (uri != null) {

            sourceUri = uri

            sourceName =
                uri.lastPathSegment ?: "Selected video"

            inspection = null
            outputLocation = ""
            status = "Local video selected."
        }
    }

    fun startExport(
        uri: Uri,
        mode: String,
        temporaryInput: File? = null
    ) {

        try {

            val audioOnly = mode == "FULL AUDIO"

            val startMs = if (mode == "CLIPS") {
                (startSeconds.toLongOrNull() ?: -1L) * 1000L
            } else {
                C.TIME_UNSET
            }

            val endMs = if (mode == "CLIPS") {
                (endSeconds.toLongOrNull() ?: -1L) * 1000L
            } else {
                C.TIME_UNSET
            }

            if (
                mode == "CLIPS" &&
                (startMs < 0 || endMs <= startMs)
            ) {
                busy = false
                temporaryInput?.delete()

                status = "Enter a valid clip start and end time."
                return
            }

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uri)

            if (mode == "CLIPS") {

                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
            }

            val mediaItem = mediaItemBuilder.build()

            val editedItemBuilder =
                EditedMediaItem.Builder(mediaItem)

            if (audioOnly) {
                editedItemBuilder.setRemoveVideo(true)
            }

            val editedItem = editedItemBuilder.build()

            val extension = if (audioOnly) "m4a" else "mp4"

            val outputFile = File(
                context.cacheDir,
                "export_${UUID.randomUUID()}.$extension"
            )

            status = "Processing on your Android device..."

            val transformer = Transformer.Builder(context)
                .addListener(

                    object : Transformer.Listener {

                        override fun onCompleted(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: ExportResult
                        ) {

                            mainHandler.post {

                                Thread {

                                    try {

                                        val saved = saveToGallery(
                                            context,
                                            outputFile,
                                            audioOnly
                                        )

                                        mainHandler.post {

                                            busy = false
                                            outputLocation = saved

                                            status = if (audioOnly) {
                                                "Audio export completed. Saved as M4A."
                                            } else {
                                                "Video export completed successfully."
                                            }

                                            outputFile.delete()
                                            temporaryInput?.delete()
                                        }

                                    } catch (e: Exception) {

                                        mainHandler.post {

                                            busy = false

                                            status =
                                                "Export finished, but saving failed: " +
                                                (e.localizedMessage
                                                    ?: "Unknown error")

                                            outputFile.delete()
                                            temporaryInput?.delete()
                                        }
                                    }

                                }.start()
                            }
                        }

                        override fun onError(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {

                            mainHandler.post {

                                busy = false

                                status =
                                    "Processing failed: " +
                                    (exportException.localizedMessage
                                        ?: "Unsupported media or codec.")

                                outputFile.delete()
                                temporaryInput?.delete()
                            }
                        }
                    }
                )
                .build()

            transformer.start(
                editedItem,
                outputFile.absolutePath
            )

        } catch (e: Exception) {

            busy = false
            temporaryInput?.delete()

            status = e.localizedMessage
                ?: "Could not start processing."
        }
    }

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

                Text(
                    "ANDROID LOCAL PROCESSING STUDIO",
                    color = MutedText,
                    fontSize = 10.sp
                )

                Spacer(Modifier.height(28.dp))

                SectionTitle("1. SELECT VIDEO SOURCE")

                OutlinedButton(
                    onClick = {
                        filePicker.launch(arrayOf("video/*"))
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),

                    border = BorderStroke(1.dp, AccentCyan)
                ) {
                    Text(
                        "CHOOSE VIDEO FROM PHONE",
                        color = AccentCyan
                    )
                }

                if (sourceName.isNotBlank()) {

                    Text(
                        sourceName,
                        color = SuccessGreen,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "OR USE AN AUTHORIZED DIRECT VIDEO URL",
                    color = MutedText,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = sourceUrl,

                    onValueChange = {
                        sourceUrl = it
                        inspection = null
                        sourceUri = null
                        sourceName = ""
                        outputLocation = ""
                    },

                    modifier = Modifier.fillMaxWidth(),

                    placeholder = {
                        Text(
                            "Paste HTTPS video URL",
                            color = MutedText
                        )
                    },

                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {

                        val input = sourceUrl.trim()

                        if (busy) return@Button

                        if (isYouTubeUrl(input)) {

                            inspection = InspectionResult(
                                false,
                                true,
                                "YouTube media downloading is not supported."
                            )

                            status =
                                "YouTube media extraction is not supported."

                            return@Button
                        }

                        if (!isSafeHttpsUrl(input)) {
                            status = "Enter a valid public HTTPS URL."
                            return@Button
                        }

                        busy = true
                        status = "Checking direct video URL..."

                        Thread {

                            val result =
                                inspectDirectVideoUrl(input)

                            mainHandler.post {

                                busy = false
                                inspection = result
                                status = result.message
                            }

                        }.start()
                    },

                    enabled = !busy && sourceUrl.isNotBlank(),

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text("CHECK VIDEO URL")
                }

                inspection?.let { result ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),

                        colors = CardDefaults.cardColors(
                            containerColor = CardBackground
                        )
                    ) {

                        Column(Modifier.padding(14.dp)) {

                            Text(
                                if (result.success) {
                                    "URL CHECK SUCCESS"
                                } else {
                                    "URL CHECK FAILED"
                                },

                                color = if (result.success) {
                                    SuccessGreen
                                } else {
                                    Color(0xFFFF7777)
                                },

                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                result.message,
                                color = MutedText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                SectionTitle("2. SELECT PROCESSING MODE")

                ModeCard(
                    "FULL VIDEO",
                    "Export the complete selected video.",
                    selectedMode == "FULL VIDEO"
                ) {
                    selectedMode = "FULL VIDEO"
                }

                ModeCard(
                    "CLIPS",
                    "Cut a selected time range from the video.",
                    selectedMode == "CLIPS"
                ) {
                    selectedMode = "CLIPS"
                }

                ModeCard(
                    "FULL AUDIO",
                    "Extract audio and save as M4A.",
                    selectedMode == "FULL AUDIO"
                ) {
                    selectedMode = "FULL AUDIO"
                }

                if (selectedMode == "CLIPS") {

                    SectionTitle("3. CLIP TIME (SECONDS)")

                    OutlinedTextField(
                        value = startSeconds,

                        onValueChange = {
                            startSeconds = it.filter(Char::isDigit)
                        },

                        label = {
                            Text("Start seconds")
                        },

                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = endSeconds,

                        onValueChange = {
                            endSeconds = it.filter(Char::isDigit)
                        },

                        label = {
                            Text("End seconds")
                        },

                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {

                        if (busy) return@Button

                        val currentMode = selectedMode
                        val currentUri = sourceUri
                        val currentUrl = sourceUrl.trim()

                        if (isYouTubeUrl(currentUrl)) {
                            status =
                                "YouTube media extraction is not supported."
                            return@Button
                        }

                        if (
                            currentUri == null &&
                            currentUrl.isBlank()
                        ) {
                            status = "Choose a video or enter a URL."
                            return@Button
                        }

                        if (
                            currentUri == null &&
                            !isSafeHttpsUrl(currentUrl)
                        ) {
                            status = "Invalid HTTPS video URL."
                            return@Button
                        }

                        busy = true
                        outputLocation = ""
                        status = "Preparing video..."

                        if (currentUri != null) {

                            startExport(
                                currentUri,
                                currentMode
                            )

                        } else {

                            Thread {

                                var downloaded: File? = null

                                try {

                                    downloaded = downloadVideo(
                                        context,
                                        currentUrl
                                    )

                                    mainHandler.post {

                                        startExport(
                                            Uri.fromFile(downloaded),
                                            currentMode,
                                            downloaded
                                        )
                                    }

                                } catch (e: Exception) {

                                    downloaded?.delete()

                                    mainHandler.post {

                                        busy = false

                                        status =
                                            e.localizedMessage
                                                ?: "Video download failed."
                                    }
                                }

                            }.start()
                        }
                    },

                    enabled = !busy,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = Color.Black
                    ),

                    shape = RoundedCornerShape(14.dp)
                ) {

                    if (busy) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )

                        Spacer(Modifier.width(10.dp))

                        Text("PROCESSING...")

                    } else {

                        Text(
                            "START PROCESSING",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),

                    colors = CardDefaults.cardColors(
                        containerColor = CardBackground
                    )
                ) {

                    Column(Modifier.padding(16.dp)) {

                        Text(
                            "PROCESSING STATUS",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            status,
                            color = TextWhite,
                            fontSize = 13.sp
                        )

                        if (busy) {

                            Spacer(Modifier.height(12.dp))

                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = AccentCyan
                            )

                            Text(
                                "Processing is running on this device.",
                                color = MutedText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (outputLocation.isNotBlank()) {

                            Spacer(Modifier.height(12.dp))

                            Text(
                                "OUTPUT SAVED",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                outputLocation,
                                color = MutedText,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "VIDEO OUTPUT: MP4",
                    color = MutedText,
                    fontSize = 11.sp
                )

                Text(
                    "AUDIO OUTPUT: M4A (AAC)",
                    color = MutedText,
                    fontSize = 11.sp
                )

                Text(
                    "Use only media you own or have permission to process.",
                    color = MutedText,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Spacer(Modifier.height(24.dp))
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
            .padding(bottom = 10.dp),

        shape = RoundedCornerShape(14.dp),

        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                Color(0xFF183449)
            } else {
                CardBackground
            }
        ),

        border = if (selected) {
            BorderStroke(1.dp, AccentCyan)
        } else {
            null
        }
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(Modifier.weight(1f)) {

                Text(
                    title,
                    color = if (selected) AccentCyan else TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    description,
                    color = MutedText,
                    fontSize = 12.sp
                )
            }

            Text(
                if (selected) "✓" else "›",
                color = AccentCyan,
                fontSize = 24.sp
            )
        }
    }
}
