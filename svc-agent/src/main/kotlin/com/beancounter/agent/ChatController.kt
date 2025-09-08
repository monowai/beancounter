package com.beancounter.agent

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for serving the chat interface at the root path
 */
@RestController
class ChatController {
    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    @Operation(
        summary = "Get the chat interface",
        description = "Returns the HTML chat interface for testing the agent at the root path."
    )
    fun getChatInterface(): String = getChatInterfaceHtml()

    private fun getChatInterfaceHtml(): String =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Beancounter AI Agent</title>
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                }
                .container {
                    width: 100%;
                    max-width: 800px;
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
                    background: #f8f9fa;
                    color: #333;
                }
                .message-meta {
                    font-size: 11px;
                    color: #666;
                    margin-top: 4px;
                }
                .input-container {
                    padding: 20px;
                    background: #f8f9fa;
                    display: flex;
                    gap: 12px;
                    align-items: center;
                }
                .input-field {
                    flex: 1;
                    padding: 12px 16px;
                    border: 1px solid #ddd;
                    border-radius: 24px;
                    font-size: 14px;
                    outline: none;
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
                .send-button:hover:not(:disabled) {
                    background: #0056b3;
                }
                .send-button:disabled {
                    background: #6c757d;
                    cursor: not-allowed;
                }
                .loading {
                    display: none;
                    text-align: center;
                    padding: 20px;
                    color: #666;
                }
                .examples {
                    padding: 20px;
                    background: #f8f9fa;
                    border-top: 1px solid #eee;
                }
                .examples h3 {
                    margin-bottom: 12px;
                    color: #333;
                    font-size: 14px;
                }
                .example-query {
                    display: inline-block;
                    margin: 4px 8px 4px 0;
                    padding: 6px 12px;
                    background: white;
                    border: 1px solid #ddd;
                    border-radius: 16px;
                    cursor: pointer;
                    font-size: 12px;
                    color: #666;
                    transition: all 0.2s;
                }
                .example-query:hover {
                    background: #007bff;
                    color: white;
                    border-color: #007bff;
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
                            <ul style="margin-top: 8px; padding-left: 20px;">
                                <li>Portfolio analysis and positions</li>
                                <li>Market data and currency rates</li>
                                <li>Corporate events and actions</li>
                                <li>Investment insights and recommendations</li>
                            </ul>
                            <div style="margin-top: 12px; font-size: 12px; color: #666;">
                                💡 <strong>Tip:</strong> Set your Auth0 JWT token above to get started!
                            </div>
                        </div>
                        <div class="message-meta">
                            Agent • <script>document.write(new Date().toLocaleTimeString())</script>
                        </div>
                    </div>
                </div>
                
                <div class="examples">
                    <h3>Try these examples:</h3>
                    <div class="example-query" onclick="sendExample('List my portfolios')">List portfolios</div>
                    <div class="example-query" onclick="sendExample('What are my positions in portfolio TEST?')">Portfolio positions</div>
                    <div class="example-query" onclick="sendExample('Show me market data for AAPL')">Market data</div>
                    <div class="example-query" onclick="sendExample('What events are coming up for my portfolio?')">Upcoming events</div>
                    <div class="example-query" onclick="sendExample('Analyze my portfolio TEST')">Portfolio analysis</div>
                    <div class="example-query" onclick="sendExample('Check service connectivity')">Service status</div>
                </div>
                
                <div class="input-container">
                    <input type="text" id="messageInput" class="input-field" placeholder="Ask me anything about your portfolios..." autocomplete="off">
                    <button id="sendButton" class="send-button" onclick="sendMessage()">Send</button>
                </div>
                
                <div class="loading" id="loading">
                    <div>🤔 Thinking...</div>
                </div>
            </div>

            <script>
                const chatContainer = document.getElementById('chatContainer');
                const messageInput = document.getElementById('messageInput');
                const sendButton = document.getElementById('sendButton');
                const loading = document.getElementById('loading');

                function addMessage(content, isUser) {
                    const messageDiv = document.createElement('div');
                    messageDiv.className = 'message ' + (isUser ? 'user' : 'agent');
                    
                    const contentDiv = document.createElement('div');
                    contentDiv.className = 'message-content';
                    
                    if (typeof content === 'string') {
                        contentDiv.innerHTML = renderMarkdown(content);
                    } else {
                        contentDiv.innerHTML = renderMarkdown(formatResponse(content));
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
                                // Try to format common data structures more readably
                                if (result.data && Array.isArray(result.data)) {
                                    html += '<details><summary>' + key + ' (' + result.data.length + ' items)</summary>';
                                    html += '<div style="margin: 10px 0; padding: 10px; background: #f8f9fa; border-radius: 4px;">';
                                    html += '<pre style="white-space: pre-wrap; font-size: 12px;">' + JSON.stringify(result, null, 2) + '</pre>';
                                    html += '</div></details>';
                                } else {
                                    html += '<details><summary>' + key + '</summary>';
                                    html += '<div style="margin: 10px 0; padding: 10px; background: #f8f9fa; border-radius: 4px;">';
                                    html += '<pre style="white-space: pre-wrap; font-size: 12px;">' + JSON.stringify(result, null, 2) + '</pre>';
                                    html += '</div></details>';
                                }
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

                async function sendMessage() {
                    const message = messageInput.value.trim();
                    if (!message) return;
                    addMessage(message, true);
                    messageInput.value = '';
                    showLoading();
                    try {
                        const token = localStorage.getItem('authToken') || sessionStorage.getItem('authToken');
                        const headers = { 'Content-Type': 'application/json' };
                        if (token) { headers['Authorization'] = 'Bearer ' + token; }
                        const response = await fetch('/api/agent/query', {
                            method: 'POST',
                            headers: headers,
                            body: JSON.stringify({ query: message, context: {} })
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

                function renderMarkdown(text) {
                    // Simple markdown renderer for basic formatting
                    return text
                        // Headers
                        .replace(/^### (.*$)/gim, '<h3>$1</h3>')
                        .replace(/^## (.*$)/gim, '<h2>$1</h2>')
                        .replace(/^# (.*$)/gim, '<h1>$1</h1>')
                        // Bold
                        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                        // Italic
                        .replace(/\*(.*?)\*/g, '<em>$1</em>')
                        // Code blocks
                        .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
                        // Inline code
                        .replace(/`(.*?)`/g, '<code>$1</code>')
                        // Tables - handle markdown tables
                        .replace(/(\|.*\|[\s\S]*?)(?=\n\n|\n$|$)/g, function(match) {
                            const lines = match.trim().split('\n').filter(line => line.trim());
                            if (lines.length < 3) return match; // Need header, separator, and at least one data row
                            
                            let table = '<table style="border-collapse: collapse; width: 100%; margin: 10px 0; border: 1px solid #dee2e6;">';
                            
                            // Header row
                            const headerCells = lines[0].split('|').map(cell => cell.trim()).filter(cell => cell);
                            if (headerCells.length > 0) {
                                table += '<tr>';
                                headerCells.forEach(cell => {
                                    table += '<th style="background-color: #f8f9fa; font-weight: bold; border: 1px solid #dee2e6; padding: 8px; text-align: left;">' + cell + '</th>';
                                });
                                table += '</tr>';
                            }
                            
                            // Data rows (skip separator row at index 1)
                            for (let i = 2; i < lines.length; i++) {
                                const cells = lines[i].split('|').map(cell => cell.trim()).filter(cell => cell);
                                if (cells.length > 0) {
                                    table += '<tr>';
                                    cells.forEach(cell => {
                                        table += '<td style="border: 1px solid #dee2e6; padding: 8px;">' + cell + '</td>';
                                    });
                                    table += '</tr>';
                                }
                            }
                            
                            table += '</table>';
                            return table;
                        })
                        // Line breaks
                        .replace(/\n/g, '<br>')
                        // Lists
                        .replace(/^• (.*$)/gim, '<li>$1</li>')
                        .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
                }

                // Event listeners
                sendButton.addEventListener('click', sendMessage);
                messageInput.addEventListener('keypress', function(e) {
                    if (e.key === 'Enter') {
                        sendMessage();
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
}