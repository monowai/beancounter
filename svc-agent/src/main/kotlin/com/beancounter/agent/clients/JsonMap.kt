package com.beancounter.agent.clients

import org.springframework.core.ParameterizedTypeReference

/** A JSON object read as a plain map, for upstreams the agent passes through to tools. */
internal val JSON_MAP = object : ParameterizedTypeReference<Map<String, Any>>() {}