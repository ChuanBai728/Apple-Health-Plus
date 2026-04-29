import { NextRequest, NextResponse } from 'next/server';

const BACKEND = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8080';

const ALLOWED_FORWARD_HEADERS = new Set([
  'content-type',
  'accept',
  'accept-encoding',
  'accept-language',
  'user-agent',
  'x-request-id',
]);

async function proxy(req: NextRequest) {
  const url = `${BACKEND}${req.nextUrl.pathname}${req.nextUrl.search}`;

  const headers: Record<string, string> = {};
  req.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (ALLOWED_FORWARD_HEADERS.has(lower)) {
      headers[key] = value;
    }
  });

  let body: BodyInit | null = null;
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    body = await req.blob();
  }

  try {
    const backendRes = await fetch(url, { method: req.method, headers, body });

    const resHeaders = new Headers();
    backendRes.headers.forEach((value, key) => {
      if (!['transfer-encoding', 'connection'].includes(key.toLowerCase())) {
        resHeaders.set(key, value);
      }
    });

    return new NextResponse(await backendRes.blob(), {
      status: backendRes.status,
      headers: resHeaders,
    });
  } catch {
    return NextResponse.json({ error: 'Backend unreachable' }, { status: 502 });
  }
}

export async function GET(req: NextRequest) { return proxy(req); }
export async function POST(req: NextRequest) { return proxy(req); }
export async function PUT(req: NextRequest) { return proxy(req); }
export async function DELETE(req: NextRequest) { return proxy(req); }
export const dynamic = 'force-dynamic';
