import { NextRequest, NextResponse } from 'next/server';

const GATEWAY_URL = process.env.GATEWAY_URL || 'http://localhost:8080';

async function handleProxy(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  try {
    const resolvedParams = await params;
    const pathString = resolvedParams.path.join('/');
    
    // Construct the gateway URL including any query parameters
    const searchParams = request.nextUrl.search;
    const targetUrl = `${GATEWAY_URL}/${pathString}${searchParams}`;

    console.log(`[BFF Proxy] Forwarding: ${request.method} ${request.nextUrl.pathname}${searchParams} -> ${targetUrl}`);

    // Parse the body if it exists and is not a GET/HEAD request
    let body: any = undefined;
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method)) {
      try {
        body = await request.text();
      } catch (e) {
        // Body is empty or unparsable
      }
    }

    // Forward relevant headers
    const headers = new Headers();
    request.headers.forEach((value, key) => {
      // Avoid forwarding host, connection, content-length (let fetch calculate content-length)
      if (!['host', 'connection', 'content-length', 'content-type'].includes(key.toLowerCase())) {
        headers.set(key, value);
      }
    });

    if (body) {
      headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(targetUrl, {
      method: request.method,
      headers: headers,
      body: body,
      cache: 'no-store'
    });

    // Extract response body
    let responseData;
    const contentType = response.headers.get('content-type');
    
    if (contentType && contentType.includes('application/json')) {
      responseData = await response.json();
    } else {
      responseData = await response.text();
    }

    // Return the response with correct status and headers
    const responseHeaders = new Headers();
    response.headers.forEach((value, key) => {
      if (!['content-encoding', 'transfer-encoding'].includes(key.toLowerCase())) {
        responseHeaders.set(key, value);
      }
    });

    // Ensure JSON responses are correctly serialized
    if (typeof responseData === 'object') {
      return NextResponse.json(responseData, {
        status: response.status,
        headers: responseHeaders
      });
    }

    return new NextResponse(responseData, {
      status: response.status,
      headers: responseHeaders
    });

  } catch (error: any) {
    console.error(`[BFF Proxy Error]`, error);
    return NextResponse.json(
      { error: 'BFF Gateway Proxy Error', message: error.message },
      { status: 502 }
    );
  }
}

export async function GET(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, context);
}

export async function POST(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, context);
}

export async function PUT(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, context);
}

export async function PATCH(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, context);
}

export async function DELETE(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, context);
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 200,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': '*'
    }
  });
}
