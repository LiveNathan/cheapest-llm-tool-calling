# Chat Memory

Large language models (LLMs) are stateless, meaning they do not retain information about previous interactions. This can
be a limitation when you want to maintain context or state across multiple interactions. To address this, Spring AI
provides chat memory features that allow you to store and retrieve information across multiple interactions with the
LLM.

The `ChatMemory` abstraction allows you to implement various types of memory to support different use cases. The
underlying storage of the messages is handled by the `ChatMemoryRepository`, whose sole responsibility is to store and
retrieve messages. It’s up to the `ChatMemory` implementation to decide which messages to keep and when to remove them.
Examples of strategies could include keeping the last N messages, keeping messages for a certain time period, or keeping
messages up to a certain token limit.

Before choosing a memory type, it’s essential to understand the difference between chat memory and chat history.

- **Chat Memory**. The information that a large-language model retains and uses to maintain contextual awareness
  throughout a conversation.
- **Chat History**. The entire conversation history, including all messages exchanged between the user and the model.
  The `ChatMemory` abstraction is designed to manage the *chat memory*. It allows you to store and retrieve messages
  that are relevant to the current conversation context. However, it is not the best fit for storing the *chat history*.
  If you need to maintain a complete record of all the messages exchanged, you should consider using a different
  approach, such as relying on Spring Data for efficient storage and retrieval of the complete chat history.

## <a href="about:blank#_quick_start"></a> Quick Start

Spring AI auto-configures a `ChatMemory` bean that you can use directly in your application. By default, it uses an
in-memory repository to store messages (`InMemoryChatMemoryRepository`) and a `MessageWindowChatMemory` implementation
to manage the conversation history. If a different repository is already configured (e.g., Cassandra, JDBC, or Neo4j),
Spring AI will use that instead.

```java
@Autowired
ChatMemory chatMemory;
```

The following sections will describe further the different memory types and repositories available in Spring AI.

## <a href="about:blank#_memory_types"></a> Memory Types

The `ChatMemory` abstraction allows you to implement various types of memory to suit different use cases. The choice of
memory type can significantly impact the performance and behavior of your application. This section describes the
built-in memory types provided by Spring AI and their characteristics.

### <a href="about:blank#_message_window_chat_memory"></a> Message Window Chat Memory

`MessageWindowChatMemory` maintains a window of messages up to a specified maximum size. When the number of messages
exceeds the maximum, older messages are removed while preserving system messages. The default window size is 20
messages.

```java
MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
    .maxMessages(10)
    .build();
```

This is the default message type used by Spring AI to auto-configure a `ChatMemory` bean.

## <a href="about:blank#_memory_storage"></a> Memory Storage

Spring AI offers the `ChatMemoryRepository` abstraction for storing chat memory. This section describes the built-in
repositories provided by Spring AI and how to use them, but you can also implement your own repository if needed.

### <a href="about:blank#_in_memory_repository"></a> In-Memory Repository

`InMemoryChatMemoryRepository` stores messages in memory using a `ConcurrentHashMap`.

By default, if no other repository is already configured, Spring AI auto-configures a `ChatMemoryRepository` bean of
type `InMemoryChatMemoryRepository` that you can use directly in your application.

```java
@Autowired
ChatMemoryRepository chatMemoryRepository;
```

If you’d rather create the `InMemoryChatMemoryRepository` manually, you can do so as follows:

```java
ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
```

### <a href="about:blank#_jdbcchatmemoryrepository"></a> JdbcChatMemoryRepository

`JdbcChatMemoryRepository` is a built-in implementation that uses JDBC to store messages in a relational database. It
supports multiple databases out-of-the-box and is suitable for applications that require persistent storage of chat
memory.

First, add the following dependency to your project:

- Maven
- Gradle

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

```groovy
dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc'
}
```

Spring AI provides auto-configuration for the `JdbcChatMemoryRepository`, that you can use directly in your application.

```java
@Autowired
JdbcChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

If you’d rather create the `JdbcChatMemoryRepository` manually, you can do so by providing a `JdbcTemplate` instance and
a `JdbcChatMemoryRepositoryDialect`:

```java
ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
    .jdbcTemplate(jdbcTemplate)
    .dialect(new PostgresChatMemoryRepositoryDialect())
    .build();

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

#### <a href="about:blank#_supported_databases_and_dialect_abstraction"></a> Supported Databases and Dialect Abstraction

Spring AI supports multiple relational databases via a dialect abstraction. The following databases are supported
out-of-the-box:

- PostgreSQL
- MySQL / MariaDB
- SQL Server
- HSQLDB
  The correct dialect can be auto-detected from the JDBC URL when using
  `JdbcChatMemoryRepositoryDialect.from(DataSource)`. You can extend support for other databases by implementing the
  `JdbcChatMemoryRepositoryDialect` interface.

#### <a href="about:blank#_configuration_properties"></a> Configuration Properties

| Property <!-- col-0 --> | Description <!-- col-1 --> | Default Value <!-- col-2 --> |
| `spring.ai.chat.memory.repository.jdbc.initialize-schema` <!-- col-0 --> | Controls when to initialize the schema.
Values: `embedded` (default), `always`, `never`. <!-- col-1 --> | `embedded` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.jdbc.schema` <!-- col-0 --> | Location of the schema script to use for
initialization. Supports `classpath:` URLs and platform placeholders. <!-- col-1 --> |
`classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-@@platform@@.sql` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.jdbc.platform` <!-- col-0 --> | Platform to use in initialization scripts if the
@@platform@@ placeholder is used. <!-- col-1 --> | *auto-detected* <!-- col-2 --> |

#### <a href="about:blank#_schema_initialization"></a> Schema Initialization

The auto-configuration will automatically create the `SPRING_AI_CHAT_MEMORY` table on startup, using a vendor-specific
SQL script for your database. By default, schema initialization runs only for embedded databases (H2, HSQL, Derby,
etc.).

You can control schema initialization using the `spring.ai.chat.memory.repository.jdbc.initialize-schema` property:

```properties
spring.ai.chat.memory.repository.jdbc.initialize-schema=embedded # Only for embedded DBs (default)
spring.ai.chat.memory.repository.jdbc.initialize-schema=always   # Always initialize
spring.ai.chat.memory.repository.jdbc.initialize-schema=never    # Never initialize (useful with Flyway/Liquibase)
```

To override the schema script location, use:

```properties
spring.ai.chat.memory.repository.jdbc.schema=classpath:/custom/path/schema-mysql.sql
```

#### <a href="about:blank#_extending_dialects"></a> Extending Dialects

To add support for a new database, implement the `JdbcChatMemoryRepositoryDialect` interface and provide SQL for
selecting, inserting, and deleting messages. You can then pass your custom dialect to the repository builder.

```java
ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
    .jdbcTemplate(jdbcTemplate)
    .dialect(new MyCustomDbDialect())
    .build();
```

### <a href="about:blank#_cassandrachatmemoryrepository"></a> CassandraChatMemoryRepository

`CassandraChatMemoryRepository` uses Apache Cassandra to store messages. It is suitable for applications that require
persistent storage of chat memory, especially for availability, durability, scale, and when taking advantage of
time-to-live (TTL) feature.

`CassandraChatMemoryRepository` has a time-series schema, keeping record of all past chat windows, valuable for
governance and auditing. Setting time-to-live to some value, for example three years, is recommended.

To use `CassandraChatMemoryRepository` first, add the dependency to your project:

- Maven
- Gradle

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-cassandra</artifactId>
</dependency>
```

```groovy
dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-cassandra'
}
```

Spring AI provides auto-configuration for the `CassandraChatMemoryRepository` that you can use directly in your
application.

```java
@Autowired
CassandraChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

If you’d rather create the `CassandraChatMemoryRepository` manually, you can do so by providing a
`CassandraChatMemoryRepositoryConfig` instance:

```java
ChatMemoryRepository chatMemoryRepository = CassandraChatMemoryRepository
    .create(CassandraChatMemoryRepositoryConfig.builder().withCqlSession(cqlSession));

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

#### <a href="about:blank#_configuration_properties_2"></a> Configuration Properties

| Property <!-- col-0 --> | Description <!-- col-1 --> | Default Value <!-- col-2 --> |
| `spring.cassandra.contactPoints` <!-- col-0 --> | Host(s) to initiate cluster discovery <!-- col-1 --> |
`127.0.0.1` <!-- col-2 --> |
| `spring.cassandra.port` <!-- col-0 --> | Cassandra native protocol port to connect to <!-- col-1 --> |
`9042` <!-- col-2 --> |
| `spring.cassandra.localDatacenter` <!-- col-0 --> | Cassandra datacenter to connect to <!-- col-1 --> |
`datacenter1` <!-- col-2 --> |
| `spring.ai.chat.memory.cassandra.time-to-live` <!-- col-0 --> | Time to live (TTL) for messages written in
Cassandra <!-- col-1 --> |  <!-- col-2 --> |
| `spring.ai.chat.memory.cassandra.keyspace` <!-- col-0 --> | Cassandra keyspace <!-- col-1 --> |
`springframework` <!-- col-2 --> |
| `spring.ai.chat.memory.cassandra.messages-column` <!-- col-0 --> | Cassandra column name for messages <!-- col-1 --> |
`springframework` <!-- col-2 --> |
| `spring.ai.chat.memory.cassandra.table` <!-- col-0 --> | Cassandra table <!-- col-1 --> |
`ai_chat_memory` <!-- col-2 --> |
| `spring.ai.chat.memory.cassandra.initialize-schema` <!-- col-0 --> | Whether to initialize the schema on
startup. <!-- col-1 --> | `true` <!-- col-2 --> |

#### <a href="about:blank#_schema_initialization_2"></a> Schema Initialization

The auto-configuration will automatically create the `ai_chat_memory` table.

You can disable the schema initialization by setting the property
`spring.ai.chat.memory.repository.cassandra.initialize-schema` to `false`.

### <a href="about:blank#_neo4j_chatmemoryrepository"></a> Neo4j ChatMemoryRepository

`Neo4jChatMemoryRepository` is a built-in implementation that uses Neo4j to store chat messages as nodes and
relationships in a property graph database. It is suitable for applications that want to leverage Neo4j’s graph
capabilities for chat memory persistence.

First, add the following dependency to your project:

- Maven
- Gradle

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-neo4j</artifactId>
</dependency>
```

```groovy
dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-neo4j'
}
```

Spring AI provides auto-configuration for the `Neo4jChatMemoryRepository`, which you can use directly in your
application.

```java
@Autowired
Neo4jChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

If you’d rather create the `Neo4jChatMemoryRepository` manually, you can do so by providing a Neo4j `Driver` instance:

```java
ChatMemoryRepository chatMemoryRepository = Neo4jChatMemoryRepository.builder()
    .driver(driver)
    .build();

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

#### <a href="about:blank#_configuration_properties_3"></a> Configuration Properties

| Property <!-- col-0 --> | Description <!-- col-1 --> | Default Value <!-- col-2 --> |
| `spring.ai.chat.memory.repository.neo4j.sessionLabel` <!-- col-0 --> | The label for the nodes that store conversation
sessions <!-- col-1 --> | `Session` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.neo4j.messageLabel` <!-- col-0 --> | The label for the nodes that store
messages <!-- col-1 --> | `Message` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.neo4j.toolCallLabel` <!-- col-0 --> | The label for nodes that store tool calls (
e.g. in Assistant Messages) <!-- col-1 --> | `ToolCall` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.neo4j.metadataLabel` <!-- col-0 --> | The label for nodes that store message
metadata <!-- col-1 --> | `Metadata` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.neo4j.toolResponseLabel` <!-- col-0 --> | The label for the nodes that store tool
responses <!-- col-1 --> | `ToolResponse` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.neo4j.mediaLabel` <!-- col-0 --> | The label for the nodes that store media
associated with a message <!-- col-1 --> | `Media` <!-- col-2 --> |

#### <a href="about:blank#_index_initialization"></a> Index Initialization

The Neo4j repository will automatically ensure that indexes are created for conversation IDs and message indices to
optimize performance. If you use custom labels, indexes will be created for those labels as well. No schema
initialization is required, but you should ensure your Neo4j instance is accessible to your application.

### <a href="about:blank#_cosmosdbchatmemoryrepository"></a> CosmosDBChatMemoryRepository

`CosmosDBChatMemoryRepository` is a built-in implementation that uses Azure Cosmos DB NoSQL API to store messages. It is
suitable for applications that require a globally distributed, highly scalable document database for chat memory
persistence. The repository uses the conversation ID as the partition key to ensure efficient data distribution and fast
retrieval.

First, add the following dependency to your project:

- Maven
- Gradle

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-cosmos-db</artifactId>
</dependency>
```

```groovy
dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-cosmos-db'
}
```

Spring AI provides auto-configuration for the `CosmosDBChatMemoryRepository`, which you can use directly in your
application.

```java
@Autowired
CosmosDBChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

If you’d rather create the `CosmosDBChatMemoryRepository` manually, you can do so by providing a
`CosmosDBChatMemoryRepositoryConfig` instance:

```java
ChatMemoryRepository chatMemoryRepository = CosmosDBChatMemoryRepository
    .create(CosmosDBChatMemoryRepositoryConfig.builder()
        .withCosmosClient(cosmosAsyncClient)
        .withDatabaseName("chat-memory-db")
        .withContainerName("conversations")
        .build());

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(10)
    .build();
```

#### <a href="about:blank#_configuration_properties_4"></a> Configuration Properties

| Property <!-- col-0 --> | Description <!-- col-1 --> | Default Value <!-- col-2 --> |
| `spring.ai.chat.memory.repository.cosmosdb.endpoint` <!-- col-0 --> | Azure Cosmos DB endpoint URI. Required for
auto-configuration. <!-- col-1 --> |  <!-- col-2 --> |
| `spring.ai.chat.memory.repository.cosmosdb.key` <!-- col-0 --> | Azure Cosmos DB primary or secondary key. If not
provided, Azure Identity authentication will be used. <!-- col-1 --> |  <!-- col-2 --> |
| `spring.ai.chat.memory.repository.cosmosdb.connection-mode` <!-- col-0 --> | Connection mode for Cosmos DB client (
`direct` or `gateway`). <!-- col-1 --> | `gateway` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.cosmosdb.database-name` <!-- col-0 --> | Name of the Cosmos DB
database. <!-- col-1 --> | `SpringAIChatMemory` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.cosmosdb.container-name` <!-- col-0 --> | Name of the Cosmos DB
container. <!-- col-1 --> | `ChatMemory` <!-- col-2 --> |
| `spring.ai.chat.memory.repository.cosmosdb.partition-key-path` <!-- col-0 --> | Partition key path for the
container. <!-- col-1 --> | `/conversationId` <!-- col-2 --> |

#### <a href="about:blank#_authentication"></a> Authentication

The Cosmos DB Chat Memory Repository supports two authentication methods:

1. **Key-based authentication**: Provide the `spring.ai.chat.memory.repository.cosmosdb.key` property with your Cosmos
   DB primary or secondary key.
2. **Azure Identity authentication**: When no key is provided, the repository uses Azure Identity (
   `DefaultAzureCredential`) to authenticate with managed identity, service principal, or other Azure credential
   sources.

#### <a href="about:blank#_schema_initialization_3"></a> Schema Initialization

The auto-configuration will automatically create the specified database and container if they don’t exist. The container
is configured with the conversation ID as the partition key (`/conversationId`) to ensure optimal performance for chat
memory operations. No manual schema setup is required.

You can customize the database and container names using the configuration properties mentioned above.

## <a href="about:blank#_memory_in_chat_client"></a> Memory in Chat Client

When using the ChatClient API, you can provide a `ChatMemory` implementation to maintain conversation context across
multiple interactions.

Spring AI provides a few built-in Advisors that you can use to configure the memory behavior of the `ChatClient`, based
on your needs.

|  <!-- col-0 --> | Currently, the intermediate messages exchanged with a large-language model when performing tool
calls are not stored in the memory. This is a limitation of the current implementation and will be addressed in future
releases. If you need to store these messages, refer to the instructions for
the [User Controlled Tool Execution](tools.html#_user_controlled_tool_execution). <!-- col-1 --> |

- `MessageChatMemoryAdvisor`. This advisor manages the conversation memory using the provided `ChatMemory`
  implementation. On each interaction, it retrieves the conversation history from the memory and includes it in the
  prompt as a collection of messages.
- `PromptChatMemoryAdvisor`. This advisor manages the conversation memory using the provided `ChatMemory`
  implementation. On each interaction, it retrieves the conversation history from the memory and appends it to the
  system prompt as plain text.
- `VectorStoreChatMemoryAdvisor`. This advisor manages the conversation memory using the provided `VectorStore`
  implementation. On each interaction, it retrieves the conversation history from the vector store and appends it to the
  system message as plain text.
  For example, if you want to use `MessageWindowChatMemory` with the `MessageChatMemoryAdvisor`, you can configure it as
  follows:

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();
```

When performing a call to the `ChatClient`, the memory will be automatically managed by the `MessageChatMemoryAdvisor`.
The conversation history will be retrieved from the memory based on the specified conversation ID:

```java
String conversationId = "007";

chatClient.prompt()
    .user("Do I have license to code?")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
    .call()
    .content();
```

### <a href="about:blank#_promptchatmemoryadvisor"></a> PromptChatMemoryAdvisor

#### <a href="about:blank#_custom_template"></a> Custom Template

The `PromptChatMemoryAdvisor` uses a default template to augment the system message with the retrieved conversation
memory. You can customize this behavior by providing your own `PromptTemplate` object via the `.promptTemplate()`
builder method.

|  <!-- col-0 --> | The `PromptTemplate` provided here customizes how the advisor merges retrieved memory with the
system message. This is distinct from configuring a `TemplateRenderer` on the `ChatClient` itself (using
`.templateRenderer()`), which affects the rendering of the initial user/system prompt content **before** the advisor
runs. See [ChatClient Prompt Templates](chatclient.html#_prompt_templates) for more details on client-level template
rendering. <!-- col-1 --> |
The custom `PromptTemplate` can use any `TemplateRenderer` implementation (by default, it uses `StPromptTemplate` based
on the [StringTemplate](https://www.stringtemplate.org/) engine). The important requirement is that the template must
contain the following two placeholders:

- an `instructions` placeholder to receive the original system message.
- a `memory` placeholder to receive the retrieved conversation memory.

### <a href="about:blank#_vectorstorechatmemoryadvisor"></a> VectorStoreChatMemoryAdvisor

#### <a href="about:blank#_custom_template_2"></a> Custom Template

The `VectorStoreChatMemoryAdvisor` uses a default template to augment the system message with the retrieved conversation
memory. You can customize this behavior by providing your own `PromptTemplate` object via the `.promptTemplate()`
builder method.

|  <!-- col-0 --> | The `PromptTemplate` provided here customizes how the advisor merges retrieved memory with the
system message. This is distinct from configuring a `TemplateRenderer` on the `ChatClient` itself (using
`.templateRenderer()`), which affects the rendering of the initial user/system prompt content **before** the advisor
runs. See [ChatClient Prompt Templates](chatclient.html#_prompt_templates) for more details on client-level template
rendering. <!-- col-1 --> |
The custom `PromptTemplate` can use any `TemplateRenderer` implementation (by default, it uses `StPromptTemplate` based
on the [StringTemplate](https://www.stringtemplate.org/) engine). The important requirement is that the template must
contain the following two placeholders:

- an `instructions` placeholder to receive the original system message.
- a `long_term_memory` placeholder to receive the retrieved conversation memory.

## <a href="about:blank#_memory_in_chat_model"></a> Memory in Chat Model

If you’re working directly with a `ChatModel` instead of a `ChatClient`, you can manage the memory explicitly:

```java
// Create a memory instance
ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
String conversationId = "007";

// First interaction
UserMessage userMessage1 = new UserMessage("My name is James Bond");
chatMemory.add(conversationId, userMessage1);
ChatResponse response1 = chatModel.call(new Prompt(chatMemory.get(conversationId)));
chatMemory.add(conversationId, response1.getResult().getOutput());

// Second interaction
UserMessage userMessage2 = new UserMessage("What is my name?");
chatMemory.add(conversationId, userMessage2);
ChatResponse response2 = chatModel.call(new Prompt(chatMemory.get(conversationId)));
chatMemory.add(conversationId, response2.getResult().getOutput());

// The response will contain "James Bond"
```

<!-- <nav> -->
[Mistral AI](moderation/mistral-ai-moderation.html) [Tool Calling](tools.html)
<!-- </nav> -->