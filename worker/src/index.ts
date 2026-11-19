interface Env {}

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...CORS_HEADERS,
      "Content-Type": "application/json",
    },
  });
}

function extractYouTubeId(input: string): string | null {
  try {
    const url = new URL(input);
    const host = url.hostname.toLowerCase().replace(/^www\./, "");

    if (host === "youtu.be") {
      return url.pathname.split("/").filter(Boolean)[0] || null;
    }

    const allowedHosts = [
      "youtube.com",
      "m.youtube.com",
      "music.youtube.com",
    ];

    if (!allowedHosts.includes(host)) return null;

    const videoId = url.searchParams.get("v");
    if (videoId) return videoId;

    const parts = url.pathname.split("/").filter(Boolean);

    if (
      ["shorts", "embed", "live"].includes(parts[0]) &&
      parts[1]
    ) {
      return parts[1];
    }

    return null;
  } catch {
    return null;
  }
}

function validVideoId(id: string): boolean {
  return /^[a-zA-Z0-9_-]{11}$/.test(id);
}

async function inspectYouTube(request: Request): Promise<Response> {
  let body: { url?: string };

  try {
    body = await request.json();
  } catch {
    return json({ success: false, message: "Invalid JSON body." }, 400);
  }

  const input = body.url?.trim();

  if (!input) {
    return json({ success: false, message: "Video URL is required." }, 400);
  }

  const videoId = extractYouTubeId(input);

  if (!videoId || !validVideoId(videoId)) {
    return json({
      success: false,
      message: "Invalid or unsupported YouTube URL.",
    }, 400);
  }

  const canonicalUrl = `https://www.youtube.com/watch?v=${videoId}`;

  const oembedUrl = new URL("https://www.youtube.com/oembed");
  oembedUrl.searchParams.set("url", canonicalUrl);
  oembedUrl.searchParams.set("format", "json");

  try {
    const response = await fetch(oembedUrl.toString(), {
      headers: { Accept: "application/json" },
    });

    if (!response.ok) {
      return json({
        success: false,
        message: "YouTube video metadata is unavailable. The video may be private, restricted, or unavailable.",
      }, 404);
    }

    const metadata = await response.json() as {
      title?: string;
      author_name?: string;
      thumbnail_url?: string;
      thumbnail_width?: number;
      thumbnail_height?: number;
    };

    return json({
      success: true,
      platform: "youtube",
      videoId,
      url: canonicalUrl,
      title: metadata.title ?? "YouTube video",
      author: metadata.author_name ?? "Unknown creator",
      thumbnail: metadata.thumbnail_url ??
        `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
      previewUrl: `https://www.youtube-nocookie.com/embed/${videoId}`,
      previewAvailable: true,
      downloadAvailable: false,
      message: "YouTube preview information loaded successfully.",
    });
  } catch {
    return json({
      success: false,
      message: "Could not connect to YouTube. Please try again.",
    }, 502);
  }
}

async function inspectDirectVideo(request: Request): Promise<Response> {
  let body: { url?: string };

  try {
    body = await request.json();
  } catch {
    return json({ success: false, message: "Invalid JSON body." }, 400);
  }

  const input = body.url?.trim();

  if (!input) {
    return json({ success: false, message: "Video URL is required." }, 400);
  }

  let url: URL;

  try {
    url = new URL(input);
  } catch {
    return json({ success: false, message: "Invalid URL." }, 400);
  }

  if (url.protocol !== "https:") {
    return json({
      success: false,
      message: "Only HTTPS video URLs are supported.",
    }, 400);
  }

  const host = url.hostname.toLowerCase();

  if (
    host === "localhost" ||
    host.endsWith(".localhost") ||
    host.endsWith(".local") ||
    host === "127.0.0.1" ||
    host === "::1" ||
    host.startsWith("10.") ||
    host.startsWith("192.168.") ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(host)
  ) {
    return json({ success: false, message: "Private network URLs are not allowed." }, 400);
  }

  try {
    const response = await fetch(url.toString(), {
      method: "HEAD",
      redirect: "manual",
    });

    if (!response.ok) {
      return json({
        success: false,
        message: "Could not inspect this video URL.",
      }, 400);
    }

    const contentType = response.headers.get("content-type") || "";
    const contentLength = response.headers.get("content-length");

    if (
      !contentType.toLowerCase().startsWith("video/") &&
      !contentType.toLowerCase().includes("application/octet-stream")
    ) {
      return json({
        success: false,
        message: "The URL does not point to a recognized video file.",
      }, 415);
    }

    return json({
      success: true,
      platform: "direct",
      url: url.toString(),
      contentType,
      fileSize: contentLength ? Number(contentLength) : null,
      qualityStatus: "not-yet-connected",
      processingAvailable: false,
      message: "Video source inspected. Processing service is not connected yet.",
    });
  } catch {
    return json({
      success: false,
      message: "Could not connect to the video source.",
    }, 502);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        service: "ULTRA VIDEO CLIPS API",
        version: "1.1.0",
      });
    }

    if (request.method === "POST" && url.pathname === "/api/youtube/inspect") {
      return inspectYouTube(request);
    }

    if (request.method === "POST" && url.pathname === "/api/video/inspect") {
      return inspectDirectVideo(request);
    }

    return json({ success: false, message: "Route not found." }, 404);
  },
};
