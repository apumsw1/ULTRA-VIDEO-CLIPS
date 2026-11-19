interface Env {}

const VERSION = "1.2.0-free-plan";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Content-Type": "application/json; charset=utf-8",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: CORS_HEADERS,
  });
}

function getVideoId(input: string): string | null {
  try {
    const url = new URL(input);
    const host = url.hostname.toLowerCase();

    if (
      host === "youtu.be" ||
      host === "www.youtu.be"
    ) {
      return url.pathname.split("/").filter(Boolean)[0] || null;
    }

    if (
      host === "youtube.com" ||
      host === "www.youtube.com" ||
      host === "m.youtube.com" ||
      host === "music.youtube.com"
    ) {
      if (url.pathname === "/watch") {
        return url.searchParams.get("v");
      }

      const parts = url.pathname.split("/").filter(Boolean);

      if (
        ["shorts", "embed", "live"].includes(parts[0] || "")
      ) {
        return parts[1] || null;
      }
    }

    return null;
  } catch {
    return null;
  }
}

function isPrivateIPv4(host: string): boolean {
  const parts = host.split(".");

  if (
    parts.length !== 4 ||
    parts.some((part) => !/^\d+$/.test(part))
  ) {
    return false;
  }

  const nums = parts.map(Number);

  if (nums.some((n) => n < 0 || n > 255)) {
    return true;
  }

  const [a, b] = nums;

  return (
    a === 0 ||
    a === 10 ||
    a === 127 ||
    a >= 224 ||
    (a === 100 && b >= 64 && b <= 127) ||
    (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) ||
    (a === 192 && b === 0) ||
    (a === 198 && (b === 18 || b === 19))
  );
}

function isBlockedHost(host: string): boolean {
  const normalized = host.toLowerCase().replace(/^\[|\]$/g, "");

  if (
    normalized === "localhost" ||
    normalized.endsWith(".localhost") ||
    normalized.endsWith(".local") ||
    normalized === "::1" ||
    normalized === "::" ||
    normalized.startsWith("fc") ||
    normalized.startsWith("fd") ||
    normalized.startsWith("fe80:")
  ) {
    return true;
  }

  if (normalized.includes(":")) {
    return true;
  }

  if (/^\d+\.\d+\.\d+\.\d+$/.test(normalized)) {
    return isPrivateIPv4(normalized);
  }

  return false;
}

function validateDirectVideoUrl(
  input: unknown
): { valid: true; url: URL } | { valid: false; error: string } {
  if (typeof input !== "string" || input.length > 4096) {
    return {
      valid: false,
      error: "A valid video URL is required.",
    };
  }

  let url: URL;

  try {
    url = new URL(input.trim());
  } catch {
    return {
      valid: false,
      error: "Invalid URL format.",
    };
  }

  if (url.protocol !== "https:") {
    return {
      valid: false,
      error: "Only HTTPS video URLs are supported.",
    };
  }

  if (url.username || url.password) {
    return {
      valid: false,
      error: "URLs containing credentials are not allowed.",
    };
  }

  if (url.port && url.port !== "443") {
    return {
      valid: false,
      error: "Custom ports are not allowed.",
    };
  }

  if (isBlockedHost(url.hostname)) {
    return {
      valid: false,
      error: "Private or local network URLs are not allowed.",
    };
  }

  return { valid: true, url };
}

async function inspectYouTube(body: any): Promise<Response> {
  const input = body?.url;

  if (typeof input !== "string") {
    return json(
      {
        success: false,
        error: "YouTube URL is required.",
      },
      400
    );
  }

  const videoId = getVideoId(input);

  if (
    !videoId ||
    !/^[a-zA-Z0-9_-]{11}$/.test(videoId)
  ) {
    return json(
      {
        success: false,
        error: "Invalid YouTube video URL.",
      },
      400
    );
  }

  const canonicalUrl =
    `https://www.youtube.com/watch?v=${videoId}`;

  try {
    const endpoint =
      "https://www.youtube.com/oembed?format=json&url=" +
      encodeURIComponent(canonicalUrl);

    const response = await fetch(endpoint, {
      headers: {
        "User-Agent": "ULTRA-VIDEO-CLIPS/1.0",
      },
    });

    if (!response.ok) {
      return json(
        {
          success: false,
          error: "YouTube video information is unavailable.",
        },
        404
      );
    }

    const metadata: any = await response.json();

    return json({
      success: true,
      type: "youtube",
      videoId,
      title: metadata.title || "Unknown title",
      author: metadata.author_name || "Unknown creator",
      thumbnail:
        `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`,
      previewUrl:
        `https://www.youtube.com/embed/${videoId}`,
      originalUrl: canonicalUrl,
      downloadAvailable: false,
      processingAvailable: false,
      message:
        "YouTube preview is available. Media downloading and extraction are not provided.",
    });
  } catch {
    return json(
      {
        success: false,
        error: "Unable to connect to YouTube.",
      },
      502
    );
  }
}

function inspectDirectVideo(body: any): Response {
  const result = validateDirectVideoUrl(body?.url);

  if (!result.valid) {
    return json(
      {
        success: false,
        error: result.error,
      },
      400
    );
  }

  return json({
    success: true,
    type: "direct_video",
    originalUrl: result.url.toString(),
    host: result.url.hostname,
    urlValidated: true,
    contentType: "not_checked",
    downloadAvailable: false,
    processingAvailable: false,
    processingMode: "android-local",
    message:
      "URL validation completed. The Android app must download the authorized media and inspect it locally before processing.",
  });
}

function getCapabilities(): Response {
  return json({
    success: true,
    application: "ULTRA VIDEO CLIPS",
    version: VERSION,
    plan: "cloudflare-free",
    capabilities: {
      api: true,
      youtubeMetadata: true,
      youtubePreview: true,
      directUrlValidation: true,
      androidLocalProcessing: true,
      cloudProcessing: false,
      cloudFFmpeg: false,
      videoDownloadFromWorker: false,
      videoClippingOnWorker: false,
      audioExtractionOnWorker: false,
      mp3ConversionOnWorker: false,
    },
    processing: {
      mode: "android-local",
      requiredComponents: [
        "Android MediaExtractor",
        "Android MediaMuxer",
        "Android MediaCodec",
      ],
      message:
        "Video processing must be implemented inside the Android application. This Worker does not run FFmpeg or process media files.",
    },
  });
}

async function handleRequest(
  request: Request,
  _env: Env
): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;

  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: CORS_HEADERS,
    });
  }

  if (request.method === "GET" && path === "/") {
    return json({
      success: true,
      name: "ULTRA VIDEO CLIPS API",
      version: VERSION,
      status: "online",
    });
  }

  if (request.method === "GET" && path === "/health") {
    return json({
      success: true,
      status: "healthy",
      version: VERSION,
      timestamp: new Date().toISOString(),
    });
  }

  if (
    request.method === "GET" &&
    path === "/api/processing/capabilities"
  ) {
    return getCapabilities();
  }

  if (request.method !== "POST") {
    return json(
      {
        success: false,
        error: "Method not allowed.",
      },
      405
    );
  }

  let body: any;

  try {
    const contentLength = Number(
      request.headers.get("content-length") || "0"
    );

    if (contentLength > 16384) {
      return json(
        {
          success: false,
          error: "Request body is too large.",
        },
        413
      );
    }

    const rawBody = await request.text();

    if (rawBody.length > 16384) {
      return json(
        {
          success: false,
          error: "Request body is too large.",
        },
        413
      );
    }

    body = JSON.parse(rawBody);
  } catch {
    return json(
      {
        success: false,
        error: "Invalid JSON request body.",
      },
      400
    );
  }

  if (path === "/api/youtube/inspect") {
    return inspectYouTube(body);
  }

  if (path === "/api/video/inspect") {
    return inspectDirectVideo(body);
  }

  return json(
    {
      success: false,
      error: "API endpoint not found.",
      path,
    },
    404
  );
}

export default {
  async fetch(
    request: Request,
    env: Env
  ): Promise<Response> {
    try {
      return await handleRequest(request, env);
    } catch {
      return json(
        {
          success: false,
          error: "Internal server error.",
        },
        500
      );
    }
  },
};
