# API Documentation

**Local LLM Server - REST API**

This document describes the HTTP API exposed by the Local LLM Server. The API is OpenAI-compatible, meaning you can use existing OpenAI client libraries with minimal configuration changes.

## Table of Contents

1. [Base URL](#base-url)
2. [Authentication](#authentication)
3. [Endpoints](#endpoints)
4. [Request/Response Models](#requestresponse-models)
5. [Error Handling](#error-handling)
6. [Rate Limiting](#rate-limiting)
7. [Streaming Protocol](#streaming-protocol)
8. [Client Examples](#client-examples)

## Base URL

The server runs on port **8080** and binds only to local IP addresses.

**Find your server URL:**
1. Open the app dashboard
2. Look for "Server URLs" section
3. Use any listed local IP address

**Example URLs:**
- `http://192.168.211.100:8080`
- `http://10.0.0.100:8080`
- `http://127.0.0.1:8080` (localhost, same device only)

**Important:** Replace `192.168.211.100` with your actual device IP in all examples below.

## Authentication

**No authentication required.**

The server is protected by network isolation (local-only binding). All clients on the same local network can access the API.

For OpenAI client libraries that require an API key, use any non-empty string:

```python
client = OpenAI(
    base_url="http://192.168.211.100:8080/v1",
    api_key="not-needed"  # Required by library, but not validated
)
```

## Endpoints

### POST /v1/chat/completions

Generate a chat completion using the loaded LLM.

**OpenAI Compatibility:** This endpoint matches the OpenAI Chat Completions API format.

#### Request

**Headers:**
- `Content-Type: application/json`

**Body:**

```json
{
  "model": "gemma-4",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant."
    },
    {
      "role": "user",
      "content": "What is the capital of France?"
    }
  ],
  "max_tokens": 100,
  "temperature": 0.7,
  "top_p": 0.9,
  "stream": false
}
```

**Parameters:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `model` | string | Yes | Model identifier (currently ignored, uses loaded model) |
| `messages` | array | Yes | Conversation history |
| `messages[].role` | string | Yes | Role: "system", "user", or "assistant" |
| `messages[].content` | string | Yes | Message content |
| `max_tokens` | integer | No | ⚠️ **IGNORED** - Model generates complete responses |
| `temperature` | float | No | ⚠️ **IGNORED** - Server uses global config (1.0) |
| `top_p` | float | No | ⚠️ **IGNORED** - Server uses global config (0.95) |
| `stream` | boolean | No | Enable streaming (default: false) ✅ |

**Parameter Support:**
- ✅ **Fully supported:** `stream`
- ⚠️ **Ignored (global config/natural completion):** `max_tokens`, `temperature`, `top_p`
- ❌ **Not supported:** `presence_penalty`, `frequency_penalty`, `stop`, `n`, `logprobs` (LiteRT limitations)

**Global Sampling Configuration:**
All requests use standardized sampling parameters for optimal performance:
- `temperature`: 1.0 (fixed)
- `top_p`: 0.95 (fixed)
- `top_k`: 64 (fixed)

**Response Generation:**
The model always generates complete responses naturally, ignoring `max_tokens`. This ensures coherent output and prevents incomplete sentences.

#### Response (Non-Streaming)

**Status:** `200 OK`

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1714090800,
  "model": "gemma-4",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "The capital of France is Paris."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 15,
    "completion_tokens": 8,
    "total_tokens": 23
  }
}
```

#### Response (Streaming)

**Status:** `200 OK`  
**Content-Type:** `text/event-stream`

Server-Sent Events (SSE) format:

```
data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1714090800,"model":"gemma-4","choices":[{"index":0,"delta":{"role":"assistant","content":"The"},"finish_reason":null}]}

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1714090800,"model":"gemma-4","choices":[{"index":0,"delta":{"content":" capital"},"finish_reason":null}]}

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1714090800,"model":"gemma-4","choices":[{"index":0,"delta":{"content":" of"},"finish_reason":null}]}

...

data: {"id":"chatcmpl-abc123","object":"chat.completion.chunk","created":1714090800,"model":"gemma-4","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

### GET /v1/models

List available models (OpenAI-compatible endpoint).

Returns information about the currently loaded model.

#### Request

**No parameters required.**

```bash
curl http://192.168.211.100:8080/v1/models
```

#### Response

**Status:** `200 OK`

```json
{
  "object": "list",
  "data": [
    {
      "id": "gemma-4-e2b-it",
      "object": "model",
      "created": 1714090800,
      "owned_by": "local"
    }
  ]
}
```

**Note:** If no model is loaded, returns an empty array: `{"object": "list", "data": []}`

### GET /v1/models/{model}

Get details about a specific model.

#### Request

```bash
curl http://192.168.211.100:8080/v1/models/gemma-4-e2b-it
```

#### Response

**Status:** `200 OK`

```json
{
  "id": "gemma-4-e2b-it",
  "object": "model",
  "created": 1714090800,
  "owned_by": "local"
}
```

**Error Response (Model Not Found):**

**Status:** `404 Not Found`

```json
{
  "error": {
    "message": "Model not found",
    "type": "invalid_request_error"
  }
}
```

### GET /health

Health check endpoint to verify server is running.

#### Request

**No parameters required.**

```bash
curl http://192.168.211.100:8080/health
```

#### Response

**Status:** `200 OK`

```json
{
  "status": "healthy",
  "model_loaded": true,
  "version": "1.0.0"
}
```

## Request/Response Models

### Message

```typescript
{
  role: "system" | "user" | "assistant",
  content: string
}
```

### ChatCompletionRequest

```typescript
{
  model: string,
  messages: Message[],
  max_tokens?: number,      // ⚠️ IGNORED
  temperature?: number,     // ⚠️ IGNORED - Server uses 1.0
  top_p?: number,           // ⚠️ IGNORED - Server uses 0.95
  stream?: boolean          // default false ✅
}
```

**Note:** `temperature` and `top_p` are accepted for OpenAI API compatibility but ignored. The server uses global sampling configuration: temp=1.0, top_p=0.95, top_k=64.

### ChatCompletionResponse

```typescript
{
  id: string,
  object: "chat.completion",
  created: number,      // Unix timestamp
  model: string,
  choices: [
    {
      index: number,
      message: {
        role: "assistant",
        content: string
      },
      finish_reason: "stop" | "length" | "error"
    }
  ],
  usage: {
    prompt_tokens: number,
    completion_tokens: number,
    total_tokens: number
  }
}
```

### ChatCompletionChunk (Streaming)

```typescript
{
  id: string,
  object: "chat.completion.chunk",
  created: number,
  model: string,
  choices: [
    {
      index: number,
      delta: {
        role?: "assistant",  // Only in first chunk
        content?: string     // Token(s) generated
      },
      finish_reason: null | "stop" | "length" | "error"
    }
  ]
}
```

## Error Handling

### Error Response Format

```json
{
  "error": {
    "message": "Detailed error description",
    "type": "invalid_request_error",
    "code": "validation_failed"
  }
}
```

### HTTP Status Codes

| Code | Meaning | Common Causes |
|------|---------|---------------|
| `400` | Bad Request | Invalid JSON, missing required fields |
| `500` | Internal Server Error | Model inference failed, out of memory |
| `503` | Service Unavailable | Model not loaded, server starting |

## Rate Limiting

**No rate limiting implemented.**

The server is designed for trusted local network use where all clients are assumed to be cooperative. Resource constraints are naturally enforced by:

- **Device hardware**: CPU/GPU/NPU performance limits
- **Memory availability**: Model size and inference memory requirements  
- **Model capabilities**: Token generation speed

If you need rate limiting for your use case, implement it at the reverse proxy or client level.

## Streaming Protocol

Streaming uses **Server-Sent Events (SSE)**, a standard HTTP streaming protocol.

### How It Works

1. Client sends request with `"stream": true`
2. Server responds with `Content-Type: text/event-stream`
3. Server sends chunks as `data: {...}` lines
4. Each chunk contains partial response
5. Final chunk has `finish_reason: "stop"`
6. Stream ends with `data: [DONE]`

**Python (OpenAI SDK):**

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://192.168.211.100:8080/v1",
    api_key="not-needed"
)

stream = client.chat.completions.create(
    model="gemma-4",
    messages=[{"role": "user", "content": "Hello"}],
    stream=True
)

for chunk in stream:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end='')
```

## Client Examples

- **Python** (`examples.py`) - Using OpenAI SDK
- **curl** (`examples.sh`) - Command-line examples

### Quick Examples

#### curl - Non-Streaming

```bash
curl http://192.168.211.100:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "What is 2+2?"}
    ],
    "max_tokens": 50
  }'
```

#### curl - Streaming

```bash
curl -N http://192.168.211.100:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "Count to 5"}
    ],
    "stream": true
  }'
```

#### Python - OpenAI SDK

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://192.168.211.100:8080/v1",
    api_key="not-needed"
)

response = client.chat.completions.create(
    model="gemma-4",
    messages=[
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content": "Explain quantum computing in simple terms."}
    ],
    max_tokens=200
)

print(response.choices[0].message.content)
```

#### LangChain Integration

```python
from langchain.chat_models import ChatOpenAI
from langchain.schema import HumanMessage

llm = ChatOpenAI(
    openai_api_base="http://192.168.211.100:8080/v1",
    openai_api_key="not-needed",
    model_name="gemma-4"
)

response = llm([HumanMessage(content="Hello, how are you?")])
print(response.content)
```