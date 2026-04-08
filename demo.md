# CLM-Nexus-AI: Enterprise GenAI Abstraction Framework

> **CLM-Nexus-AI** is an enterprise abstraction library built on **LangChain4j** and **LangGraph4j** for Spring Boot 3 applications integrating with **Google Gemini (Vertex AI)**. It eliminates the boilerplate of authentication, model configuration, state persistence, and observability — letting developers focus entirely on business logic.

| Property | Value |
|---|---|
| **Group ID** | `com.db.clm.kyc.ai` |
| **Artifact ID** | `clm-nexus-ai` |
| **Classifier** | `springboot3` |
| **Java** | 17+ |
| **Framework** | Spring Boot 3 |
| **LLM** | Google Gemini (exclusive) |

---

## Table of Contents

1. [Why Nexus-AI](#why-nexus-ai)
2. [Architecture Overview](#architecture-overview)
3. [Getting Started](#getting-started)
4. [Authentication (Zero-Key Management)](#authentication)
5. [Chat Models](#chat-models)
6. [Building Agents — AgentSpec & AgentFactory](#building-agents)
7. [Workflow Engine](#workflow-engine)
8. [Agent Composition Patterns](#agent-composition-patterns)
9. [State Persistence & Checkpoints](#state-persistence--checkpoints)
10. [Custom Stores (Database Layer)](#custom-stores)
11. [Observability & Listeners](#observability--listeners)
12. [Spring Boot Auto-Configuration](#spring-boot-auto-configuration)
13. [Safety Settings](#safety-settings)
14. [Configuration Reference](#configuration-reference)

---

## Why Nexus-AI

Without Nexus-AI, integrating Google Gemini into an enterprise Spring Boot application requires developers to manually handle:

| Concern | Without Nexus-AI | With Nexus-AI |
|---|---|---|
| **Authentication** | Write Azure AD JWT assertion, WIF token exchange, Google SA impersonation, token refresh | Set YAML properties. Framework handles everything. |
| **Model Integration** | Raw HTTP clients or incomplete langchain4j Gemini support | `@Autowired ChatModel` — ready to use |
| **Observability** | Build custom trace listeners for every LLM interaction | Auto-configured Langfuse tracing — zero code |
| **State Persistence** | Implement checkpoint storage, chat memory, workflow tracking | Oracle-backed, auto-configured, transparent |
| **JSON Output** | Parse unpredictable LLM responses manually | Built-in JSON sanitizer, structured output |
| **Configuration** | Hardcoded URLs, credentials, model parameters | Centralized YAML-driven configuration |

**Developer workflow with Nexus-AI:**
```
Add Maven Dependency → Configure application.yml → @Autowired ChatModel → Build agents
```

---

## Architecture Overview

```
┌──────────────────┐
│  User Application │
└────────┬─────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│         CLM-Nexus-AI: Central Abstraction Layer             │
│                                                             │
│  ┌─────────────┐ ┌──────────────┐ ┌───────────────────────┐│
│  │ YAML Config  │ │  Automated   │ │  Standardized JSON    ││
│  │ & Properties │ │  State &     │ │  Output               ││
│  │              │ │  Memory      │ │                       ││
│  └─────────────┘ └──────────────┘ └───────────────────────┘│
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Centralized Developer Experience                       ││
│  │  • WorkflowEngine  • AgentFactory  • AgentSpec          ││
│  │  • ChatModel beans  • Listeners   • Stores              ││
│  └─────────────────────────────────────────────────────────┘│
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│              Integrated Ecosystem                           │
│                                                             │
│  ┌──────────────┐ ┌──────────────┐ ┌───────────────────────┐│
│  │ WIF          │ │ Custom Gemini│ │ Simplified            ││
│  │ Authentication│ │ Implementation│ │ Observability Suite  ││
│  │ (Azure AD →  │ │ (ChatModel + │ │ (Langfuse tracing,   ││
│  │  Google SA)  │ │  Streaming)  │ │  metrics, prompt     ││
│  │              │ │              │ │  versioning)          ││
│  └──────────────┘ └──────────────┘ └───────────────────────┘│
│                                                             │
│  ┌──────────────────────────────────────────────────────────┐│
│  │  LangChain4j & LangGraph4j  •  Google Gemini (Vertex AI)││
│  │  Oracle Database             •  Langfuse                 ││
│  └──────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 5 Core Pillars

| Pillar | What It Does |
|---|---|
| **Auth Manager** | Zero-key management via Azure AD → WIF → Google SA token chain. No static keys or hardcoded credentials. |
| **Custom Chat Models** | `NexusGoogleGenAiChatModel` and `NexusGoogleGenAiStreamingChatModel` — bridge gaps in langchain4j's native Gemini support. |
| **Langfuse Observability** | Drop-in, auto-configured end-to-end tracing: token usage, latency, error rates, prompt versioning. |
| **State & Persistence** | Chat memory and multi-agent checkpoints persisted to Oracle DB. Stateless application, stateful experience. |
| **JSON Sanitizer** | Normalizes raw LLM output into predictable, parsable, type-safe structured JSON. |

### Capability Matrix

| Feature | Supported | Not Supported |
|---|---|---|
| LLM Provider | Google Gemini (Vertex AI) | OpenAI, Anthropic |
| Auth | WIF / Azure AD | Static API Keys |
| State | Multi-Agent Checkpoints + Chat History | — |
| Observability | Langfuse Tracing | — |
| Output | JSON Formatting + Streaming | — |
| Agent Patterns | Sequence, Loop, Parallel, Conditional | — |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven
- Spring Boot 3 application
- Google Cloud Platform: Configured Workload Identity Pool and Provider
- Azure Active Directory: Access for generating access tokens

### Step 1: Add Maven Dependency

```xml
<dependency>
    <groupId>com.db.clm.kyc.ai</groupId>
    <artifactId>clm-nexus-ai</artifactId>
    <version>YOUR_LIBRARY_VERSION</version>
    <classifier>springboot3</classifier>
</dependency>
```

> **Note:** The `springboot3` classifier is required.

### Step 2: Configure application.yml

```yaml
clm:
  nexus:
    ai:
      enabled: true
      proxy: <proxy-url>
      wif:
        wif_provider: <path-to-wif-provider.json>
        wif_keystore: <path-to-wif-keystore.json>
      azure:
        tenant-id: <YOUR_TENANT_ID>
        client-id: "your-azure-client-id"
        client-secret: "your-azure-client-secret"
        scope: "your-azure-scope"
        grant-type: "client_credentials"
        client_assertion_type: urn:ietf:params:oauth:client-assertion-type:jwt-bearer
        thumbprint: <your-thumbprint>
        db_key_protect_key_name: <key-name>
        db_key_protect_key_passphrase: <passphrase>
      google:
        project-id: "your-gcp-project-id"
        location: "us-central1"
        transport: "GRPC"
        chat-model-name: "gemini-1.5-pro-preview-0409"
        output-tokens: 2048
        temperature: 0.2
        top-k: 40
        top-p: 0.95
        credentials:
          project-number: "your-gcp-project-number"
          location: "global"
          workload-identity-pool-id: "your-wif-pool-id"
          workload-identity-provider-id: "your-wif-provider-id"
          service-account-email: "your-sa@your-project.iam.gserviceaccount.com"
          subject-token-type: "urn:ietf:params:oauth:token-type:jwt"
```

### Step 3: Use

```java
@Service
public class MyAIService {
    private final ChatModel chatModel;

    public MyAIService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String ask(String question) {
        return chatModel.generate(question);
    }
}
```

That's it. The `ChatModel` bean is auto-configured with authentication, model parameters, safety settings, and observability listeners.

---

## Authentication

Nexus-AI provides zero-key authentication. Developers provide certificates via YAML properties — the framework handles the entire token chain.

### Authentication Flow

```
Application Properties (certs, tenant, client)
        │
        ▼
┌─────────────────────┐
│  AzureTokenService   │  ── Reads certs from DB KeyProtect
│                      │  ── Builds signed JWT (RS256)
│                      │  ── Calls Azure AD token endpoint
└─────────┬───────────┘
          │ Azure AD Access Token (JWT)
          ▼
┌─────────────────────┐
│  CredentialManager   │  ── Reads WIF provider JSON config
│                      │  ── Exchanges Azure token via WIF
│                      │  ── Impersonates Google Service Account
└─────────┬───────────┘
          │ GoogleCredentials
          ▼
┌─────────────────────┐
│  ChatModel Beans     │  ── Authenticated and ready
│  (auto-configured)   │
└─────────────────────┘
```

### Key Classes

| Class | Responsibility |
|---|---|
| `AzureTokenService` | JWT Bearer client assertion flow against Azure AD. Reads certificates from DB KeyProtect, builds signed JWT, calls token endpoint. |
| `CredentialManager` | Exchanges Azure AD token for Google credentials via WIF. Creates ExternalAccountCredentials → STS token → ServiceAccount impersonation. |
| `CredentialUtils` | Low-level credential construction and HTTP transport with proxy. |
| `GatewayGoogleCredentials` | Extends `GoogleCredentials` for enterprise API gateway routing with auto-refresh. |

### What Developers Do

Set YAML properties. That's it. No auth code, no token management, no key rotation.

### What The Framework Does

1. Loads certificates from DB KeyProtect
2. Creates signed JWT assertion
3. Exchanges with Azure AD for access token
4. Exchanges Azure token with Google WIF for STS token
5. Impersonates Google Service Account
6. Injects `GoogleCredentials` into all ChatModel beans
7. Auto-refreshes tokens before expiry

---

## Chat Models

Nexus-AI provides two custom ChatModel implementations wrapping Google's GenAI SDK (`com.google.genai`).

### NexusGoogleGenAiChatModel

`implements ChatModel` — synchronous, request-response.

**Key features:**
- Retry logic (configurable `maxRetries`, default 3 with 1s sleep)
- Tool calling via `FunctionCallingConfig` (modes: ANY, AUTO, REQUIRED, NONE)
- Structured JSON output via `responseSchema` + `responseMimeType("application/json")`
- Multimodal support: images, audio, video, documents (extensive MIME type mapping)
- `ThinkingConfig` with configurable `thinkingBudget`
- Seed for reproducible outputs
- Safety settings (all Gemini HarmCategory values)
- `returnFixedContentOnMaxTokensReached` flag

### NexusGoogleGenAiStreamingChatModel

`implements StreamingChatModel` — chunked streaming response.

**Key features:**
- Partial text delivery via `handler.onPartialResponse(partialText)`
- Tool call accumulation across chunks via inner `StreamHandler` class
- Same auth, config, tool calling, and safety features as ChatModel
- No retry (streaming by nature)

### Model Factories

For per-request model configuration (different model, temperature, etc.):

**NexusVertexAiChatModelFactory:**
```java
@Service
public class MyService {
    private final NexusVertexAiChatModelFactory modelFactory;

    public String ask(String question) {
        ChatModel model = modelFactory.builder("gemini-1.5-pro")
            .temperature(0.8f)
            .topK(10)
            .topP(0.9f)
            .maxOutputTokens(1024)
            .build();
        return model.generate(question);
    }
}
```

**NexusGoogleGenAiModelFactory:**
```java
@Service
public class MyService {
    private final NexusGoogleGenAiModelFactory modelFactory;

    public String ask(String question) {
        // Simple — pre-configured auth, project, location, timeout
        ChatModel model = modelFactory.create("gemini-1.5-flash");
        return model.generate(question);
    }

    public String askAdvanced(String question) {
        ChatModel model = modelFactory.builder()
            .modelName("gemini-1.5-pro")
            .temperature(0.8)
            .maxOutputTokens(100)
            .build();
        return model.generate(question);
    }
}
```

### Comparison

| Feature | Default ChatModel Bean | VertexAI Factory | GenAI Factory |
|---|---|---|---|
| Per-request config | No | Yes | Yes |
| SDK | Google GenAI | Vertex AI | Google GenAI |
| Auth | Auto from YAML | Auto from YAML | Auto from YAML |
| Use case | Simple, single-model apps | Dynamic model switching (Vertex) | Dynamic model switching (GenAI) |

---

## Building Agents

Agents are the core building blocks. Define them with `AgentSpec`, create them with `AgentFactory`.

### AgentSpec — The Blueprint

`AgentSpec` is a builder-pattern configuration record that defines everything about an agent's behavior.

```java
AgentSpec spec = AgentSpec.builder("myAgentName", "Agent description")
    .inputKeys(Map.of("sourceText", ClassInfo.of(String.class)))
    .output("result", ClassInfo.of(String.class))
    .tools(myTool1, myTool2)
    .chatMemory(myChatMemory)
    .contentRetriever(myRetriever)       // RAG
    .inputGuardrails(myGuardrail)
    .outputGuardrails(myOutputGuardrail)
    .async(true)
    .build();
```

**Full field reference:**

| Category | Fields |
|---|---|
| **Core Identity** | `agentName` (DB prompt lookup key), `description`, `inputKeys`, `output` (key + type) |
| **Model Override** | `chatModel`, `streamingChatModel`, `returnFixedContentOnMaxTokensReached` |
| **Tools** | `tools` (List), `toolProvider`, `hallucinatedToolNameStrategy`, `maxSequentialToolExecutions` |
| **Memory** | `chatMemory`, `chatMemoryProvider`, `summarizedContextAgents` |
| **RAG** | `contentRetriever`, `retrievalAugmentor` |
| **Guardrails** | `inputGuardrails` (List), `outputGuardrails` (List) |
| **Execution** | `listener` (AgentListener), `async` (Boolean) |

### AgentFactory — The Builder

`AgentFactory` builds langchain4j agents from `AgentSpec` definitions. It centralizes all agent construction logic.

**Dependencies (auto-injected):**
- `NexusGoogleGenAiModelFactory` — model creation
- `NexusStoreProvider` — prompt retrieval from DB
- `AgentExecutionListener` — execution tracking

**`create(AgentSpec spec)` → `UntypedAgent`:**

1. Retrieves prompt config (system/user messages + model settings) from DB using `spec.getAgentName()`
2. Builds `ChatModel` with model params from DB config (modelName, temperature, topK, topP, maxOutputTokens, thinkingBudget, seed, responseSchema)
3. Builds agent with: chatModel, name, description, systemMessage, userMessage, inputKeys, returnType, outputKey
4. Conditionally wires: streaming model, tools, tool provider, chat memory, summarize context, content retriever (RAG), retrieval augmentor, input/output guardrails, hallucinated tool name strategy, listener, async flag

**`createAll(AgentSpec... specs)` → `UntypedAgent[]`:**
Convenience method — calls `create()` for each spec.

### Prompt Management

Agent prompts are stored in database and retrieved at runtime by agent name. This enables:
- **Prompt versioning** — update prompts without code changes or redeployment
- **Centralized management** — all prompts in one place
- **Per-agent model configuration** — each agent can use different model params (stored alongside prompt)

---

## Workflow Engine

`WorkflowEngine` is the **entry point** to the framework. It receives a request, builds the agent workflow graph, executes it with checkpoint persistence, and returns results.

### How To Use

Extend `WorkflowEngine` and implement `buildGraph()`:

```java
@Component
public class MyKycWorkflowEngine extends WorkflowEngine {

    @Override
    protected StateGraph<AgentState> buildGraph(
            AgentFactory agentFactory,
            WorkflowRequest request) {

        // Define agent specs
        var extractorSpec = AgentSpec.builder("extractor", "Extracts data from documents")
            .inputKeys(Map.of("document", ClassInfo.of(String.class)))
            .output("extractedData", ClassInfo.of(Map.class))
            .build();

        var validatorSpec = AgentSpec.builder("validator", "Validates extracted data")
            .inputKeys(Map.of("extractedData", ClassInfo.of(Map.class)))
            .output("validationResult", ClassInfo.of(String.class))
            .build();

        // Create and compose agents
        var extractor = agentFactory.create(extractorSpec);
        var validator = agentFactory.create(validatorSpec);
        var workflow = agentFactory.sequence("finalResult", extractor, validator);

        return buildStateGraph(workflow);
    }
}
```

### Dependencies (auto-injected)

| Dependency | Purpose |
|---|---|
| `AgentFactory` | Creates and composes agents |
| `OracleCheckpointSaver` | Checkpoint persistence for recovery |
| `NexusWorkflowStore` | Workflow metadata and state persistence |
| `WorkflowInitializer` | Initializes workflow context, creates execution records |
| `WorkflowResultProcessor` | Processes and persists final workflow results |

### Execution Flow

```
REST Controller / Service
    │
    ▼
WorkflowEngine.execute(WorkflowRequest)
    │
    ├── 1. workflowStore.create/resume(workflowId)
    ├── 2. workflowStore.updateStatus(IN_PROGRESS)
    │
    ├── 3. buildGraph(agentFactory, request)    ← YOUR CODE
    │       ├── agentFactory.create(specs...)
    │       ├── agentFactory.sequence/loop/parallel(...)
    │       └── returns StateGraph
    │
    ├── 4. Attach OracleCheckpointSaver to graph
    ├── 5. Set RunnableConfig(thread_id = workflowId)
    │
    ├── 6. graph.execute(inputState)
    │       ├── Agent 1 → Listener logs I/O → Langfuse traces LLM → Checkpoint saved
    │       ├── Agent 2 → ...
    │       └── Agent N → ...
    │
    ├── 7. Extract output from final state
    ├── 8. workflowStore.updateStatus(COMPLETED)
    │
    └── 9. Return WorkflowResponse
```

### Resume from Checkpoint

```java
// Resume from last successful checkpoint on failure
WorkflowResponse response = workflowEngine.resume(workflowId);
```

The engine loads the last checkpoint, rebuilds the graph, and continues execution from where it left off.

### WorkflowRequest / WorkflowResponse

**Request:**
```java
WorkflowRequest request = new WorkflowRequest();
request.setWorkflowId("optional-id");    // auto-generated if null
request.setPartyId("PARTY-123");
request.setWorkflowType("KYC_REVIEW");
request.setInput(Map.of("document", documentText));
request.setMetadata(Map.of("source", "batch-job"));
```

**Response:**
```java
WorkflowResponse response = engine.execute(request);
response.getWorkflowId();           // workflow identifier
response.getStatus();               // COMPLETED or FAILED
response.getOutput();               // final agent output map
response.getMetadata();             // metadata
response.getExecutionHistory();     // all agent I/O records
```

---

## Agent Composition Patterns

`AgentFactory` provides 4 composition patterns for building complex multi-agent workflows.

### 1. Sequence

Agents execute in order. Output of each agent feeds as input to the next.

```java
UntypedAgent pipeline = agentFactory.sequence("finalOutput",
    extractorAgent,
    validatorAgent,
    formatterAgent
);
```

**Flexible overload** — accepts mixed `AgentSpec`, `UntypedAgent`, and plain Objects:
```java
UntypedAgent pipeline = agentFactory.sequence("output",
    extractorSpec,        // AgentSpec → auto-created
    validatorAgent,       // UntypedAgent → used as-is
    customProcessor       // Object → treated as one-off agent
);
```

### 2. Loop

Iterative workflows with exit conditions.

```java
// Loop until score >= 0.8, max 3 iterations
UntypedAgent reviewCycle = agentFactory.loop(
    3,                                                    // max iterations
    scope -> scope.resultState("score", 0.0) >= 0.8,     // exit condition
    criticSpec,
    validatorSpec
);
```

**Variants:**
- `loop()` — exit condition checked at start of each iteration (while)
- `loopUntil()` — same as loop, pre-condition check
- `loopDoUntil()` — exit condition checked at end of each iteration (do-while)

### 3. Parallel

Agents execute concurrently. Results from all agents are collected.

```java
UntypedAgent concurrent = agentFactory.parallel(
    sentimentAgent,
    entityExtractionAgent,
    classificationAgent
);
```

### 4. Conditional

Routes execution to different agents based on a condition.

```java
var router = agentFactory.conditional(DocumentRouter.class);
```

### Combining Patterns

Patterns compose naturally:

```java
// A pipeline where step 2 is a parallel fan-out and step 3 is an iterative review
var step1 = agentFactory.create(preprocessSpec);
var step2 = agentFactory.parallel(analysisA, analysisB, analysisC);
var step3 = agentFactory.loop(3, exitCondition, reviewSpec, fixSpec);
var step4 = agentFactory.create(summarySpec);

var fullWorkflow = agentFactory.sequence("finalResult", step1, step2, step3, step4);
```

---

## State Persistence & Checkpoints

### OracleCheckpointSaver

`implements BaseCheckpointSaver` from LangGraph4j.

Persists workflow checkpoints to Oracle DB via JDBC. Auto-configured — no setup required.

| Method | Description |
|---|---|
| `get(config)` | Retrieve latest checkpoint by `thread_id` |
| `put(config, checkpoint)` | Save/upsert checkpoint keyed by `thread_id` + `checkpoint_id` |
| `list(config)` | List all checkpoints for a thread, ordered by creation time |
| `getTuple(config)` | Get checkpoint with full metadata and parent reference |

**Database schema:**
| Column | Type | Description |
|---|---|---|
| `thread_id` | VARCHAR | Workflow/conversation thread identifier |
| `checkpoint_id` | VARCHAR | Unique checkpoint identifier |
| `parent_checkpoint_id` | VARCHAR | Links to previous checkpoint for history |
| `checkpoint_data` | CLOB/BLOB | Serialized checkpoint state |
| `metadata` | CLOB | Additional metadata |
| `created_at` | TIMESTAMP | Creation timestamp |

**How it works:**
- Each workflow gets a `thread_id`
- Checkpoints saved after each agent transition
- Parent linking enables walking back through state history
- On failure, `resume()` loads last checkpoint and continues

---

## Custom Stores

All database access is handled by framework stores. App code never writes SQL.

### PromptStore / NexusAiStoreRepository

Retrieves prompt configurations from database. Used by `AgentFactory` to get system/user prompts and model parameters per agent.

**PromptConfig contains:**
- `systemInstruction` — system prompt text
- `promptText` — user prompt template
- `modelName`, `temperature`, `topK`, `topP`, `maxOutputTokens`
- `thinkingBudget`, `seed`
- `responseSchema`
- `promptVersion`

### NexusAgentExecutionStore

Persists agent execution records: agent name, input (JSON), output (JSON), start/end timestamps, status, error message.

**Table: `KYC_AI_AGENT_EXECUTION`**

### WorkflowStore

Tracks workflow lifecycle and metadata.

**Table: `KYC_AI_WORKFLOW`**

| Status | Description |
|---|---|
| `PENDING` | Workflow created, not started |
| `IN_PROGRESS` | Agents executing |
| `COMPLETED` | All agents finished successfully |
| `FAILED` | Error occurred during execution |

### NexusStoreProvider

Facade wrapping all stores. Auto-injected into `AgentFactory` and listeners. Provides unified interface:
- `getPromptConfig(agentName)` → `PromptConfig`
- `saveAgentExecution(...)` → persists agent I/O
- `updateWorkflowStatus(...)` → updates lifecycle state
- `getWorkflowsByParty(partyId)` → lists workflows

---

## Observability & Listeners

Nexus-AI provides two auto-configured listeners. Zero code required — all agents and LLM calls are automatically instrumented.

### AgentExecutionListener

`implements AgentListener`

Captures input/output of **every agent execution** and persists to database.

| Hook | What It Captures |
|---|---|
| `onStart(agentExecution)` | Agent name, description, start timestamp |
| `onComplete(agentExecution)` | Agent name, result (JSON), end timestamp, status=SUCCESS |
| `onError(agentExecution)` | Agent name, error details, timestamp, status=ERROR |

Auto-attached to all agents created by `AgentFactory`.

### NexusObservabilityListener

`implements ChatModelListener`

Langfuse-based observability for **all LLM calls**.

| Hook | What It Captures |
|---|---|
| `onRequest(context)` | Creates Langfuse trace + generation span. Logs: model name, input messages. |
| `onResponse(context)` | Updates span with: output, token usage (prompt/completion/total), latency, finish reason. |
| `onError(context)` | Updates trace with error status, error message, error type. |

Auto-attached to `ChatModel` and `StreamingChatModel` beans.

### How They Work Together

```
Agent Execution
    ├── AgentExecutionListener.onStart()     → DB: logs start
    │       │
    │       ▼
    │   ChatModel.generate()
    │       ├── NexusObservabilityListener.onRequest()  → Langfuse: trace start
    │       │       │
    │       │       ▼
    │       │   Google Gemini API call
    │       │       │
    │       │       ▼
    │       └── NexusObservabilityListener.onResponse() → Langfuse: tokens, latency
    │       │
    │       ▼
    └── AgentExecutionListener.onComplete()  → DB: logs input + output
```

---

## Spring Boot Auto-Configuration

Everything is driven by `clm.nexus.ai.enabled=true`.

### NexusAutoConfiguration

`@ConditionalOnProperty(name = "clm.nexus.ai.enabled", havingValue = "true")`

**Beans auto-created:**

| Bean | Type | Condition |
|---|---|---|
| `chatModel` | `NexusGoogleGenAiChatModel` | Google properties present |
| `streamingChatModel` | `NexusGoogleGenAiStreamingChatModel` | Google properties present |
| `NexusVertexAiChatModelFactory` | Model factory | Always |
| `NexusGoogleGenAiModelFactory` | Model factory | Always |
| `AgentFactory` | Agent builder | Always |
| `AgentExecutionListener` | DB persistence listener | Always |
| `NexusObservabilityListener` | Langfuse listener | Langfuse properties present |
| `NexusStoreProvider` | DB store facade | Always |
| `List<SafetySetting>` | Gemini safety settings | Always |

### NexusAiStateConfiguration

**Additional beans:**

| Bean | Type |
|---|---|
| `OracleCheckpointSaver` | Checkpoint persistence |
| `CheckpointSerializer` | JSON serialization for checkpoints |
| Thread pool executor | Async operations |

### Feature Detection

Features are auto-detected from property presence:
- Google properties present → ChatModel beans created
- Azure properties present → Azure AD auth enabled
- Langfuse properties present → Observability listener enabled
- DataSource configured → Checkpoint persistence enabled

---

## Safety Settings

Nexus-AI configures all Gemini safety categories by default via the `NexusAiSafetySettings` enum:

| Category | Description |
|---|---|
| `HARM_CATEGORY_HARASSMENT` | Harassment content |
| `HARM_CATEGORY_HATE_SPEECH` | Hate speech |
| `HARM_CATEGORY_SEXUALLY_EXPLICIT` | Sexually explicit content |
| `HARM_CATEGORY_DANGEROUS_CONTENT` | Dangerous content |
| `HARM_CATEGORY_CIVIC_INTEGRITY` | Civic integrity |

Safety settings are auto-injected into both ChatModel and StreamingChatModel beans.

---

## Configuration Reference

### Complete YAML Reference

```yaml
clm:
  nexus:
    ai:
      # Master switch
      enabled: true                    # true/false — enables/disables all nexus-ai beans

      # Proxy
      proxy: <proxy-url>              # Optional enterprise proxy

      # Azure AD Authentication
      azure:
        tenant-id: <tenant-id>
        client-id: <client-id>
        client-secret: <client-secret>
        scope: <scope>
        grant-type: client_credentials
        client_assertion_type: urn:ietf:params:oauth:client-assertion-type:jwt-bearer
        thumbprint: <cert-thumbprint>
        db_key_protect_key_name: <key-name>
        db_key_protect_key_passphrase: <passphrase>

      # Workload Identity Federation
      wif:
        wif_provider: <path-to-wif-provider.json>
        wif_keystore: <path-to-wif-keystore.json>

      # Google / Gemini
      google:
        project-id: <gcp-project-id>
        location: us-central1
        transport: GRPC
        chat-model-name: gemini-1.5-pro-preview-0409
        output-tokens: 2048
        temperature: 0.2
        top-k: 40
        top-p: 0.95
        credentials:
          project-number: <gcp-project-number>
          location: global
          workload-identity-pool-id: <pool-id>
          workload-identity-provider-id: <provider-id>
          service-account-email: <sa>@<project>.iam.gserviceaccount.com
          subject-token-type: urn:ietf:params:oauth:token-type:jwt
```

### Properties Classes

| Class | Prefix | Purpose |
|---|---|---|
| `NexusAiProperties` | `clm.nexus.ai` | Master switch, proxy |
| `GoogleProperties` | `clm.nexus.ai.google` | GCP project, model params |
| `AzureAdProperties` | `clm.nexus.ai.azure` | Azure AD token config |
| `KeystoreProperties` | — | Keystore paths |
| `WorkloadIdentityFederationProperties` | `clm.nexus.ai.wif` | WIF config |
| `LangfuseProperties` | — | Langfuse connection settings |

---

## Quick Reference

### Usage Patterns

| Pattern | When To Use | Code |
|---|---|---|
| **Basic** | Single model, global config | `@Autowired ChatModel chatModel` |
| **VertexAI Factory** | Per-request model config (Vertex) | `modelFactory.builder("model").temperature(0.8f).build()` |
| **GenAI Factory** | Per-request model config (GenAI SDK) | `modelFactory.create("gemini-1.5-flash")` |
| **Agent Workflow** | Multi-agent orchestration | Extend `WorkflowEngine`, implement `buildGraph()` |

### Package Structure

| Package | Contents |
|---|---|
| `com.db.clm.kyc.ai.nexus.agent` | `AgentFactory`, `AgentSpec` |
| `com.db.clm.kyc.ai.nexus.auth` | `AzureTokenService`, `CredentialManager`, `CredentialUtils` |
| `com.db.clm.kyc.ai.nexus.chat.model` | `NexusGoogleGenAiChatModel`, `NexusGoogleGenAiStreamingChatModel`, model factories |
| `com.db.clm.kyc.ai.nexus.checkpoint` | `OracleCheckpointSaver`, `CheckpointSerializer` |
| `com.db.clm.kyc.ai.nexus.config` | `NexusAutoConfiguration`, all properties classes |
| `com.db.clm.kyc.ai.nexus.engine` | `WorkflowEngine` |
| `com.db.clm.kyc.ai.nexus.listener` | `AgentExecutionListener`, `NexusObservabilityListener` |
| `com.db.clm.kyc.ai.nexus.store` | `PromptStore`, `NexusAgentExecutionStore`, `WorkflowStore`, `NexusStoreProvider` |
