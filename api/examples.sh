#!/usr/bin/env bash

# curl examples for Local LLM Server
# 
# Replace SERVER_IP with your Android device's IP address
# (found in the app dashboard under "Server URLs")

SERVER_IP="192.168.211.100"
BASE_URL="http://${SERVER_IP}:8080"

echo "==========================================="
echo "Local LLM Server - curl Examples"
echo "==========================================="
echo ""
echo "Server URL: ${BASE_URL}"
echo ""

echo "Health Check"
echo "   Command: curl ${BASE_URL}/health"
echo ""
curl -s "${BASE_URL}/health" | jq .
echo ""
echo ""

echo "List Available Models"
echo "   Command: curl ${BASE_URL}/v1/models"
echo ""
curl -s "${BASE_URL}/v1/models" | jq .
echo ""
echo ""

echo "Simple Chat Completion (non-streaming)"
echo "   Asking: What is 2+2?"
echo ""
curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "What is 2+2?"}
    ],
    "max_tokens": 50
  }' | jq .
echo ""
echo ""

echo "Chat with System Message"
echo "   System: You are a pirate"
echo "   User: Hello, who are you?"
echo ""
curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant that speaks like a pirate."},
      {"role": "user", "content": "Hello, who are you?"}
    ],
    "max_tokens": 100
  }' | jq '.choices[0].message.content'
echo ""
echo ""

echo "Streaming Response"
echo "   Watch tokens appear in real-time"
echo "   (Press Ctrl+C if it hangs)"
echo ""
curl -N "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "Count from 1 to 5"}
    ],
    "stream": true
  }'
echo ""
echo ""

echo "Give me a creative name for a cat"
echo ""
curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "Give me a creative name for a cat"}
    ],
    "max_tokens": 20
  }' | jq '.choices[0].message.content'
echo ""
echo ""

echo "[Again] Give me a creative name for a cat"
echo ""
curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "Give me a creative name for a cat"}
    ],
    "max_tokens": 20
  }' | jq '.choices[0].message.content'
echo ""
echo ""

echo "[Temperature Parameter] Give me a creative name for a cat"
echo ""
curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "Give me a creative name for a cat"}
    ],
    "temperature": 0.2,
    "max_tokens": 20
  }'
echo ""
echo ""

echo "Multi-turn Conversation"
echo "   Simulating a conversation history"
echo ""
curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "My name is Alice"},
      {"role": "assistant", "content": "Nice to meet you, Alice!"},
      {"role": "user", "content": "What is my name?"}
    ],
    "max_tokens": 50
  }' | jq '.choices[0].message.content'
echo ""
echo ""

echo "[Again] What is my name?"
echo "   Test fresh context."
echo ""
curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "What is my name?"}
    ],
    "max_tokens": 50
  }' | jq '.choices[0].message.content'
echo ""
echo ""


echo "Extract Just the Response Text"
echo ""
RESPONSE=$(curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "Say hello in French"}
    ],
    "max_tokens": 20
  }' | jq -r '.choices[0].message.content')

echo "Response: ${RESPONSE}"
echo ""
echo ""

echo "Measuring Response Time"
echo ""
time curl -s "${BASE_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4",
    "messages": [
      {"role": "user", "content": "What is AI?"}
    ],
    "max_tokens": 100
  }' > /dev/null
echo ""
echo ""

echo "Verbose Output (HTTP headers)"
echo "    First few lines only..."
echo ""
curl -v "${BASE_URL}/health" 2>&1 | head -20
echo ""
echo ""

echo "==========================================="
echo "All examples completed!"
echo "==========================================="
