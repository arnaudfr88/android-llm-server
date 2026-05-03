#!/usr/bin/env python3
"""
Python client example for Local LLM Server

This script demonstrates how to use the OpenAI Python library to interact
with the local LLM server running on your Android device.

Prerequisites:
    pip install openai

Usage:
    python examples.py
"""

from openai import OpenAI
import sys

# Replace with your Android device's IP address
# Find it in the app dashboard under "Server URLs"
SERVER_IP = "192.168.211.100"
BASE_URL = f"http://{SERVER_IP}:8080/v1"

def create_client():
    """Create an OpenAI client configured for the local server."""
    return OpenAI(
        base_url=BASE_URL,
        api_key="not-needed"  # Required by library, but not validated by server
    )

def example_simple_chat():
    """Simple non-streaming chat completion."""
    print("=== Simple Chat Example ===\n")
    
    client = create_client()
    
    response = client.chat.completions.create(
        model="gemma-4",  # Model name is informational, server uses loaded model
        messages=[
            {"role": "user", "content": "What is the capital of France?"}
        ],
        max_tokens=100
    )
    
    print(f"Response: {response.choices[0].message.content}")
    print(f"Tokens used: {response.usage.total_tokens}")
    print()

def example_conversation():
    """Multi-turn conversation."""
    print("=== Conversation Example ===\n")
    
    client = create_client()
    
    messages = [
        {"role": "system", "content": "You are a helpful assistant that speaks like a pirate."},
        {"role": "user", "content": "Hello, who are you?"}
    ]
    
    response = client.chat.completions.create(
        model="gemma-4",
        messages=messages
    )
    
    assistant_message = response.choices[0].message.content
    print(f"Assistant: {assistant_message}\n")
    
    # Continue conversation
    messages.append({"role": "assistant", "content": assistant_message})
    messages.append({"role": "user", "content": "Tell me a joke"})
    
    response = client.chat.completions.create(
        model="gemma-4",
        messages=messages
    )
    
    print(f"Assistant: {response.choices[0].message.content}\n")

def example_streaming():
    """Streaming response for real-time output."""
    print("=== Streaming Example ===\n")
    
    client = create_client()
    
    stream = client.chat.completions.create(
        model="gemma-4",
        messages=[
            {"role": "user", "content": "Count from 1 to 10"}
        ],
        stream=True
    )
    
    print("Assistant: ", end='', flush=True)
    for chunk in stream:
        if chunk.choices[0].delta.content:
            print(chunk.choices[0].delta.content, end='', flush=True)
    print("\n")

def example_error_handling():
    """Proper error handling."""
    print("=== Error Handling Example ===\n")
    
    client = create_client()
    
    try:
        # Try with an overly long prompt
        long_content = "N" * 50000  # Exceeds character limit
        
        response = client.chat.completions.create(
            model="gemma-4",
            messages=[
                {"role": "user", "content": long_content}
            ]
        )

        print(f"Assistant: {response.choices[0].message.content}\n")
        
    except Exception as e:
        print(f"Error caught: {type(e).__name__}")
        print(f"Message: {str(e)}\n")

def interactive_chat():
    """Interactive chat session."""
    print("=== Interactive Chat ===")
    print("Type 'exit' to quit\n")
    
    client = create_client()
    messages = [
        {"role": "system", "content": "You are a helpful assistant."}
    ]
    
    while True:
        user_input = input("You: ").strip()
        
        if user_input.lower() in ['exit', 'quit']:
            print("Goodbye!")
            break
            
        if not user_input:
            continue
        
        messages.append({"role": "user", "content": user_input})
        
        try:
            response = client.chat.completions.create(
                model="gemma-4",
                messages=messages,
                max_tokens=200
            )
            
            assistant_message = response.choices[0].message.content
            messages.append({"role": "assistant", "content": assistant_message})
            
            print(f"Assistant: {assistant_message}\n")
            
        except Exception as e:
            print(f"Error: {e}\n")

def main():
    """Run all examples or interactive mode."""
    if len(sys.argv) > 1 and sys.argv[1] == '--interactive':
        interactive_chat()
    else:
        print(f"Connecting to server at {BASE_URL}\n")
        print("Make sure the app is running and server is started!\n")
        print("-" * 60 + "\n")
        
        try:
            example_simple_chat()
            example_conversation()
            example_streaming()
            example_error_handling()
            
            print("-" * 60)
            print("\nAll examples completed!")
            print("\nRun with --interactive flag for interactive chat mode:")
            print(f"  python {sys.argv[0]} --interactive\n")
            
        except Exception as e:
            print(f"\n❌ Error connecting to server: {e}")
            print(f"\nMake sure:")
            print(f"  1. The Android app is running")
            print(f"  2. Server is started (toggle ON in app)")
            print(f"  3. SERVER_IP is correct (currently: {SERVER_IP})")
            print(f"  4. You're on the same WiFi network\n")
            sys.exit(1)

if __name__ == "__main__":
    main()
