export interface Env {
  DB: D1Database;
  MEDIA_QUEUE: Queue<DownloadJob>;
  MEDIA_BUCKET: R2Bucket;
  PROCESSOR_SHARED_SECRET: string;
  PROCESSOR_URL: string;
  PUBLIC_API_BASE: string;
  PUBLIC_MEDIA_BASE_URL?: string;
}

interface DownloadJob {
  jobId: string;
  sourceUrl: string;
  format: string;
  createdAt: number;
}

const HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "Content-Type, X-Processor-Secret"
};

const FORMATS = [
  "source", "360", "480", "720",
  "1080", "1440", "2160"
];

function response(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: HEADERS
  });
}

function validId(id: string) {
  return /^[a-f0-9-]{36}$/i.test(id);
}

function validateUrl(value: unknown): string {
  if (typeof value !== "string" || value.length > 4096) {
    throw new Error("Invalid video URL.");
  }

  let url: URL;

  try {
    url = new URL(value);
  } catch {
    throw new Error("Invalid URL.");
  }

  if (url.protocol !== "https:") {
    throw new Error("HTTPS URLs only.");
  }

  if (url.username || url.password) {
    throw new Error("URL credentials are not allowed.");
  }

  if (url.port && url.port !== "443") {
    throw new Error("Only HTTPS port 443 is allowed.");
  }

  const host = url.hostname.toLowerCase();

  if (
    host === "localhost" ||
    host.endsWith(".localhost") ||
    host.endsWith(".local") ||
    /^\d{1,3}(\.\d{1,3}){3}$/.test(host) ||
    host.includes(":")
  ) {
    throw new Error("Private or IP-based hosts are not allowed.");
  }

  const normalized = host.replace(/^www\./, "");

  if (
    ["youtube.com", "youtu.be", "youtube-nocookie.com"]
      .some(domain =>
        normalized === domain ||
        normalized.endsWith("." + domain)
      )
  ) {
    throw new Error(
      "YouTube extraction is not supported."
    );
  }

  url.hash = "";
  return url.toString();
}

async function createDownloadJob(
  request: Request,
  env: Env
) {
  let body: {
    url?: unknown;
    format?: unknown;
  };

  try {
    body = await request.json();
  } catch {
    return response({
      success: false,
      error: "Invalid JSON."
    }, 400);
  }

  let sourceUrl: string;

  try {
    sourceUrl = validateUrl(body.url);
  } catch (error) {
    return response({
      success: false,
      error: (error as Error).message
    }, 400);
  }

  const format =
    typeof body.format === "string"
      ? body.format
      : "source";

  if (!FORMATS.includes(format)) {
    return response({
      success: false,
      error: "Unsupported format."
    }, 400);
  }

  const jobId = crypto.randomUUID();
  const now = Date.now();

  try {
    await env.DB.prepare(`
      INSERT INTO jobs (
        id,
        status,
        source_url,
        format,
        created_at,
        updated_at
      )
      VALUES (?, 'QUEUED', ?, ?, ?, ?)
    `).bind(
      jobId,
      sourceUrl,
      format,
      now,
      now
    ).run();

    await env.MEDIA_QUEUE.send({
      jobId,
      sourceUrl,
      format,
      createdAt: now
    });

  } catch (error) {
    console.error(error);

    return response({
      success: false,
      error: "Job creation failed."
    }, 503);
  }

  return response({
    success: true,
    jobId,
    status: "QUEUED",
    statusUrl:
      `${env.PUBLIC_API_BASE}/api/v2/status/${jobId}`
  }, 202);
}

async function getStatus(
  jobId: string,
  request: Request,
  env: Env
) {
  if (!validId(jobId)) {
    return response({
      success: false,
      error: "Invalid job ID."
    }, 400);
  }

  const job = await env.DB.prepare(`
    SELECT
      id,
      status,
      format,
      title,
      file_size AS fileSize,
      download_url AS downloadUrl,
      error,
      created_at AS createdAt,
      updated_at AS updatedAt
    FROM jobs
    WHERE id = ?
  `).bind(jobId).first<Record<string, unknown>>();

  if (!job) {
    return response({
      success: false,
      error: "Job not found."
    }, 404);
  }

  if (job.status === "COMPLETED" && !job.downloadUrl) {
    job.downloadUrl =
      `${new URL(request.url).origin}/api/v2/file/${jobId}`;
  }

  return response({
    success: true,
    jobId,
    ...job
  });
}

async function processorCallback(
  request: Request,
  env: Env
) {
  const secret = request.headers.get(
    "X-Processor-Secret"
  );

  if (
    !env.PROCESSOR_SHARED_SECRET ||
    secret !== env.PROCESSOR_SHARED_SECRET
  ) {
    return response({
      success: false,
      error: "Unauthorized."
    }, 401);
  }

  let body: {
    jobId?: string;
    status?: string;
    title?: string;
    fileSize?: number;
    objectKey?: string;
    error?: string;
  };

  try {
    body = await request.json();
  } catch {
    return response({
      success: false,
      error: "Invalid JSON."
    }, 400);
  }

  const id = body.jobId || "";

  if (!validId(id)) {
    return response({
      success: false,
      error: "Invalid job ID."
    }, 400);
  }

  const now = Date.now();

  if (body.status === "PROCESSING") {
    await env.DB.prepare(`
      UPDATE jobs
      SET status = 'PROCESSING', updated_at = ?
      WHERE id = ?
    `).bind(now, id).run();

    return response({ success: true });
  }

  if (body.status === "FAILED") {
    await env.DB.prepare(`
      UPDATE jobs
      SET status = 'FAILED',
          error = ?,
          updated_at = ?
      WHERE id = ?
    `).bind(
      (body.error || "Processing failed.").slice(0, 500),
      now,
      id
    ).run();

    return response({ success: true });
  }

  if (body.status === "COMPLETED") {
    const objectKey = body.objectKey || "";

    if (!objectKey.startsWith(`jobs/${id}/`)) {
      return response({
        success: false,
        error: "Invalid object key."
      }, 400);
    }

    const object = await env.MEDIA_BUCKET.head(objectKey);

    if (!object) {
      return response({
        success: false,
        error: "R2 object not found."
      }, 409);
    }

    const publicUrl = env.PUBLIC_MEDIA_BASE_URL
      ? `${env.PUBLIC_MEDIA_BASE_URL.replace(/\/+$/, "")}/${objectKey
          .split("/")
          .map(encodeURIComponent)
          .join("/")}`
      : null;

    await env.DB.prepare(`
      UPDATE jobs
      SET status = 'COMPLETED',
          title = ?,
          file_size = ?,
          object_key = ?,
          download_url = ?,
          error = NULL,
          updated_at = ?
      WHERE id = ?
    `).bind(
      (body.title || "Processed video").slice(0, 250),
      body.fileSize || object.size,
      objectKey,
      publicUrl,
      now,
      id
    ).run();

    return response({ success: true });
  }

  return response({
    success: false,
    error: "Unknown processor status."
  }, 400);
}

async function downloadFile(
  jobId: string,
  env: Env
) {
  const job = await env.DB.prepare(`
    SELECT status, object_key
    FROM jobs
    WHERE id = ?
  `).bind(jobId).first<{
    status: string;
    object_key: string | null;
  }>();

  if (
    !job ||
    job.status !== "COMPLETED" ||
    !job.object_key
  ) {
    return response({
      success: false,
      error: "File is not ready."
    }, 404);
  }

  const object = await env.MEDIA_BUCKET.get(
    job.object_key
  );

  if (!object) {
    return response({
      success: false,
      error: "File not found."
    }, 404);
  }

  const headers = new Headers(HEADERS);

  object.writeHttpMetadata(headers);
  headers.set(
    "Content-Type",
    object.httpMetadata?.contentType || "video/mp4"
  );
  headers.set("Content-Length", String(object.size));
  headers.set(
    "Content-Disposition",
    `attachment; filename="ultra-${jobId}.mp4"`
  );

  return new Response(object.body, {
    status: 200,
    headers
  });
}

async function handle(
  request: Request,
  env: Env
): Promise<Response> {
  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: HEADERS
    });
  }

  const url = new URL(request.url);
  const path = url.pathname;

  if (request.method === "GET" && path === "/") {
    return response({
      name: "ULTRA VIDEO CLIPS API",
      version: "2.0.0",
      status: "online"
    });
  }

  if (request.method === "GET" && path === "/health") {
    return response({
      success: true,
      database: true,
      storage: true,
      queue: true
    });
  }

  if (
    request.method === "POST" &&
    path === "/api/v2/download"
  ) {
    return createDownloadJob(request, env);
  }

  if (
    request.method === "POST" &&
    path === "/api/v2/processor/callback"
  ) {
    return processorCallback(request, env);
  }

  const statusMatch = path.match(
    /^\/api\/v2\/status\/([a-f0-9-]+)$/i
  );

  if (request.method === "GET" && statusMatch) {
    return getStatus(statusMatch[1], request, env);
  }

  const fileMatch = path.match(
    /^\/api\/v2\/file\/([a-f0-9-]+)$/i
  );

  if (request.method === "GET" && fileMatch) {
    if (!validId(fileMatch[1])) {
      return response({ error: "Invalid ID." }, 400);
    }

    return downloadFile(fileMatch[1], env);
  }

  return response({
    success: false,
    error: "Route not found."
  }, 404);
}

export default {
  async fetch(request: Request, env: Env) {
    try {
      return await handle(request, env);
    } catch (error) {
      console.error(error);

      return response({
        success: false,
        error: "Internal server error."
      }, 500);
    }
  },

  async queue(
    batch: MessageBatch<DownloadJob>,
    env: Env
  ) {
    for (const message of batch.messages) {
      const job = message.body;

      try {
        await env.DB.prepare(`
          UPDATE jobs
          SET status = 'PROCESSING', updated_at = ?
          WHERE id = ? AND status = 'QUEUED'
        `).bind(Date.now(), job.jobId).run();

        const result = await fetch(env.PROCESSOR_URL, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Processor-Secret":
              env.PROCESSOR_SHARED_SECRET
          },
          body: JSON.stringify({
            jobId: job.jobId,
            sourceUrl: job.sourceUrl,
            format: job.format,
            callbackUrl:
              `${env.PUBLIC_API_BASE}/api/v2/processor/callback`
          })
        });

        if (!result.ok) {
          throw new Error(
            `Processor HTTP ${result.status}`
          );
        }

        message.ack();

      } catch (error) {
        console.error("Queue error:", error);

        await env.DB.prepare(`
          UPDATE jobs
          SET status = 'FAILED',
              error = ?,
              updated_at = ?
          WHERE id = ?
        `).bind(
          String(error).slice(0, 500),
          Date.now(),
          job.jobId
        ).run();

        message.ack();
      }
    }
  }
};
