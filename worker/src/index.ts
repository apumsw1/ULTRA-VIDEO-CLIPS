
export interface Env {
  MEDIA_PROCESSOR_URL?: string;
  MEDIA_PROCESSOR_TOKEN?: string;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Access-Control-Max-Age": "86400",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function validateUrl(value: string): URL | null {
  try {
    const url = new URL(value);

    if (url.protocol !== "https:") return null;
    if (url.username || url.password) return null;

    const host = url.hostname.toLowerCase();

    if (
      !host ||
      host.length > 253 ||
      host === "localhost" ||
      host.endsWith(".localhost") ||
      host.endsWith(".local") ||
      host.endsWith(".internal") ||
      host.endsWith(".test") ||
      host.startsWith("[") ||
      /^\d{1,3}(\.\d{1,3}){3}$/.test(host)
    ) {
      return null;
    }

    if (
      host === "youtube.com" ||
      host.endsWith(".youtube.com") ||
      host === "youtu.be"
    ) {
      return null;
    }

    return url;
  } catch {
    return null;
  }
}

async function inspectVideo(request: Request): Promise<Response> {
  let body: { url?: unknown };

  try {
    body = await request.json() as { url?: unknown };
  } catch {
    return json({
      ok: false,
      error: "Invalid JSON request."
    }, 400);
  }

  if (
    typeof body.url !== "string" ||
    body.url.length > 4096
  ) {
    return json({
      ok: false,
      error: "A valid video URL is required."
    }, 400);
  }

  const mediaUrl = validateUrl(body.url.trim());

  if (!mediaUrl) {
    return json({
      ok: false,
      error: "Only public HTTPS direct-video URLs are supported."
    }, 400);
  }

  let response: Response;

  try {
    response = await fetch(mediaUrl.toString(), {
      method: "HEAD",
      redirect: "manual",
      headers: {
        "User-Agent": "ULTRA-VIDEO-CLIPS/1.0",
        "Accept": "video/*, application/octet-stream"
      },
      signal: AbortSignal.timeout(12000)
    });
  } catch {
    return json({
      ok: false,
      error: "Could not connect to the video source."
    }, 422);
  }

  if (response.status >= 300 && response.status < 400) {
    return json({
      ok: false,
      error: "Redirect detected. Use the final direct video URL."
    }, 422);
  }

  if (!response.ok) {
    return json({
      ok: false,
      error: `Source returned HTTP ${response.status}.`
    }, 422);
  }

  const contentType = (
    response.headers.get("content-type") || ""
  ).split(";")[0].trim().toLowerCase();

  if (
    !contentType.startsWith("video/") &&
    contentType !== "application/octet-stream"
  ) {
    return json({
      ok: false,
      error: "This URL is not identified as a direct video file.",
      detectedContentType: contentType || "unknown"
    }, 422);
  }

  const rawLength = response.headers.get("content-length");

  const contentLength = rawLength && /^\d+$/.test(rawLength)
    ? Number(rawLength)
    : null;

  return json({
    ok: true,
    source: {
      type: "direct-video-file",
      contentType,
      contentLengthBytes: contentLength,
      downloadable: true
    },
    qualityDetection: {
      status: "not-yet-connected",
      availableQualities: [],
      message: "FFmpeg processing service is required to detect video resolution."
    },
    nextStep: "Connect the media-processing service."
  });
}

export default {
  async fetch(
    request: Request,
    _env: Env
  ): Promise<Response> {

    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: corsHeaders
      });
    }

    if (
      request.method === "GET" &&
      url.pathname === "/health"
    ) {
      return json({
        ok: true,
        service: "ULTRA VIDEO CLIPS API",
        version: "1.0.0"
      });
    }

    if (
      request.method === "POST" &&
      url.pathname === "/api/video/inspect"
    ) {
      return inspectVideo(request);
    }

    return json({
      ok: false,
      error: "Route not found."
    }, 404);
  }
};
