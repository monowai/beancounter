package com.beancounter.agent

import com.beancounter.auth.model.AuthConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller for Beancounter AI Agent
 *
 * Provides endpoints for AI agent interactions including natural language queries,
 * portfolio analysis, and market overviews.
 */
@RestController
@RequestMapping("/agent")
@CrossOrigin
@Tag(
    name = "AI Agent",
    description = "Beancounter AI Agent for natural language portfolio and market analysis"
)
class AgentController(
    private val beancounterAgent: BeancounterAgent,
    private val healthService: HealthService
) {
    private val log = LoggerFactory.getLogger(AgentController::class.java)

    @GetMapping("/login", produces = [MediaType.TEXT_HTML_VALUE])
    @Operation(
        summary = "Get the login page",
        description = "Returns a simple login page for development purposes."
    )
    fun getLoginPage(): String =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Beancounter Agent - Login</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    margin: 0;
                    padding: 20px;
                    background-color: #f5f5f5;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                }
                .login-container {
                    background: white;
                    padding: 40px;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
                    max-width: 500px;
                    width: 100%;
                }
                .header {
                    text-align: center;
                    margin-bottom: 30px;
                }
                .header h1 {
                    color: #333;
                    margin-bottom: 10px;
                }
                .header p {
                    color: #666;
                    margin: 0;
                }
                .form-group {
                    margin-bottom: 20px;
                }
                .form-group label {
                    display: block;
                    margin-bottom: 8px;
                    font-weight: 500;
                    color: #333;
                }
                .form-group input, .form-group textarea {
                    width: 100%;
                    padding: 12px;
                    border: 2px solid #e1e5e9;
                    border-radius: 8px;
                    font-size: 14px;
                    box-sizing: border-box;
                }
                .form-group input:focus, .form-group textarea:focus {
                    outline: none;
                    border-color: #007bff;
                }
                .form-group textarea {
                    height: 120px;
                    resize: vertical;
                    font-family: monospace;
                    font-size: 12px;
                }
                .btn {
                    width: 100%;
                    padding: 12px;
                    background: #007bff;
                    color: white;
                    border: none;
                    border-radius: 8px;
                    font-size: 16px;
                    font-weight: 500;
                    cursor: pointer;
                    transition: background-color 0.2s;
                }
                .btn:hover {
                    background: #0056b3;
                }
                .btn-secondary {
                    background: #6c757d;
                    margin-top: 10px;
                }
                .btn-secondary:hover {
                    background: #545b62;
                }
                .help-text {
                    font-size: 12px;
                    color: #666;
                    margin-top: 8px;
                }
                .auth0-link {
                    text-align: center;
                    margin-top: 20px;
                }
                .auth0-link a {
                    color: #007bff;
                    text-decoration: none;
                }
                .auth0-link a:hover {
                    text-decoration: underline;
                }
            </style>
        </head>
        <body>
            <div class="login-container">
                <div class="header">
                    <h1>🤖 Beancounter Agent</h1>
                    <p>Development Login</p>
                </div>

                <form id="loginForm">
                    <div class="form-group">
                        <label for="jwtToken">JWT Token (for development)</label>
                        <textarea id="jwtToken" placeholder="Paste your Auth0 JWT token here..."></textarea>
                        <div class="help-text">
                            Get your token from Auth0 or use a test token for development.
                        </div>
                    </div>

                    <button type="submit" class="btn">Login & Go to Chat</button>
                </form>

                <div class="auth0-link">
                    <p>Need a token? <a href="#" onclick="openAuth0Login()">Get one from Auth0</a></p>
                </div>
            </div>

            <script>
                document.getElementById('loginForm').addEventListener('submit', function(e) {
                    e.preventDefault();

                    const token = document.getElementById('jwtToken').value.trim();
                    if (!token) {
                        alert('Please enter a JWT token');
                        return;
                    }

                    // Store the token
                    localStorage.setItem('authToken', token);

                    // Redirect to chat
                    window.location.href = '/api/agent/chat';
                });

                function openAuth0Login() {
                    // This would typically redirect to Auth0, but for development
                    // we'll just show instructions
                    alert('For development:\n\n1. Go to your Auth0 dashboard\n2. Create a test token\n3. Or use a token from your frontend app\n4. Paste it in the textarea above');
                }
            </script>
        </body>
        </html>
        """.trimIndent()

    @GetMapping("/chat", produces = [MediaType.TEXT_HTML_VALUE])
    @Operation(
        summary = "Get the chat interface",
        description = "Returns the HTML chat interface for testing the agent."
    )
    fun getChatInterfaceAtPath(): String = getChatInterfaceHtml()

    private fun getChatInterfaceHtml(): String =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Beancounter AI Agent Chat</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    margin: 0;
                    padding: 20px;
                    background-color: #f5f5f5;
                }
                .container {
                    max-width: 800px;
                    margin: 0 auto;
                    background: white;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
                    overflow: hidden;
                }
                .header {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: white;
                    padding: 20px;
                    text-align: center;
                }
                .header h1 {
                    margin: 0;
                    font-size: 24px;
                }
                .header p {
                    margin: 8px 0 0 0;
                    opacity: 0.9;
                }
                .status-bar {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-top: 15px;
                    padding: 10px 15px;
                    background: rgba(255, 255, 255, 0.1);
                    border-radius: 8px;
                    font-size: 12px;
                }
                .status-light {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .light {
                    width: 12px;
                    height: 12px;
                    border-radius: 50%;
                    animation: pulse 2s infinite;
                }
                .light.green {
                    background: #28a745;
                    box-shadow: 0 0 10px rgba(40, 167, 69, 0.5);
                }
                .light.amber {
                    background: #ffc107;
                    box-shadow: 0 0 10px rgba(255, 193, 7, 0.5);
                }
                .light.red {
                    background: #dc3545;
                    box-shadow: 0 0 10px rgba(220, 53, 69, 0.5);
                }
                .light.gray {
                    background: #6c757d;
                    box-shadow: none;
                    animation: none;
                }
                @keyframes pulse {
                    0% { opacity: 1; }
                    50% { opacity: 0.5; }
                    100% { opacity: 1; }
                }
                .status-text {
                    font-weight: 500;
                }
                .last-updated {
                    opacity: 0.8;
                    font-size: 11px;
                }
                .refresh-button {
                    background: rgba(255, 255, 255, 0.2);
                    border: 1px solid rgba(255, 255, 255, 0.3);
                    color: white;
                    padding: 4px 8px;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 11px;
                    transition: background-color 0.2s;
                }
                .refresh-button:hover {
                    background: rgba(255, 255, 255, 0.3);
                }
                .refresh-button:disabled {
                    opacity: 0.5;
                    cursor: not-allowed;
                }
                .chat-container {
                    height: 500px;
                    overflow-y: auto;
                    padding: 20px;
                    border-bottom: 1px solid #eee;
                }
                .message {
                    margin-bottom: 16px;
                    display: flex;
                    align-items: flex-start;
                }
                .message.user {
                    justify-content: flex-end;
                }
                .message.agent {
                    justify-content: flex-start;
                }
                .message-content {
                    max-width: 70%;
                    padding: 12px 16px;
                    border-radius: 18px;
                    word-wrap: break-word;
                }
                .message.user .message-content {
                    background: #007bff;
                    color: white;
                }
                .message.agent .message-content {
                    background: #f1f3f4;
                    color: #333;
                }
                .message-meta {
                    font-size: 12px;
                    opacity: 0.7;
                    margin-top: 4px;
                }
                .input-container {
                    padding: 20px;
                    display: flex;
                    gap: 12px;
                }
                .input-field {
                    flex: 1;
                    padding: 12px 16px;
                    border: 2px solid #e1e5e9;
                    border-radius: 24px;
                    font-size: 14px;
                    outline: none;
                    transition: border-color 0.2s;
                }
                .input-field:focus {
                    border-color: #007bff;
                }
                .send-button {
                    padding: 12px 24px;
                    background: #007bff;
                    color: white;
                    border: none;
                    border-radius: 24px;
                    cursor: pointer;
                    font-size: 14px;
                    font-weight: 500;
                    transition: background-color 0.2s;
                }
                .send-button:hover {
                    background: #0056b3;
                }
                .send-button:disabled {
                    background: #ccc;
                    cursor: not-allowed;
                }
                .loading {
                    display: none;
                    text-align: center;
                    padding: 20px;
                    color: #666;
                }
                .error {
                    background: #f8d7da;
                    color: #721c24;
                    padding: 12px 16px;
                    border-radius: 8px;
                    margin: 16px 20px;
                    border: 1px solid #f5c6cb;
                }
                .error-message {
                    background: #f8d7da;
                    border: 1px solid #f5c6cb;
                }
                .error-message .message-content {
                    background: #f8d7da;
                    color: #721c24;
                }
                .error-icon {
                    font-size: 20px;
                    margin-right: 10px;
                }
                .error-text {
                    flex: 1;
                }
                .error-text small {
                    color: #6c757d;
                    font-size: 12px;
                    line-height: 1.4;
                }
                .examples {
                    padding: 20px;
                    background: #f8f9fa;
                    border-top: 1px solid #eee;
                }
                .examples h3 {
                    margin: 0 0 12px 0;
                    color: #333;
                    font-size: 16px;
                }
                .example-queries {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 8px;
                }
                .example-query {
                    background: white;
                    border: 1px solid #dee2e6;
                    border-radius: 16px;
                    padding: 8px 12px;
                    font-size: 12px;
                    cursor: pointer;
                    transition: all 0.2s;
                }
                .example-query:hover {
                    background: #e9ecef;
                    border-color: #007bff;
                }
            </style>
        </head>
        <body>
            <div class="container">
                    <div class="header">
                        <h1>🤖 Beancounter AI Agent</h1>
                        <p>Ask me about your portfolios, market data, or corporate events</p>
                        <div style="margin-top: 10px;">
                            <input type="text" id="authToken" placeholder="Enter your Auth0 JWT token"
                                   style="width: 300px; padding: 8px; border-radius: 4px; border: 1px solid #ccc; font-size: 12px;">
                            <button onclick="setAuthToken()" style="margin-left: 8px; padding: 8px 12px; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); border-radius: 4px; cursor: pointer; font-size: 12px;">Set Token</button>
                        </div>
                        <div class="status-bar">
                            <div class="status-light">
                                <div class="light gray" id="statusLight"></div>
                                <span class="status-text" id="statusText">Checking services...</span>
                            </div>
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="refresh-button" id="refreshButton" onclick="refreshHealthStatus()">🔄 Refresh</button>
                                <div class="last-updated" id="lastUpdated">Never</div>
                            </div>
                        </div>
                    </div>

                <div class="chat-container" id="chatContainer">
                    <div class="message agent">
                        <div class="message-content">
                            <div>Hello! I'm your Beancounter AI Agent. I can help you with:</div>
                            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
                                <li>Portfolio analysis and positions</li>
                                <li>Market data and asset information</li>
                                <li>Corporate events and dividends</li>
                                <li>FX rates and currency conversions</li>
                                <li>Position valuations and metrics</li>
                            </ul>
                            <div class="message-meta">Agent • Just now</div>
                        </div>
                    </div>
                </div>

                <div class="loading" id="loading">
                    <div>🤔 Thinking...</div>
                </div>

                <div class="input-container">
                    <input type="text" id="messageInput" class="input-field" placeholder="Ask me anything about your portfolios..." autocomplete="off">
                    <button id="sendButton" class="send-button">Send</button>
                </div>

                <div class="examples">
                    <h3>💡 Try these examples:</h3>
                    <div class="example-queries">
                        <div class="example-query" onclick="sendExample('What are my positions in portfolio TEST?')">Portfolio positions</div>
                        <div class="example-query" onclick="sendExample('Show me market data for AAPL')">Market data</div>
                        <div class="example-query" onclick="sendExample('What events are coming up for my portfolio?')">Upcoming events</div>
                        <div class="example-query" onclick="sendExample('Get me FX rates for USD to EUR')">FX rates</div>
                        <div class="example-query" onclick="sendExample('Analyze my portfolio TEST')">Portfolio analysis</div>
                        <div class="example-query" onclick="sendExample('What markets are available?')">Available markets</div>
                    </div>
                </div>
            </div>

            <script>
                const chatContainer = document.getElementById('chatContainer');
                const messageInput = document.getElementById('messageInput');
                const sendButton = document.getElementById('sendButton');
                const loading = document.getElementById('loading');

                function addMessage(content, isUser = false) {
                    const messageDiv = document.createElement('div');
                    messageDiv.className = 'message ' + (isUser ? 'user' : 'agent');

                    const contentDiv = document.createElement('div');
                    contentDiv.className = 'message-content';

                    if (typeof content === 'string') {
                        contentDiv.innerHTML = content.replace(/\n/g, '<br>');
                    } else {
                        contentDiv.innerHTML = formatResponse(content);
                    }

                    const metaDiv = document.createElement('div');
                    metaDiv.className = 'message-meta';
                    metaDiv.textContent = (isUser ? 'You' : 'Agent') + ' • ' + new Date().toLocaleTimeString();

                    contentDiv.appendChild(metaDiv);
                    messageDiv.appendChild(contentDiv);
                    chatContainer.appendChild(messageDiv);

                    chatContainer.scrollTop = chatContainer.scrollHeight;
                }

                function formatResponse(response) {
                    let html = '<strong>Response:</strong> ' + response.response + '<br><br>';

                    if (response.actions && response.actions.length > 0) {
                        html += '<strong>Actions taken:</strong><br>';
                        response.actions.forEach(function(action) {
                            html += '• ' + action.description + '<br>';
                        });
                        html += '<br>';
                    }

                    if (response.results && Object.keys(response.results).length > 0) {
                        html += '<strong>Results:</strong><br>';
                        for (var key in response.results) {
                            var result = response.results[key];
                            if (typeof result === 'object' && result !== null) {
                                html += '<details><summary>' + key + '</summary><pre>' + JSON.stringify(result, null, 2) + '</pre></details>';
                            } else {
                                html += '<strong>' + key + ':</strong> ' + result + '<br>';
                            }
                        }
                    }

                    if (response.error) {
                        html += '<br><strong style="color: #dc3545;">Error:</strong> ' + response.error;
                    }

                    return html;
                }

                function showLoading() {
                    loading.style.display = 'block';
                    sendButton.disabled = true;
                    sendButton.textContent = 'Sending...';
                }

                function hideLoading() {
                    loading.style.display = 'none';
                    sendButton.disabled = false;
                    sendButton.textContent = 'Send';
                }

                function showError(message) {
                    const errorDiv = document.createElement('div');
                    errorDiv.className = 'message error-message';
                    errorDiv.innerHTML =
                        '<div class="message-content">' +
                            '<div class="error-icon">⚠️</div>' +
                            '<div class="error-text">' +
                                '<strong>Error:</strong> ' + message +
                                '<br><br>' +
                                '<small>' +
                                    '💡 <strong>Common solutions:</strong><br>' +
                                    '• Check if all services are running (Data, Event, Position)<br>' +
                                    '• Verify your authentication token is valid<br>' +
                                    '• Try a simpler query like "list my portfolios"<br>' +
                                    '• Check the browser console for more details' +
                                '</small>' +
                            '</div>' +
                        '</div>' +
                        '<div class="message-meta">' +
                            'System • ' + new Date().toLocaleTimeString() +
                        '</div>';
                    chatContainer.appendChild(errorDiv);
                    chatContainer.scrollTop = chatContainer.scrollHeight;
                }

                async function sendMessage() {
                    const message = messageInput.value.trim();
                    if (!message) return;

                    addMessage(message, true);
                    messageInput.value = '';
                    showLoading();

                    try {
                        // Get the auth token from localStorage or sessionStorage
                        const token = localStorage.getItem('authToken') || sessionStorage.getItem('authToken');

                        const headers = {
                            'Content-Type': 'application/json',
                        };

                        // Add Authorization header if token is available
                        if (token) {
                            headers['Authorization'] = 'Bearer ' + token;
                        }

                        const response = await fetch('/api/agent/query', {
                            method: 'POST',
                            headers: headers,
                            body: JSON.stringify({
                                query: message,
                                context: null
                            })
                        });

                        if (!response.ok) {
                            let errorMessage = 'HTTP ' + response.status + ': ' + response.statusText;

                            // Try to get more detailed error information from ProblemDetail
                            try {
                                const errorData = await response.json();

                                // Handle ProblemDetail format (application/problem+json)
                                if (errorData.detail) {
                                    errorMessage += ' - ' + errorData.detail;
                                } else if (errorData.title) {
                                    errorMessage += ' - ' + errorData.title;
                                } else if (errorData.message) {
                                    errorMessage += ' - ' + errorData.message;
                                } else if (errorData.error) {
                                    errorMessage += ' - ' + errorData.error;
                                }

                                // Add additional context for common error types
                                if (response.status === 400) {
                                    errorMessage += ' (Bad Request - check your query format)';
                                } else if (response.status === 401) {
                                    errorMessage += ' (Unauthorized - check your authentication token)';
                                } else if (response.status === 403) {
                                    errorMessage += ' (Forbidden - insufficient permissions)';
                                } else if (response.status === 404) {
                                    errorMessage += ' (Not Found - service endpoint may not exist)';
                                } else if (response.status === 500) {
                                    errorMessage += ' (Internal Server Error - check if MCP services are running)';
                                }

                                // Show the full error response for debugging
                                console.error('Full error response:', errorData);
                            } catch (parseError) {
                                // If we can't parse the error response, try to get the text
                                try {
                                    const errorText = await response.text();
                                    if (errorText) {
                                        errorMessage += ' - ' + errorText;
                                    }
                                } catch (textError) {
                                    // Fall back to basic error message
                                }
                            }

                            throw new Error(errorMessage);
                        }

                        const data = await response.json();
                        addMessage(data, false);
                    } catch (error) {
                        console.error('Error:', error);
                        showError(error.message);
                    } finally {
                        hideLoading();
                    }
                }

                function sendExample(query) {
                    messageInput.value = query;
                    sendMessage();
                }

                function setAuthToken() {
                    const token = document.getElementById('authToken').value.trim();
                    if (token) {
                        localStorage.setItem('authToken', token);
                        alert('Auth token set successfully!');
                    } else {
                        alert('Please enter a valid token');
                    }
                }

                // Health status functions
                async function checkHealthStatus() {
                    try {
                        const response = await fetch('/api/agent/health');
                        if (response.ok) {
                            const healthData = await response.json();
                            updateStatusLight(healthData);
                        } else {
                            updateStatusLight({ overallStatus: 'RED', summary: 'Health check failed' });
                        }
                    } catch (error) {
                        console.error('Health check failed:', error);
                        updateStatusLight({ overallStatus: 'RED', summary: 'Health check failed' });
                    }
                }

                async function refreshHealthStatus() {
                    const refreshButton = document.getElementById('refreshButton');
                    const originalText = refreshButton.textContent;

                    // Disable button and show loading state
                    refreshButton.disabled = true;
                    refreshButton.textContent = '🔄 Checking...';

                    try {
                        await checkHealthStatus();
                    } finally {
                        // Re-enable button
                        refreshButton.disabled = false;
                        refreshButton.textContent = originalText;
                    }
                }

                function updateStatusLight(healthData) {
                    const statusLight = document.getElementById('statusLight');
                    const statusText = document.getElementById('statusText');
                    const lastUpdated = document.getElementById('lastUpdated');

                    // Remove all status classes
                    statusLight.className = 'light';

                    // Set the appropriate status
                    switch (healthData.overallStatus) {
                        case 'GREEN':
                            statusLight.classList.add('green');
                            statusText.textContent = 'All services operational';
                            break;
                        case 'AMBER':
                            statusLight.classList.add('amber');
                            statusText.textContent = 'Some services partially available';
                            break;
                        case 'RED':
                            statusLight.classList.add('red');
                            statusText.textContent = 'Services unavailable';
                            break;
                        default:
                            statusLight.classList.add('gray');
                            statusText.textContent = 'Status unknown';
                    }

                    // Update last checked time
                    const now = new Date();
                    lastUpdated.textContent = 'Updated ' + now.toLocaleTimeString();

                    // Log detailed status for debugging
                    console.log('Health Status:', healthData);
                    if (healthData.services) {
                        healthData.services.forEach(function(service) {
                            if (service.status !== 'UP') {
                                console.warn(service.name + ': ' + service.status + ' - ' + service.error);
                            }
                        });
                    }
                }

                // Check health status on page load and every 30 seconds
                document.addEventListener('DOMContentLoaded', function() {
                    checkHealthStatus();
                    setInterval(checkHealthStatus, 30000); // Check every 30 seconds
                });

                // Event listeners
                sendButton.addEventListener('click', sendMessage);
                messageInput.addEventListener('keypress', function(e) {
                    if (e.key === 'Enter') {
                        sendMessage();
                    }
                });

                // Focus input on load
                messageInput.focus();
            </script>
        </body>
        </html>
        """.trimIndent()

    @GetMapping("/health")
    @Operation(
        summary = "Get service health status",
        description = "Get the health status of all MCP services (Data, Event, Position)"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Health status retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Health Status",
                                summary = "Example health status response",
                                value = """
                        {
                          "overallStatus": "GREEN",
                          "services": [
                            {
                              "name": "Data Service",
                              "status": "UP",
                              "responseTime": 45,
                              "lastChecked": "2024-01-15T10:30:00",
                              "error": null
                            },
                            {
                              "name": "Event Service",
                              "status": "UP",
                              "responseTime": 32,
                              "lastChecked": "2024-01-15T10:30:00",
                              "error": null
                            },
                            {
                              "name": "Position Service",
                              "status": "UP",
                              "responseTime": 28,
                              "lastChecked": "2024-01-15T10:30:00",
                              "error": null
                            }
                          ],
                          "lastChecked": "2024-01-15T10:30:00",
                          "summary": "3 of 3 services available"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getHealthStatus(): ServiceHealthStatus = healthService.checkAllServicesHealth()

    @PostMapping("/query")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
    )
    @Operation(
        summary = "Process natural language query",
        description = "Process a natural language query and return structured results with AI-generated response"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Query processed successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Portfolio Analysis Query",
                                value = """
                        {
                          "query": "Show me my portfolio analysis",
                          "response": "Here's your portfolio analysis. I've retrieved your portfolio information and current positions.",
                          "actions": [
                            {
                              "id": "get_portfolio",
                              "type": "GET_PORTFOLIO",
                              "description": "Get portfolio information"
                            }
                          ],
                          "results": {
                            "get_portfolio": {
                              "id": "portfolio-123",
                              "code": "MAIN",
                              "name": "Main Portfolio"
                            }
                          },
                          "timestamp": "2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun processQuery(
        @Parameter(description = "Natural language query to process")
        @RequestBody queryRequest: QueryRequest
    ): AgentResponse {
        val authentication =
            org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .authentication
        log.info(
            "Processing query '{}' with authentication: {} (type: {})",
            queryRequest.query,
            authentication?.name,
            authentication?.javaClass?.simpleName
        )

        return beancounterAgent.processQuery(queryRequest.query, queryRequest.context)
    }

    @PostMapping("/test")
    @Operation(
        summary = "Test endpoint without MCP calls",
        description = "Simple test endpoint that doesn't call external MCP services"
    )
    fun testQuery(
        @RequestBody queryRequest: QueryRequest
    ): AgentResponse =
        AgentResponse(
            query = queryRequest.query,
            response = "Test response for: ${queryRequest.query}",
            actions = emptyList(),
            results = mapOf("test" to "This is a test response without calling MCP services"),
            timestamp = java.time.LocalDate.now()
        )

    @GetMapping("/portfolio/{portfolioId}/analysis")
    @Operation(
        summary = "Get comprehensive portfolio analysis",
        description = "Get detailed analysis of a portfolio including positions, events, and metrics"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolio analysis retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Portfolio Analysis",
                                value = """
                        {
                          "portfolio": {
                            "id": "portfolio-123",
                            "code": "MAIN",
                            "name": "Main Portfolio"
                          },
                          "positions": {
                            "data": {
                              "positions": [
                                {
                                  "asset": {
                                    "id": "asset-456",
                                    "code": "AAPL",
                                    "name": "Apple Inc."
                                  },
                                  "quantity": 100,
                                  "marketValue": 15000.00
                                }
                              ]
                            }
                          },
                          "events": {
                            "data": []
                          },
                          "metrics": {
                            "totalValue": 15000.00,
                            "totalGain": 500.00
                          },
                          "analysisDate": "2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun analyzePortfolio(
        @Parameter(description = "Portfolio identifier")
        @RequestParam portfolioId: String,
        @Parameter(description = "Analysis date in YYYY-MM-DD format or 'today'")
        @RequestParam(defaultValue = "today") date: String
    ): PortfolioAnalysis = beancounterAgent.analyzePortfolio(portfolioId, date)

    @GetMapping("/market/overview")
    @Operation(
        summary = "Get market overview",
        description = "Get comprehensive market overview including markets, currencies, and FX rates"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Market overview retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Market Overview",
                                value = """
                        {
                          "markets": {
                            "data": [
                              {
                                "code": "NYSE",
                                "name": "New York Stock Exchange",
                                "currencyId": "USD"
                              }
                            ]
                          },
                          "currencies": [
                            {
                              "id": "USD",
                              "code": "USD",
                              "name": "US Dollar"
                            }
                          ],
                          "fxRates": {
                            "USD-EUR": {
                              "fromCurrency": "USD",
                              "toCurrency": "EUR",
                              "rate": 0.85
                            }
                          },
                          "timestamp": "2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getMarketOverview(): MarketOverview = beancounterAgent.getMarketOverview()

    @PostMapping("/portfolio/{portfolioId}/events/load")
    @Operation(
        summary = "Load events for portfolio",
        description = "Load corporate events from external sources for a specific portfolio"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Event loading initiated successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Event Loading Response",
                                value = """
                        {
                          "portfolioId": "portfolio-123",
                          "fromDate": "2024-01-01",
                          "status": "loading_started",
                          "message": "Event loading initiated for portfolio portfolio-123 from 2024-01-01"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun loadEventsForPortfolio(
        @Parameter(description = "Portfolio identifier")
        @RequestParam portfolioId: String,
        @Parameter(description = "Start date in YYYY-MM-DD format or 'today'")
        @RequestParam fromDate: String
    ): Map<String, Any> = beancounterAgent.loadEventsForPortfolio(portfolioId, fromDate)

    @PostMapping("/portfolio/{portfolioId}/events/backfill")
    @Operation(
        summary = "Backfill events for portfolio",
        description = "Backfill and reprocess existing corporate events for a portfolio"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Event backfilling initiated successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Event Backfill Response",
                                value = """
                        {
                          "portfolioId": "portfolio-123",
                          "fromDate": "2024-01-01",
                          "toDate": "2024-01-15",
                          "status": "backfill_started",
                          "message": "Event backfilling initiated for portfolio portfolio-123 from 2024-01-01 to 2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun backfillEventsForPortfolio(
        @Parameter(description = "Portfolio identifier")
        @RequestParam portfolioId: String,
        @Parameter(description = "Start date in YYYY-MM-DD format or 'today'")
        @RequestParam fromDate: String,
        @Parameter(description = "End date in YYYY-MM-DD format (optional)")
        @RequestParam(required = false) toDate: String?
    ): Map<String, Any> = beancounterAgent.backfillEvents(portfolioId, fromDate, toDate)

    @GetMapping("/capabilities")
    @Operation(
        summary = "Get agent capabilities",
        description = "Get information about what the agent can do"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Agent capabilities retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Agent Capabilities",
                                value = """
                        {
                          "name": "Beancounter AI Agent",
                          "version": "1.0.0",
                          "capabilities": [
                            "Portfolio analysis and management",
                            "Market data retrieval and analysis",
                            "Corporate event processing",
                            "Position valuation and reporting",
                            "Natural language query processing",
                            "FX rate monitoring",
                            "Multi-service orchestration"
                          ],
                          "supportedQueries": [
                            "Show me my portfolio analysis",
                            "What's the market overview?",
                            "Load events for my portfolio",
                            "Get current positions",
                            "What are the FX rates?"
                          ],
                          "mcpServices": [
                            {
                              "name": "Data Service",
                              "url": "http://localhost:9510/api/mcp",
                              "capabilities": ["Assets", "Portfolios", "Market Data", "FX Rates"]
                            },
                            {
                              "name": "Event Service",
                              "url": "http://localhost:9520/api/mcp",
                              "capabilities": ["Corporate Events", "Event Loading", "Backfilling"]
                            },
                            {
                              "name": "Position Service",
                              "url": "http://localhost:9500/api/mcp",
                              "capabilities": ["Positions", "Valuations", "Metrics"]
                            }
                          ]
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getCapabilities(): Map<String, Any> =
        mapOf(
            "name" to "Beancounter AI Agent",
            "version" to "1.0.0",
            "capabilities" to
                listOf(
                    "Portfolio analysis and management",
                    "Market data retrieval and analysis",
                    "Corporate event processing",
                    "Position valuation and reporting",
                    "Natural language query processing",
                    "FX rate monitoring",
                    "Multi-service orchestration"
                ),
            "supportedQueries" to
                listOf(
                    "Show me my portfolio analysis",
                    "What's the market overview?",
                    "Load events for my portfolio",
                    "Get current positions",
                    "What are the FX rates?"
                ),
            "mcpServices" to
                listOf(
                    mapOf(
                        "name" to "Data Service",
                        "url" to "http://localhost:9510/api/mcp",
                        "capabilities" to listOf("Assets", "Portfolios", "Market Data", "FX Rates")
                    ),
                    mapOf(
                        "name" to "Event Service",
                        "url" to "http://localhost:9520/api/mcp",
                        "capabilities" to listOf("Corporate Events", "Event Loading", "Backfilling")
                    ),
                    mapOf(
                        "name" to "Position Service",
                        "url" to "http://localhost:9500/api/mcp",
                        "capabilities" to listOf("Positions", "Valuations", "Metrics")
                    )
                )
        )
}

/**
 * Request object for natural language queries
 */
data class QueryRequest(
    val query: String,
    val context: Map<String, Any> = emptyMap()
)