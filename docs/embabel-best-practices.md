The following document outlines the best practices for building enterprise AI agents using the Embabel framework on the JVM, synthesized from the Gen AI Grows Up Enterprise JVM Agents With Embabel  by  Rod Johnson youtube video at https://youtu.be/_Y-srK-Ad4c?si=sHctq8qFy69GIQgp

## Embabel Best Practices for Enterprise AI Agents

Embabel, built on Spring and the JVM, is designed to bring explainable, type-safe, and robust agentic behavior to existing enterprise Java applications.

### 1. Architecture and Planning

| Best Practice | Implementation Details | Source |
| :--- | :--- | :--- |
| **Prioritize the JVM** | Build agents on the JVM to ensure seamless access to enterprise assets, including your existing domain model, messaging services, and database persistence. Agents are only as valuable as what they can access. | Transcript |
| **Focus on Orchestration** | Recognize that portably calling LLMs (like Spring AI) is necessary but not sufficient. Use Embabel as a "true agent framework" focused on **orchestration** to wire a series of steps (actions) together to solve real problems. | Transcript |
| **Use Deterministic Planning (GOAP)** | Rely on Embabel's Goal-Oriented Action Planning (GOAP) algorithm, which is a non-LLM, pathfinding algorithm, to dynamically determine the sequence of actions. This planning is deterministic, making the agent's behavior explainable and repeatable. | Transcript |
| **Enable Agent Scanning** | Use the `@EnableAgents` annotation on your main Spring Boot class to enable class path scanning for agents and actions. | Transcript |

### 2. Type Safety and Domain Modeling

| Best Practice | Implementation Details | Source |
| :--- | :--- | :--- |
| **Design Domain Objects First** | Start by creating or identifying your domain model (e.g., `Story`, `ReviewedStory`, `TravelBrief`). Agents are significantly easier to write once the domain objects are established. | Transcript |
| **Enforce Strong Typing** | Avoid relying on string keys for data flow. Embabel uses data typing (Java/Kotlin classes/records) to determine action preconditions and expected post-conditions, ensuring a highly type-safe workflow. | Transcript |
| **Demand Structured Output** | When interacting with an LLM, use the fluent API's `createObject(String prompt, Class<T> clazz)` method. This instructs the LLM to return data in a structured format (e.g., a `Story` record), which the framework achieves by sending a JSON schema to the model. | Transcript |
| **Annotate Actions and Goals** | Use `@Action` on methods that represent steps in a flow, and use `@AchievesGoal` to conveniently mark an action that fulfills a desired goal (e.g., `reviewStory` achieving the "reviewed story" goal). | Code |

### 3. Action Design and Tooling

| Best Practice | Implementation Details | Source |
| :--- | :--- | :--- |
| **Break Up Flows** | Decompose complex tasks into a series of smaller, focused actions. This improves reliability, reduces cost (allowing the use of cheaper models for simple steps), and facilitates better mixing of LLM calls with code interactions. | Transcript |
| **Prioritize Code over LLMs** | Any action that can be done purely in code (e.g., an API call using `RestClient` or mathematical computation) should be. Code execution is faster, cheaper, more reliable, and more environmentally friendly than an LLM call. | Transcript |
| **Use Tools Safely (Zero Data Leakage)** | Use the `withToolObject` pattern to expose existing business functionality. For instance, look up a customer entity in code, then expose that entity and its `@Tool` methods to the LLM. This allows the LLM to invoke methods on the customer object without the entity's sensitive data ever leaving the JVM. | Transcript |
| **Abstract Tools with Groups** | Use tool groups (e.g., `web search` or `map tools`) instead of specific tool implementations (e.g., Google Search). This indirection allows you to easily switch underlying services (e.g., to a high-tier service) or providers based on environment or user access levels. | Transcript |

### 4. Prompt Management

| Best Practice | Implementation Details | Source |
| :--- | :--- | :--- |
| **Use Code for Simple Prompts** | For small, focused prompts used in multi-step agents, building them in Java/Kotlin code is recommended. This allows you to easily access all objects in scope and minimizes reliance on prompt engineering. | Transcript |
| **Use Prompt Contributors** | Structure and compose prompts from reusable parts using the `Persona` or `RoleGoalBackstory` implementations of the `PromptContributor` interface. | Transcript |
| **Externalize Complicated Prompts (Jinja)** | For **big, complicated prompts**, externalize them into template files, such as those using the Jinja template language. Jinja2 is a popular templating engine that allows for dynamic prompt generation using placeholders, loops, and conditional logic, keeping LLM instructions separate from application logic. Embabel supports this externalization via methods like `withTemplate`. | Transcript, Search |
| **Ensure Contextual Awareness** | The framework automatically inserts system date and model knowledge cutoff date into prompts. This is crucial for enabling the LLM to use tools (like web search) correctly and avoid outdated information. | Transcript |

### 5. Operational Excellence

| Best Practice | Implementation Details | Source |
| :--- | :--- | :--- |
| **Track and Expose Cost** | Track token usage (input and output) and compute the total price across all models used in a multi-step flow. This usage and pricing data is exposed on the running agent process, allowing you to write **cost-aware agents** that can suggest cost-saving measures. | Transcript |
| **Design for Testability** | The design of Embabel makes unit testing easy. Since actions are POJOs (Plain Old Java Objects) and LLM interaction is abstracted behind the `AI` interface (accessible via `OperationContext`), it is very easy to mock the LLM calls and unit test action methods. | Transcript |
| **Adopt Event-Based Logging** | All info-level logging in Embabel is done in response to events. This provides a clean audit trail and allows application code to react to specific events in the agent workflow. | Transcript |
| **Configure LLM Parameters** | Use the fluent API to specify LLM options. For example, use `withTemperature(.7)` for high creativity (as seen in the `craftStory` action) or lower temperatures for deterministic tasks. You can also specify a target model or max tokens. | Code |