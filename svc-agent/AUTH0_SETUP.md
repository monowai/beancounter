# Auth0 Setup for Beancounter Agent

## Environment Variables

Set these environment variables for your Auth0 configuration:

```bash
# Your Auth0 domain (e.g., https://your-domain.auth0.com/)
export AUTH0_DOMAIN=https://your-domain.auth0.com/

# Your Auth0 API identifier (audience)
export AUTH0_AUDIENCE=your-api-identifier

# The claim that contains the user's email
export AUTH0_EMAIL_CLAIM=https://your-domain.com/email
```

## Auth0 Configuration

### 1. Create an API in Auth0
- Go to your Auth0 Dashboard
- Navigate to Applications > APIs
- Create a new API with:
  - Name: "Beancounter Agent API"
  - Identifier: `your-api-identifier` (use this as AUTH0_AUDIENCE)
  - Signing Algorithm: RS256

### 2. Create a Machine-to-Machine Application
- Go to Applications > Applications
- Create a new application
- Choose "Machine to Machine Applications"
- Authorize it for your API
- Grant the necessary scopes

### 3. Configure Scopes
Your API should have these scopes:
- `read:portfolios` - Read portfolio data
- `read:positions` - Read position data
- `read:events` - Read corporate events
- `read:market-data` - Read market data

## Testing the Agent

### Authentication Required
The agent is configured with `auth.web: true` to properly forward JWT tokens to MCP services:
- **Chat interface is accessible** without authentication (for development)
- **MCP service calls require** valid JWT tokens
- **JWT tokens are forwarded** to all downstream services

### 1. Configure Auth0 Environment Variables
Set these environment variables:
```bash
export AUTH0_DOMAIN="https://your-domain.auth0.com/"
export AUTH0_AUDIENCE="your-api-identifier"
export AUTH0_EMAIL_CLAIM="https://your-domain.com/email"
```

### 2. Start the Agent
```bash
./gradlew :svc-agent:bootRun
```

### 3. Access the Chat Interface
- **Main chat interface**: `http://localhost:9530/api/` (root path - no authentication required)
- **Alternative chat**: `http://localhost:9530/api/agent/chat`
- **Login page**: `http://localhost:9530/api/agent/login`

### 4. Get a JWT Token
You need a valid JWT token to make MCP service calls. Get one using:

**Option A: Auth0 Test Client**
1. Go to your Auth0 dashboard
2. Navigate to Applications > Test tab
3. Copy the access token

**Option B: curl command**
```bash
curl --request POST \
  --url https://your-domain.auth0.com/oauth/token \
  --header 'content-type: application/json' \
  --data '{
    "client_id": "your-client-id",
    "client_secret": "your-client-secret",
    "audience": "your-api-identifier",
    "grant_type": "client_credentials"
  }'
```

**Option C: From your frontend application**
1. Open browser dev tools
2. Go to Application/Storage > Local Storage
3. Find your JWT token

### 5. Set the Token in Chat Interface
1. Paste your JWT token in the "Enter your Auth0 JWT token" field
2. Click "Set Token"
3. Start chatting!

### 6. Test the Agent
Try these queries:
- "Show me my portfolio overview"
- "What are my current positions?"
- "Load corporate events for my portfolio"
- "Get market data for AAPL"

## Token Forwarding

The agent automatically forwards your JWT token to all MCP services (Data, Event, Position) so they can:
- Identify the authenticated user
- Apply proper authorization rules
- Access user-specific data

## Security Notes

- JWT tokens are stored in browser localStorage for the session
- Tokens are automatically included in all API requests
- The agent validates tokens using Auth0's public keys
- All MCP service calls include the user's authentication context
