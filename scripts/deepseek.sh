#!/usr/bin/env bash
# Shared DeepSeek API helper. Source this file, then call: deepseek_chat "$system" "$user"
# Requires: DEEPSEEK_API_KEY env var, curl, jq

deepseek_chat() {
    local system="$1" user="$2"
    curl -s --max-time 60 \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
        "https://api.deepseek.com/chat/completions" \
        -d "$(jq -n \
            --arg s "$system" \
            --arg u "$user" \
            '{model:"deepseek-chat",max_tokens:800,temperature:0.1,
              messages:[{role:"system",content:$s},{role:"user",content:$u}]}')" \
    | jq -r '.choices[0].message.content // "Analysis unavailable."'
}
