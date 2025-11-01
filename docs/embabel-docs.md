# Embabel Agent Framework User Guide
![315px Meister der Weltenchronik 001](images/common/315px-Meister_der_Weltenchronik_001.png)

Embabel Agent Release: 0.1.3-SNAPSHOT

© 2024-2025 Embabel

[](#overview__overview)1\. Overview
-----------------------------------

Agentic AI is the use of large language (and other multi-modal) models not just to generate text, but to act as reasoning, goal-driven agents that can plan, call tools, and adapt their actions to deliver outcomes.

The JVM is a compelling platform for this because its strong type safety provides guardrails for integrating LLM-driven behaviors with real systems. Because so many production applications already run on the JVM it is the natural place to embed AI.

While Agentic AI has been hyped, much of it has lived in academic demos with little practical value; by integrating directly into legacy and enterprise JVM applications, we can unlock AI capabilities without rewriting core systems or tearing down a factory to install new machinery.

### [](#glossary)1.1. Glossary

Before we begin, in this glossary we’ll explain some terms that may be new if you’re taking your first steps as an applied AI software developer. It is assumed that you already know what a large language model (LLM) is from an end-user’s point of view.



Agent

An Agent in the Embabel framework is a self-contained component that bundles together domain logic, AI capabilities, and tool usage to achieve a specific goal on behalf of the user.

Inside, it exposes multiple `@Action` methods, each representing discrete steps the agent can take. Actions depend on typically structured (sometimes natural language) input. The input is used to perform tasks on behalf of the user - executing domain code, calling AI models or even calling other agents as a sub-process.

When an AI model is called it may be given access to tools that expand its capabilities in order to achieve a goal. The output is a new type, representing a transformation of the input, however during execution one or more side-effects can occur. An example of side effects might be new records stored in a database, orders placed on an e-commerce site and so on.

Domain Integrated Context Engineering (DICE)

Enhances context engineering by grounding both LLM inputs and outputs in typed domain objects. Instead of untyped prompts, context is structured with business-aware models that provide precision, testability, and seamless integration with existing systems. DICE transforms context into a re-usable, inspectable, and reliably manipulable artifact.

### [](#overview__agent-framework)1.2. Why do we need an Agent Framework?

Aren’t LLMs smart enough to solve our problems directly? Aren’t MCP tools all we need to allow them to solve complex problems?

But there are many reasons that a higher level orchestration technology is needed, especially for business applications. Here are some of the most important:

*   **Explainability**: Why were choices made in solving a problem?

*   **Discoverability**: How do we find the right tools at each point, and ensure that models aren’t confused in choosing between them?

*   **Ability to mix models**, so that we are not reliant on God models but can use local, cheaper, private models for many tasks

*   **Ability to inject guardrails** at any point in a flow

*   **Ability to manage flow execution** and introduce greater resilience

*   **Composability of flows at scale**. We’ll soon be seeing not just agents running on one system, but federations of agents.

*   **Safer integration with sensitive existing systems** such as databases, where it is dangerous to allow even the best LLM write access.


Agent frameworks break complex tasks into smaller, manageable components, offering greater control and predictability.

Agent frameworks offer "code agency" as well as "LLM agency." This division is well described in this [paper from NVIDIA Research](https://research.nvidia.com/labs/lpr/slm-agents/).

Further reading:

*   [Embabel: A new Agent Platform For the JVM](https://medium.com/@springrod/embabel-a-new-agent-platform-for-the-jvm-1c83402e0014)

*   [The Embabel Vision](https://medium.com/@springrod/the-embabel-vision-967654f13793)


### [](#overview__why-embabel)1.3. Embabel Differentiators

#### [](#sophisticated-planning)1.3.1. Sophisticated Planning

Goes beyond a finite state machine or sequential execution with nesting by introducing a true planning step, using a non-LLM AI algorithm. This enables the system to perform tasks it wasn’t programmed to do by combining known steps in a novel order, as well as make decisions about parallelization and other runtime behavior.

#### [](#superior-extensibility-and-reuse)1.3.2. Superior Extensibility and Reuse

Because of dynamic planning, adding more domain objects, actions, goals and conditions can extend the capability of the system, _without editing FSM definitions_ or existing code.

#### [](#strong-typing-and-object-orientation)1.3.3. Strong Typing and Object Orientation

Actions, goals and conditions are informed by a domain model, which can include behavior. Everything is strongly typed and prompts and manually authored code interact cleanly. No more magic maps. Enjoy full refactoring support.

#### [](#platform-abstraction)1.3.4. Platform Abstraction

Clean separation between programming model and platform internals allows running locally while potentially offering higher QoS in production without changing application code.

#### [](#llm-mixing)1.3.5. LLM Mixing

It is easy to build applications that mix LLMs, ensuring the most cost-effective yet capable solution. This enables the system to leverage the strengths of different models for different tasks. In particular, it facilitates the use of local models for point tasks. This can be important for cost and privacy.

#### [](#spring-and-jvm-integration)1.3.6. Spring and JVM Integration

Built on Spring and the JVM, making it easy to access existing enterprise functionality and capabilities. For example:

*   Spring can inject and manage agents, including using Spring AOP to decorate functions.

*   Robust persistence and transaction management solutions are available.


#### [](#designed-for-testability)1.3.7. Designed for Testability

Both unit testing and agent end-to-end testing are easy from the ground up.

### [](#overview__concepts)1.4. Core Concepts

Agent frameworks break up tasks into separate smaller interactions, making LLM use more predictable and focused.

Embabel models agentic flows in terms of:

*   **Actions**: Steps an agent takes. These are the building blocks of agent behavior.

*   **Goals**: What an agent is trying to achieve.

*   **Conditions**: Conditions to while planning. Conditions are reassessed after each action is executed.

*   **Domain Model**: Objects underpinning the flow and informing Actions, Goals and Conditions.


This enables Embabel to create a **plan**: A sequence of actions to achieve a goal. Plans are dynamically formulated by the system, not the programmer. The system replans after the completion of each action, allowing it to adapt to new information as well as observe the effects of the previous action. This is effectively an [OODA loop](https://en.wikipedia.org/wiki/OODA_loop).



#### [](#complete-example)1.4.1. Complete Example

Let’s look at a complete example that demonstrates how Embabel infers conditions from input/output types and manages data flow between actions. This example comes from the [Embabel Agent Examples](https://github.com/embabel/embabel-agent-examples) repository:

```
@Agent(description = "Find news based on a person's star sign")  (1)
public class StarNewsFinder {

    private final HoroscopeService horoscopeService;  (2)
    private final int storyCount;

    public StarNewsFinder(
            HoroscopeService horoscopeService,  (3)
            @Value("${star-news-finder.story.count:5}") int storyCount) {
        this.horoscopeService = horoscopeService;
        this.storyCount = storyCount;
    }

    @Action  (4)
    public StarPerson extractStarPerson(UserInput userInput, OperationContext context) {  (5)
        return context.ai()
            .withLlm(OpenAiModels.GPT_41)
            .createObject("""
                Create a person from this user input, extracting their name and star sign:
                %s""".formatted(userInput.getContent()), StarPerson.class);  (6)
    }

    @Action  (7)
    public Horoscope retrieveHoroscope(StarPerson starPerson) {  (8)
        // Uses regular injected Spring service - not LLM
        return new Horoscope(horoscopeService.dailyHoroscope(starPerson.sign()));  (9)
    }

    @Action(toolGroups = {CoreToolGroups.WEB})  (10)
    public RelevantNewsStories findNewsStories(
            StarPerson person, Horoscope horoscope, OperationContext context) {  (11)
        var prompt = """
            %s is an astrology believer with the sign %s.
            Their horoscope for today is: %s
            Given this, use web tools to find %d relevant news stories.
            """.formatted(person.name(), person.sign(), horoscope.summary(), storyCount);

        return context.ai().withDefaultLlm().createObject(prompt, RelevantNewsStories.class);  (12)
    }

    @AchievesGoal(description = "Write an amusing writeup based on horoscope and news")  (13)
    @Action
    public Writeup writeup(
            StarPerson person, RelevantNewsStories stories, Horoscope horoscope,
            OperationContext context) {  (14)
        var llm = LlmOptions.fromCriteria(ModelSelectionCriteria.getAuto())
            .withTemperature(0.9);  (15)

        var prompt = """
            Write something amusing for %s based on their horoscope and these news stories.
            Format as Markdown with links.
            """.formatted(person.name());

        return context.ai().withLlm(llm).createObject(prompt, Writeup.class);  (16)
    }
}
```




* 1: 2
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Spring Integration: Regular Spring dependency injection - the agent uses both LLM services and traditional business services.
* 1: 3
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Service Injection: HoroscopeService is injected like any Spring bean - agents can mix AI and non-AI operations seamlessly.
* 1: 4
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Action Definition: @Action marks methods as steps the agent can take.Each action represents a capability.
* 1: 5
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Input Condition Inference: The method signature extractStarPerson(UserInput userInput, …​) tells Embabel:Precondition: "A UserInput object must be available"Required Data: The agent needs user input to proceedCapability: This action can extract structured data from unstructured input
* 1: 6
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Output Condition Creation: Returning StarPerson creates:Postcondition: "A StarPerson object is now available in the world state"Data Availability: This output becomes input for subsequent actionsType Safety: The domain model enforces structure
* 1: 7
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Non-LLM Action: Not all actions use LLMs - this demonstrates hybrid AI/traditional programming.
* 1: 8
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Data Flow Chain: The method signature retrieveHoroscope(StarPerson starPerson) creates:Precondition: "A StarPerson object must exist" (from previous action)Dependency: This action can only execute after extractStarPerson completesService Integration: Uses the injected horoscopeService rather than an LLM
* 1: 9
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Regular Service Call: This action calls a traditional Spring service - demonstrating how agents blend AI and conventional operations.
* 1: 10
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Tool Requirements: toolGroups = {CoreToolGroups.WEB} specifies this action needs web search capabilities.
* 1: 11
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Multi-Input Dependencies: This method requires both StarPerson and Horoscope - showing complex data flow orchestration.
* 1: 12
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Tool-Enabled LLM: The LLM can use web tools to search for current news stories based on the horoscope context.
* 1: 13
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Goal Achievement: @AchievesGoal marks this as a terminal action that completes the agent’s objective.
* 1: 14
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Complex Input Requirements: The final action requires three different data types, showing sophisticated orchestration.
* 1: 15
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Creative Configuration: High temperature (0.9) optimizes for creative, entertaining output - appropriate for amusing writeups.
* 1: 16
    * Agent Declaration: The @Agent annotation defines this as an agent capable of a multi-step flow.: Final Output: Returns Writeup, completing the agent’s goal with personalized content.


State is managed by the framework, through the process blackboard

#### [](#the-inferred-execution-plan)1.4.2. The Inferred Execution Plan

Based on the type signatures alone, Embabel automatically infers this execution plan:

**Goal**: Produce a `Writeup` (final return type of `@AchievesGoal` action)

The initial plan:

*   To emit `Writeup` → need `writeup()` action

*   `writeup()` requires `StarPerson`, `RelevantNewsStories`, and `Horoscope`

*   To get `StarPerson` → need `extractStarPerson()` action

*   To get `Horoscope` → need `retrieveHoroscope()` action (requires `StarPerson`)

*   To get `RelevantNewsStories` → need `findNewsStories()` action (requires `StarPerson` and `Horoscope`)

*   `extractStarPerson()` requires `UserInput` → must be provided by user


Execution sequence:

`UserInput` → `extractStarPerson()` → `StarPerson` → `retrieveHoroscope()` → `Horoscope` → `findNewsStories()` → `RelevantNewsStories` → `writeup()` → `Writeup` and achieves goal.

#### [](#key-benefits-of-type-driven-flow)1.4.3. Key Benefits of Type-Driven Flow

**Automatic Orchestration**: No manual workflow definition needed - the agent figures out the sequence from type dependencies. This is particularly beneficial if things go wrong, as the planner can re-evaluate the situation and may be able to find an alternative path to the goal.

**Dynamic Replanning**: After each action, the agent reassesses what’s possible based on available data objects.

**Type Safety**: Compile-time guarantees that data flows correctly between actions. No magic string keys.

**Flexible Execution**: If multiple actions could produce the required input type, the agent chooses based on context and efficiency. (Actions can have cost and value.)

This demonstrates how Embabel transforms simple method signatures into sophisticated multi-step agent behavior, with the complex orchestration handled automatically by the framework.

[](#agent.guide)2\. Getting Started
-----------------------------------

### [](#getting-started.installing)2.2. Getting the Binaries

The easiest way to get started with Embabel Agent is to add the Spring Boot starter dependency to your project.

#### [](#maven)2.2.1. Maven

Add the appropriate Embabel Agent Spring Boot starter to your `pom.xml` depending on your application type:

##### [](#shell-starter)Shell Starter

Starts the application in console mode with an interactive shell powered by Embabel.

```
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-shell</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```


**Features:**

*   ✅ Interactive command-line interface

*   ✅ Agent discovery and registration

*   ✅ Human-in-the-loop capabilities

*   ✅ Progress tracking and logging

*   ✅ Development-friendly error handling


##### [](#mcp-server-starter)MCP Server Starter

Starts the application with HTTP listener where agents are autodiscovered and registered as MCP servers, available for integration via SSE protocol.

```
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-mcpserver</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```


**Features:**

*   ✅️ MCP protocol server implementation

*   ✅️ Tool registration and discovery

*   ✅️ JSON-RPC communication via SSE (Server-Sent Events)

*   ✅️ Integration with MCP-compatible clients

*   ✅️ Security and sandboxing


##### [](#basic-agent-platform-starter)Basic Agent Platform Starter

Initializes Embabel Agent Platform in the Spring Container. Platform beans are available via Spring Dependency Injection mechanism. Application startup mode (web, console, microservice, etc.) is determined by the Application Designer.

```
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter</artifactId>
    <version>${embabel-agent.version}</version>
</dependency>
```


**Features:**

*   ✅️ Application decides on startup mode (console, web application, etc)

*   ✅️ Agent discovery and registration

*   ✅️ Agent Platform beans available via Dependency Injection mechanism

*   ✅️ Progress tracking and logging

*   ✅️ Development-friendly error handling


You’ll also need to add the Embabel repository to your `pom.xml`:

```
<repositories>
    <repository>
        <id>embabel-releases</id>
        <url>https://repo.embabel.com/artifactory/libs-release</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>embabel-snapshots</id>
        <url>https://repo.embabel.com/artifactory/libs-snapshot</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```


#### [](#gradle)2.2.2. Gradle

Add the required repositories to your `build.gradle.kts`:

```
repositories {
    mavenCentral()
    maven {
        name = "embabel-releases"
        url = uri("https://repo.embabel.com/artifactory/libs-release")
        mavenContent {
            releasesOnly()
        }
    }
    maven {
        name = "embabel-snapshots"
        url = uri("https://repo.embabel.com/artifactory/libs-snapshot")
        mavenContent {
            snapshotsOnly()
        }
    }
    maven {
        name = "Spring Milestones"
        url = uri("https://repo.spring.io/milestone")
    }
}
```


Add the Embabel Agent starter dependency of choice:

```
dependencies {
    implementation("com.embabel.agent:embabel-agent-starter-shell:${embabel-agent.version}")
}
```


For Gradle Groovy DSL (`build.gradle`):

```
repositories {
    mavenCentral()
    maven {
        name = 'embabel-releases'
        url = 'https://repo.embabel.com/artifactory/libs-release'
        mavenContent {
            releasesOnly()
        }
    }
    maven {
        name = 'embabel-snapshots'
        url = 'https://repo.embabel.com/artifactory/libs-snapshot'
        mavenContent {
            snapshotsOnly()
        }
    }
    maven {
        name = 'Spring Milestones'
        url = 'https://repo.spring.io/milestone'
    }
}

dependencies {
    implementation 'com.embabel.agent:embabel-agent-starter-shell:${embabel-agent.version}'
}
```


#### [](#environment-setup)2.2.3. Environment Setup

Before running your application, you’ll need to set up your environment with API keys for the LLM providers you plan to use.

Required: - `OPENAI_API_KEY`: For OpenAI models (GPT-4, GPT-5, etc.)

Optional but recommended: - `ANTHROPIC_API_KEY`: For Anthropic models (Claude 3.x, etc.)

Example `.env` file:

```
OPENAI_API_KEY=your_openai_api_key_here
ANTHROPIC_API_KEY=your_anthropic_api_key_here
```


### [](#getting-started.running)2.3. Getting Embabel Running

#### [](#running-the-examples)2.3.1. Running the Examples

The quickest way to get started with Embabel is to run the examples:

```
# Clone and run examples
git clone https://github.com/embabel/embabel-agent-examples
cd embabel-agent-examples/scripts/java
./shell.sh
```




#### [](#prerequisites)2.3.2. Prerequisites

*   Java 21+

*   API Key from OpenAI or Anthropic

*   Maven 3.9+ (optional)


Set your API keys:

```
export OPENAI_API_KEY="your_openai_key"
export ANTHROPIC_API_KEY="your_anthropic_key"
```


#### [](#using-the-shell)2.3.3. Using the Shell

Spring Shell is an easy way to interact with the Embabel agent framework, especially during development.

Type `help` to see available commands. Use `execute` or `x` to run an agent:

```
execute "Lynda is a Scorpio, find news for her" -p -r
```


This will look for an agent, choose the star finder agent and run the flow. `-p` will log prompts `-r` will log LLM responses. Omit these for less verbose logging.

Options:

*   `-p` logs prompts

*   `-r` logs LLM responses


Use the `chat` command to enter an interactive chat with the agent. It will attempt to run the most appropriate agent for each command.



#### [](#example-commands)2.3.4. Example Commands

Try these commands in the shell:

```
# Simple horoscope agent
execute "My name is Sarah and I'm a Leo"

# Research with web tools (requires Docker Desktop with MCP extension)
execute "research the recent australian federal election. what is the position of the Greens party?"

# Fact checking
x "fact check the following: holden cars are still made in australia"
```


#### [](#implementing-your-own-shell-commands)2.3.5. Implementing Your Own Shell Commands

Particularly during development, you may want to implement your own shell commands to try agents or flows. Simply write a Spring Shell component and Spring will inject it and register it automatically.

For example, you can inject the `AgentPlatform` and use it to invoke agents directly, as in this code from the examples repository:

```
@ShellComponent
public record SupportAgentShellCommands(
        AgentPlatform agentPlatform
) {

    @ShellMethod("Get bank support for a customer query")
    public String bankSupport(
            @ShellOption(value = "id", help = "customer id", defaultValue = "123") Long id,
            @ShellOption(value = "query", help = "customer query", defaultValue = "What's my balance, including pending amounts?") String query
    ) {
        var supportInput = new SupportInput(id, query);
        System.out.println("Support input: " + supportInput);
        var invocation = AgentInvocation
                .builder(agentPlatform)
                .options(ProcessOptions.builder().verbosity(v -> v.showPrompts(true)).build())
                .build(SupportOutput.class);
        var result = invocation.invoke(supportInput);
        return result.toString();
    }
}
```


### [](#getting-started.a-little-ai)2.4. Adding a Little AI to Your Application

Before we get into the magic of full-blown Embabel agents, let’s see how easy it is to add a little AI to your application using the Embabel framework. Sometimes this is all you need.

The simplest way to use Embabel is to inject an `OperationContext` and use its AI capabilities directly. This approach is consistent with standard Spring dependency injection patterns.

```
package com.embabel.example.injection;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.stereotype.Component;

/**
 * Demonstrate the simplest use of Embabel's AI capabilities,
 * injecting an AI helper into a Spring component.
 * The jokes will be terrible, but don't blame Embabel, blame the LLM.
 */
@Component
public record InjectedComponent(Ai ai) {

    public record Joke(String leadup, String punchline) {
    }

    public String tellJokeAbout(String topic) {
        return ai
                .withDefaultLlm()
                .generateText("Tell me a joke about " + topic);
    }

    public Joke createJokeObjectAbout(String topic1, String topic2, String voice) {
        return ai
                .withLlm(LlmOptions.withDefaultLlm().withTemperature(.8))
                .createObject("""
                                Tell me a joke about %s and %s.
                                The voice of the joke should be %s.
                                The joke should have a leadup and a punchline.
                                """.formatted(topic1, topic2, voice),
                        Joke.class);
    }

}
```


This example demonstrates several key aspects of Embabel’s design philosophy:

*   **Standard Spring Integration**: The `Ai` object is injected like any other Spring dependency using constructor injection

*   **Simple API**: Access AI capabilities through the `Ai` interface directly or `OperationContext.ai()`, which can also be injected in the same way

*   **Flexible Configuration**: Configure LLM options like temperature on a per-call basis

*   **Type Safety**: Generate structured objects directly with `createObject()` method

*   **Consistent Patterns**: Works exactly like you’d expect any Spring component to work


The `Ai` type provides access to all of Embabel’s AI capabilities without requiring a full agent setup, making it perfect for adding AI features to existing applications incrementally.



### [](#getting-started.first-agent)2.5. Writing Your First Agent

The easiest way to create your first agent is to use the Java or Kotlin template repositories.

#### [](#using-the-template)2.5.1. Using the Template

Or use the project creator:

```
uvx --from git+https://github.com/embabel/project-creator.git project-creator
```


#### [](#example-writeandreviewagent)2.5.2. Example: WriteAndReviewAgent

The Java template includes a `WriteAndReviewAgent` that demonstrates key concepts:

```
@Agent(description = "Agent that writes and reviews stories")
public class WriteAndReviewAgent {

    @Action
    public Story writeStory(UserInput userInput, OperationContext context) {
        // High temperature for creativity
        var writer = LlmOptions.withModel(OpenAiModels.GPT_4O_MINI)
            .withTemperature(0.8)
            .withPersona("You are a creative storyteller");

        return context.ai()
            .withLlm(writer)
            .createObject("Write a story about: " + userInput.getContent(), Story.class);
    }

    @AchievesGoal(description = "Review and improve the story")
    @Action
    public ReviewedStory reviewStory(Story story, OperationContext context) {
        // Low temperature for analytical review
        var reviewer = LlmOptions.withModel(OpenAiModels.GPT_4O_MINI)
            .withTemperature(0.2)
            .withPersona("You are a careful editor and reviewer");

        String prompt = "Review this story and suggest improvements: " + story.text();

        return context.ai()
            .withLlm(reviewer)
            .createObject(prompt, ReviewedStory.class);
    }
}
```


#### [](#key-concepts-demonstrated)2.5.3. Key Concepts Demonstrated

**Multiple LLMs with Different Configurations:**

*   Writer LLM uses high temperature (0.8) for creativity

*   Reviewer LLM uses low temperature (0.2) for analytical review

*   Different personas guide the model behavior


**Actions and Goals:**

*   `@Action` methods are the steps the agent can take

*   `@AchievesGoal` marks the final action that completes the agent’s work


**Domain Objects:**

*   `Story` and `ReviewedStory` are strongly-typed domain objects

*   Help structure the interaction between actions


#### [](#running-your-agent)2.5.4. Running Your Agent

Set your API keys and run the shell:

```
export OPENAI_API_KEY="your_key_here"
./scripts/shell.sh
```


In the shell, try:

```
x "Tell me a story about a robot learning to paint"
```


The agent will:

1.  Generate a creative story using the writer LLM

2.  Review and improve it using the reviewer LLM

3.  Return the final reviewed story


#### [](#next-steps)2.5.5. Next Steps

*   Explore the [examples repository](https://github.com/embabel/embabel-agent-examples) for more complex agents

*   Read the [Reference Documentation](#reference.reference) for detailed API information

*   Try building your own domain-specific agents


[](#reference__reference)3\. Reference
--------------------------------------

### [](#reference.flow)3.1. Invoking an Agent

Agents can be invoked programmatically or via user input.

See [Invoking Embabel Agents](#reference.invoking) for details on programmatic invocation. Programmatic invocation typically involves structured types other than user input.

In the case of user input, an LLM will choose the appropriate agent via the `Autonomy` class. Behavior varies depending on configuration:

*   In closed mode, the LLM will select the agent based on the user input and the available agents in the system.

*   In open mode, the LLM will select the goal based on the user input and then assemble an agent that can achieve that goal from the present world state.


### [](#agent-process-flow)3.2. Agent Process Flow

When an agent is invoked, Embabel creates an `AgentProcess` with a unique identifier that manages the complete execution lifecycle.

#### [](#agentprocess-lifecycle)3.2.1. AgentProcess Lifecycle

An `AgentProcess` maintains state throughout its execution and can transition between various states:

**Process States:**

*   `NOT_STARTED`: The process has not started yet

*   `RUNNING`: The process is executing without any known problems

*   `COMPLETED`: The process has completed successfully

*   `FAILED`: The process has failed and cannot continue

*   `TERMINATED`: The process was killed by an early termination policy

*   `KILLED`: The process was killed by the user or platform

*   `STUCK`: The process cannot formulate a plan to progress (may be temporary)

*   `WAITING`: The process is waiting for user input or external event

*   `PAUSED`: The process has paused due to scheduling policy


**Process Execution Methods:**

*   `tick()`: Perform the next single step and return when an action completes

*   `run()`: Execute the process as far as possible until completion, failure, or a waiting state


These methods are not directly called by user code, but are managed by the framework to control execution flow.

Each `AgentProcess` maintains:

*   **Unique ID**: Persistent identifier for tracking and reference

*   **History**: Record of all executed actions with timing information

*   **Goal**: The objective the process is trying to achieve

*   **Failure Info**: Details about any failure that occurred

*   **Parent ID**: Reference to parent process for nested executions


#### [](#planning)3.2.2. Planning

Planning occurs after each action execution using Goal-Oriented Action Planning (GOAP). The planning process:

1.  **Analyze Current State**: Examine the current blackboard contents and world state

2.  **Identify Available Actions**: Find all actions that can be executed based on their preconditions

3.  **Search for Action Sequences**: Use A\* algorithm to find optimal paths to achieve the goal

4.  **Select Optimal Plan**: Choose the best action sequence based on cost and success probability

5.  **Execute Next Action**: Run the first action in the plan and replan


This creates a dynamic **OODA loop** (Observe-Orient-Decide-Act): - **Observe**: Check current blackboard state and action results - **Orient**: Understand what has changed since the last planning cycle - **Decide**: Formulate or update the plan based on new information - **Act**: Execute the next planned action

The replanning approach allows agents to:

*   Adapt to unexpected action results

*   Handle dynamic environments where conditions change

*   Recover from partial failures

*   Take advantage of new opportunities that arise


#### [](#blackboard)3.2.3. Blackboard

The Blackboard serves as the shared memory system that maintains state throughout the agent process execution. It implements the [Blackboard architectural pattern](https://en.wikipedia.org/wiki/Blackboard_\(design_pattern\)), a knowledge-based system approach.

Most of the time, user code doesn’t need to interact with the blackboard directly, as it is managed by the framework. For example, action inputs come from the blackboard, and action outputs are automatically added to the blackboard, and conditions are evaluated based on its contents.

**Key Characteristics:**

*   **Central Repository**: Stores all domain objects, intermediate results, and process state

*   **Type-Based Access**: Objects are indexed and retrieved by their types

*   **Ordered Storage**: Objects maintain the order they were added, with latest being default

*   **Immutable Objects**: Once added, objects cannot be modified (new versions can be added)

*   **Condition Tracking**: Maintains boolean conditions used by the planning system


**Core Operations:**

```
// Add objects to blackboard
blackboard += person
blackboard["result"] = analysis

// Retrieve objects by type
val person = blackboard.last<Person>()
val allPersons = blackboard.all<Person>()

// Check conditions
blackboard.setCondition("userVerified", true)
val verified = blackboard.getCondition("userVerified")
```


**Data Flow:**

1.  **Input Processing**: Initial user input is added to the blackboard

2.  **Action Execution**: Each action reads inputs from blackboard and adds results

3.  **State Evolution**: Blackboard accumulates objects representing the evolving state

4.  **Planning Input**: Current blackboard state informs the next planning cycle

5.  **Result Extraction**: Final results are retrieved from blackboard upon completion


The blackboard enables:

*   **Loose Coupling**: Actions don’t need direct references to each other

*   **Flexible Data Flow**: Actions can consume any available data of the right type

*   **State Persistence**: Complete execution history is maintained

*   **Debugging Support**: Full visibility into state evolution for troubleshooting


#### [](#reference.flow__binding)3.2.4. Binding

By default items in the blackboard are matched by type. When there are multiple candidates the most recent one is provided. It is also possible to assign a keyed name to blackboard items. Example:

```
@Action
public Person extractPerson(UserInput userInput, OperationContext context) {
    PersonImpl maybeAPerson = context.promptRunner().withLlm(LlmOptions.fromModel(OpenAiModels.GPT_41)).createObjectIfPossible(
            """
                    Create a person from this user input, extracting their name:
                    %s""".formatted(userInput.getContent()),
            PersonImpl.class
    );
    if (maybeAPerson != null) {
        context.bind("user", maybeAPerson);
    }
    return maybeAPerson;
}
```




### [](#reference.steps)3.3. Goals, Actions and Conditions

### [](#reference.domain)3.4. Domain Objects

Domain objects in Embabel are not just strongly-typed data structures - they are real objects with behavior that can be selectively exposed to LLMs and used in agent actions.

#### [](#objects-with-behavior)3.4.1. Objects with Behavior

Unlike simple structs or DTOs, Embabel domain objects can encapsulate business logic and expose it to LLMs through the `@Tool` annotation:

```
@Entity
public class Customer {
    private String name;
    private LoyaltyLevel loyaltyLevel;
    private List<Order> orders;

    @Tool(description = "Calculate the customer's loyalty discount percentage")
    public BigDecimal getLoyaltyDiscount() {
        return loyaltyLevel.calculateDiscount(orders.size());
    }

    @Tool(description = "Check if customer is eligible for premium service")
    public boolean isPremiumEligible() {
        return orders.stream()
            .mapToDouble(Order::getTotal)
            .sum() > 1000.0;
    }

    // Regular methods not exposed to LLMs
    private void updateLoyaltyLevel() {
        // Internal business logic
    }
}
```


#### [](#selective-tool-exposure)3.4.2. Selective Tool Exposure

The `@Tool` annotation allows you to selectively expose domain object methods to LLMs:

*   **Business Logic**: Expose methods that provide business value to the LLM

*   **Calculated Properties**: Methods that compute derived values

*   **Business Rules**: Methods that implement domain-specific rules

*   **Keep Private**: Internal implementation details remain hidden


#### [](#use-in-actions)3.4.3. Use in Actions

Domain objects can be used naturally in action methods, combining LLM interactions with traditional object-oriented programming:

```
@Action
public Recommendation generateRecommendation(Customer customer, OperationContext context) {
    // LLM has access to customer.getLoyaltyDiscount() and customer.isPremiumEligible()
    // as tools, plus the customer object structure

    String prompt = String.format(
        "Generate a personalized recommendation for %s based on their profile",
        customer.getName()
    );

    return context.ai()
        .withDefaultLlm()
        .createObject(prompt, Recommendation.class);
}
```


#### [](#domain-understanding-is-critical)3.4.4. Domain Understanding is Critical

As outlined in Rod Johnson’s blog introducing DICE (Domain-Integrated Context Engineering) in [Context Engineering Needs Domain Understanding](https://medium.com/@springrod/context-engineering-needs-domain-understanding-b4387e8e4bf8), domain understanding is fundamental to effective context engineering. Domain objects serve as the bridge between:

*   **Business Domain**: Real-world entities and their relationships

*   **Agent Behavior**: How LLMs understand and interact with the domain

*   **Code Actions**: Traditional programming logic that operates on domain objects


#### [](#benefits)3.4.5. Benefits

*   **Rich Context**: LLMs receive both data structure and behavioral context

*   **Encapsulation**: Business logic stays within domain objects where it belongs

*   **Reusability**: Domain objects can be used across multiple agents

*   **Testability**: Domain logic can be unit tested independently

*   **Evolution**: Adding new tools to domain objects extends agent capabilities


This approach ensures that agents work with meaningful business entities rather than generic data structures, leading to more natural and effective AI interactions.

### [](#reference.configuration)3.5. Configuration

#### [](#enabling-embabel)3.5.1. Enabling Embabel

Annotate your Spring Boot application class to get agentic behavior.

Example:

```
@SpringBootApplication
@EnableAgents(
    loggingTheme = LoggingThemes.STAR_WARS,
    localModels = { "docker" },
    mcpClients = { "docker" }
)
class MyAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyAgentApplication.class, args);
    }
}
```


This is a normal Spring Boot application class. You can add other Spring Boot annotations as needed. `@EnableAgents` enables the agent framework. It allows you to specify a logging theme (optional) and well-known sources of local models and MCP tools. In this case we’re using Docker as a source of local models and MCP tools.

#### [](#configuration-properties)3.5.2. Configuration Properties

The following table lists all available configuration properties in Embabel Agent Platform. Properties are organized by their configuration prefix and include default values where applicable. They can be set via `application.properties`, `application.yml`, profile-specific configuration files or environment variables, as per standard Spring configuration practices.

##### [](#platform-configuration)Platform Configuration

From `AgentPlatformProperties` - unified configuration for all agent platform properties.



* Property: embabel.agent.platform.name
    * Type: String
    * Default: embabel-default
    * Description: Core platform identity name
* Property: embabel.agent.platform.description
    * Type: String
    * Default: Embabel Default Agent Platform
    * Description: Platform description


##### [](#agent-scanning)Agent Scanning

From `AgentPlatformProperties.ScanningConfig` - configures scanning of the classpath for agents.



* Property: embabel.agent.platform.scanning.annotation
    * Type: Boolean
    * Default: true
    * Description: Whether to auto register beans with @Agent and @Agentic annotation
* Property: embabel.agent.platform.scanning.bean
    * Type: Boolean
    * Default: false
    * Description: Whether to auto register as agents Spring beans of type Agent


##### [](#ranking-configuration)Ranking Configuration

From `AgentPlatformProperties.RankingConfig` - configures ranking of agents and goals based on user input when the platform should choose the agent or goal.



* Property: embabel.agent.platform.ranking.llm
    * Type: String
    * Default: null
    * Description: Name of the LLM to use for ranking, or null to use auto selection
* Property: embabel.agent.platform.ranking.max-attempts
    * Type: Int
    * Default: 5
    * Description: Maximum number of attempts to retry ranking
* Property: embabel.agent.platform.ranking.backoff-millis
    * Type: Long
    * Default: 100
    * Description: Initial backoff time in milliseconds
* Property: embabel.agent.platform.ranking.backoff-multiplier
    * Type: Double
    * Default: 5.0
    * Description: Multiplier for backoff time
* Property: embabel.agent.platform.ranking.backoff-max-interval
    * Type: Long
    * Default: 180000
    * Description: Maximum backoff time in milliseconds


##### [](#llm-operations)LLM Operations

From `AgentPlatformProperties.LlmOperationsConfig` - configuration for LLM operations including prompts and data binding.



* Property: embabel.agent.platform.llm-operations.prompts.maybe-prompt-template
    * Type: String
    * Default: maybe_prompt_contribution
    * Description: Template for "maybe" prompt, enabling failure result when LLM lacks information
* Property: embabel.agent.platform.llm-operations.prompts.generate-examples-by-default
    * Type: Boolean
    * Default: true
    * Description: Whether to generate examples by default
* Property: embabel.agent.platform.llm-operations.data-binding.max-attempts
    * Type: Int
    * Default: 10
    * Description: Maximum retry attempts for data binding
* Property: embabel.agent.platform.llm-operations.data-binding.fixed-backoff-millis
    * Type: Long
    * Default: 30
    * Description: Fixed backoff time in milliseconds between retries


##### [](#process-id-generation)Process ID Generation

From `AgentPlatformProperties.ProcessIdGenerationConfig` - configuration for process ID generation.



* Property: embabel.agent.platform.process-id-generation.include-version
    * Type: Boolean
    * Default: false
    * Description: Whether to include version in process ID generation
* Property: embabel.agent.platform.process-id-generation.include-agent-name
    * Type: Boolean
    * Default: false
    * Description: Whether to include agent name in process ID generation


##### [](#autonomy-configuration)Autonomy Configuration

From `AgentPlatformProperties.AutonomyConfig` - configures thresholds for agent and goal selection. Certainty below thresholds will result in failure to choose an agent or goal.



* Property: embabel.agent.platform.autonomy.agent-confidence-cut-off
    * Type: Double
    * Default: 0.6
    * Description: Confidence threshold for agent operations
* Property: embabel.agent.platform.autonomy.goal-confidence-cut-off
    * Type: Double
    * Default: 0.6
    * Description: Confidence threshold for goal achievement


##### [](#model-provider-configuration)Model Provider Configuration

From `AgentPlatformProperties.ModelsConfig` - model provider integration configurations.

###### [](#anthropic)Anthropic



* Property: embabel.agent.platform.models.anthropic.max-attempts
    * Type: Int
    * Default: 10
    * Description: Maximum retry attempts
* Property: embabel.agent.platform.models.anthropic.backoff-millis
    * Type: Long
    * Default: 5000
    * Description: Initial backoff time in milliseconds
* Property: embabel.agent.platform.models.anthropic.backoff-multiplier
    * Type: Double
    * Default: 5.0
    * Description: Backoff multiplier
* Property: embabel.agent.platform.models.anthropic.backoff-max-interval
    * Type: Long
    * Default: 180000
    * Description: Maximum backoff interval in milliseconds


###### [](#openai)OpenAI



* Property: embabel.agent.platform.models.openai.max-attempts
    * Type: Int
    * Default: 10
    * Description: Maximum retry attempts
* Property: embabel.agent.platform.models.openai.backoff-millis
    * Type: Long
    * Default: 5000
    * Description: Initial backoff time in milliseconds
* Property: embabel.agent.platform.models.openai.backoff-multiplier
    * Type: Double
    * Default: 5.0
    * Description: Backoff multiplier
* Property: embabel.agent.platform.models.openai.backoff-max-interval
    * Type: Long
    * Default: 180000
    * Description: Maximum backoff interval in milliseconds


##### [](#server-sent-events)Server-Sent Events

From `AgentPlatformProperties.SseConfig` - server-sent events configuration.


|Property                                      |Type|Default|Description                      |
|----------------------------------------------|----|-------|---------------------------------|
|embabel.agent.platform.sse.max-buffer-size    |Int |100    |Maximum buffer size for SSE      |
|embabel.agent.platform.sse.max-process-buffers|Int |1000   |Maximum number of process buffers|


##### [](#test-configuration)Test Configuration

From `AgentPlatformProperties.TestConfig` - test configuration.


|Property                             |Type   |Default|Description                            |
|-------------------------------------|-------|-------|---------------------------------------|
|embabel.agent.platform.test.mock-mode|Boolean|true   |Whether to enable mock mode for testing|


##### [](#process-repository-configuration)Process Repository Configuration

From `ProcessRepositoryProperties` - configuration for the agent process repository.



* Property: embabel.agent.platform.process-repository.window-size
    * Type: Int
    * Default: 1000
    * Description: Maximum number of agent processes to keep in memory when using default InMemoryAgentProcessRepository. When exceeded, oldest processes are evicted.


##### [](#standalone-llm-configuration)Standalone LLM Configuration

###### [](#llm-operations-prompts)LLM Operations Prompts

From `LlmOperationsPromptsProperties` - properties for ChatClientLlmOperations operations.



* Property: embabel.llm-operations.prompts.maybe-prompt-template
    * Type: String
    * Default: maybe_prompt_contribution
    * Description: Template to use for the "maybe" prompt, which can enable a failure result if the LLM does not have enough information to create the desired output structure
* Property: embabel.llm-operations.prompts.generate-examples-by-default
    * Type: Boolean
    * Default: true
    * Description: Whether to generate examples by default
* Property: embabel.llm-operations.prompts.default-timeout
    * Type: Duration
    * Default: 60s
    * Description: Default timeout for operations


###### [](#llm-data-binding)LLM Data Binding

From `LlmDataBindingProperties` - data binding properties with retry configuration for LLM operations.



* Property: embabel.llm-operations.data-binding.max-attempts
    * Type: Int
    * Default: 10
    * Description: Maximum retry attempts for data binding
* Property: embabel.llm-operations.data-binding.fixed-backoff-millis
    * Type: Long
    * Default: 30
    * Description: Fixed backoff time in milliseconds between retries


##### [](#additional-model-providers)Additional Model Providers

###### [](#aws-bedrock)AWS Bedrock

From `BedrockProperties` - AWS Bedrock model configuration properties.



* Property: embabel.models.bedrock.models
    * Type: List
    * Default: []
    * Description: List of Bedrock models to configure
* Property: embabel.models.bedrock.models[].name
    * Type: String
    * Default: ""
    * Description: Model name
* Property: embabel.models.bedrock.models[].knowledge-cutoff
    * Type: String
    * Default: ""
    * Description: Knowledge cutoff date
* Property: embabel.models.bedrock.models[].input-price
    * Type: Double
    * Default: 0.0
    * Description: Input token price
* Property: embabel.models.bedrock.models[].output-price
    * Type: Double
    * Default: 0.0
    * Description: Output token price


###### [](#docker-local-models)Docker Local Models

From `DockerProperties` - configuration for Docker local models (OpenAI-compatible).



* Property: embabel.docker.models.base-url
    * Type: String
    * Default: localhost:12434/engines
    * Description: Base URL for Docker model endpoint
* Property: embabel.docker.models.max-attempts
    * Type: Int
    * Default: 10
    * Description: Maximum retry attempts
* Property: embabel.docker.models.backoff-millis
    * Type: Long
    * Default: 2000
    * Description: Initial backoff time in milliseconds
* Property: embabel.docker.models.backoff-multiplier
    * Type: Double
    * Default: 5.0
    * Description: Backoff multiplier
* Property: embabel.docker.models.backoff-max-interval
    * Type: Long
    * Default: 180000
    * Description: Maximum backoff interval in milliseconds


##### [](#migration-support)Migration Support

From `DeprecatedPropertyScanningConfig` and `DeprecatedPropertyWarningConfig` - configuration for migrating from older versions of Embabel.





* Property: embabel.agent.platform.migration.scanning.enabled
    * Type: Boolean
    * Default: false
    * Description: Whether deprecated property scanning is enabled (disabled by default for production safety)
* Property: embabel.agent.platform.migration.scanning.include-packages
    * Type: List<String>
    * Default: ["com.embabel.agent", "com.embabel.agent.shell"]
    * Description: Base packages to scan for deprecated conditional annotations
* Property: embabel.agent.platform.migration.scanning.exclude-packages
    * Type: List<String>
    * Default: Extensive default list
    * Description: Package prefixes to exclude from scanning
* Property: embabel.agent.platform.migration.scanning.additional-excludes
    * Type: List<String>
    * Default: []
    * Description: Additional user-specific packages to exclude
* Property: embabel.agent.platform.migration.scanning.auto-exclude-jar-packages
    * Type: Boolean
    * Default: false
    * Description: Whether to automatically exclude JAR-based packages using classpath detection
* Property: embabel.agent.platform.migration.scanning.max-scan-depth
    * Type: Int
    * Default: 10
    * Description: Maximum depth for package scanning
* Property: embabel.agent.platform.migration.warnings.individual-logging
    * Type: Boolean
    * Default: true
    * Description: Whether to enable individual warning logging. When false, only aggregated summary is logged


### [](#reference.annotations)3.6. Annotation model

Embabel provides a Spring-style annotation model to define agents, actions, goals, and conditions. This is the recommended model to use in Java, and remains compelling in Kotlin.

#### [](#the-agent-annotation)3.6.1. The `@Agent` annotation

This is used on a class to define an agent. It is a Spring stereotype annotation, so it triggers Spring component scanning. Your agent class will automatically be registered as a Spring bean. It will also be registered with the agent framework, so it can be used in agent processes.

You must provide the `description` parameter, which is a human-readable description of the agent. This is particularly important as it may be used by the LLM in agent selection.

#### [](#the-action-annotation)3.6.2. The `@Action` annotation

The `@Action` annotation is used to mark methods that perform actions within an agent.

Action metadata can be specified on the annotation, including:

*   `description`: A human-readable description of the action.

*   `pre`: A list of preconditions _additional to the input types_ that must be satisfied before the action can be executed.

*   `post`: A list of postconditions _additional to the output type(s)_ that may be satisfied after the action is executed.

*   `canRerun`: A boolean indicating whether the action can be rerun if it has already been executed. Defaults to false.

*   `cost`:Relative cost of the action from 0-1. Defaults to 0.0.

*   `value`: Relative value of performing the action from 0-1. Defaults to 0.0.

*   `toolGroups`: Named tool groups the action requires.

*   `toolGroupRequirements`: Tool group requirements with QoS constraints.


#### [](#the-condition-annotation)3.6.3. The `@Condition` annotation

The `@Condition` annotation is used to mark methods that evaluate conditions. They can take an `OperationContext` parameter to access the blackboard and other infrastructure. If they take domain object parameters, the condition will automatically be false until suitable instances are available.

> Condition methods should not have side effects—​for example, on the blackboard. This is important because they may be called multiple times.

#### [](#parameters)3.6.4. Parameters

`@Action` methods must have at least one parameter. `@Condition` methods must have zero or more parameters, but otherwise follow the same rules as `@Action` methods regarding parameters. Ordering of parameters is not important.

Parameters fall in two categories:

*   _Domain objects_. These are the normal inputs for action methods. They are backed by the blackboard and will be used as inputs to the action method. A nullable domain object parameter will be populated if it is non-null on the blackboard. This enables nice-to-have parameters that are not required for the action to run.

*   _Infrastructure objects_. `OperationContext` parameters may be passed to action or condition methods.




The `ActionContext` or `ExecutingOperationContext` subtype can be used in action methods. It adds `asSubProcess` methods that can be used to run other agents in subprocesses. This is an important element of composition.

> Use the least specific type possible for parameters. Use `OperationContext` unless you are creating a subprocess.

#### [](#binding-by-name)3.6.5. Binding by name

The `@RequireNameMatch` annotation can be used to [bind parameters by name](#reference.flow__binding).

#### [](#handling-of-return-types)3.6.6. Handling of return types

Action methods normally return a single domain object.

Nullable return types are allowed. Returning null will trigger replanning. There may or not be an alternative path from that point, but it won’t be what the planner was trying to achieve.

There is a special case where the return type can essentially be a union type, where the action method can return one ore more of several types. This is achieved by a return type implementing the `SomeOf` tag interface. Implementations of this interface can have multiple nullable fields. Any non-null values will be bound to the blackboard, and the postconditions of the action will include all possible fields of the return type.

For example:

```
// Must implement the SomeOf interface
data class FrogOrDog(
    val frog: Frog? = null,
    val dog: Dog? = null,
) : SomeOf

@Agent(description = "Illustrates use of the SomeOf interface")
class ReturnsFrogOrDog {

    @Action
    fun frogOrDog(): FrogOrDog {
        return FrogOrDog(frog = Frog("Kermit"))
    }

    // This works because the frog field of the return type was set
    @AchievesGoal(description = "Create a prince from a frog")
    @Action
    fun toPerson(frog: Frog): PersonWithReverseTool {
        return PersonWithReverseTool(frog.name)
    }
}
```


This enables routing scenarios in an elegant manner.



#### [](#action-method-implementation)3.6.7. Action method implementation

Embabel makes it easy to seamlessly integrate LLM invocation and application code, using common types. An `@Action` method is a normal method, and can use any libraries or frameworks you like.

The only special thing about it is its ability to use the `OperationContext` parameter to access the blackboard and invoke LLMs.

#### [](#the-achievesgoal-annotation)3.6.8. The `@AchievesGoal` annotation

The `@AchievesGoal` annotation can be added to an `@Action` method to indicate that the completion of the action achieves a specific goal.

#### [](#implementing-the-stuckhandler-interface)3.6.9. Implementing the `StuckHandler` interface

If an annotated agent class implements the `StuckHandler` interface, it can handle situations where an action is stuck itself. For example, it can add data to the blackboard.

Example:

```
@Agent(
    description = "self unsticking agent",
)
class SelfUnstickingAgent : StuckHandler {

    // The agent will get stuck as there's no dog to convert to a frog
    @Action
    @AchievesGoal(description = "the big goal in the sky")
    fun toFrog(dog: Dog): Frog {
        return Frog(dog.name)
    }

    // This method will be called when the agent is stuck
    override fun handleStuck(agentProcess: AgentProcess): StuckHandlerResult {
        called = true
        agentProcess.addObject(Dog("Duke"))
        return StuckHandlerResult(
            message = "Unsticking myself",
            handler = this,
            code = StuckHandlingResultCode.REPLAN,
            agentProcess = agentProcess,
        )
    }
}
```


#### [](#advanced-usage-nested-processes)3.6.10. Advanced Usage: Nested processes

An `@Action` method can invoke another agent process. This is often done to use a stereotyped process that is composed using the DSL.

Use the `ActionContext.asSubProcess` method to create a sub-process from the action context.

For example:

```
@Action
fun report(
    reportRequest: ReportRequest,
    context: ActionContext,
): ScoredResult<Report, SimpleFeedback> = context.asSubProcess(
    // Will create an agent sub process with strong typing
    EvaluatorOptimizer.generateUntilAcceptable(
        maxIterations = 5,
        generator = {
            it.promptRunner().withToolGroup(CoreToolGroups.WEB).create(
                """
        Given the topic, generate a detailed report in ${reportRequest.words} words.

        # Topic
        ${reportRequest.topic}

        # Feedback
        ${it.input ?: "No feedback provided"}
                """.trimIndent()
            )
        },
        evaluator = {
            it.promptRunner().withToolGroup(CoreToolGroups.WEB).create(
                """
        Given the topic and word count, evaluate the report and provide feedback
        Feedback must be a score between 0 and 1, where 1 is perfect.

        # Report
        ${it.input.report}

        # Report request:

        ${reportRequest.topic}
        Word count: ${reportRequest.words}
        """.trimIndent()
            )
        },
    ))
```


### [](#reference.dsl)3.7. DSL

You can also create agents using a DSL in Kotlin or Java.

This is useful for workflows where you want an atomic action that is complete in itself but may contain multiple steps or actions.

#### [](#standard-workflows)3.7.1. Standard Workflows

There are a number of standard workflows, constructed by builders, that meet common requirements. These can be used to create agents that will be exposed as Spring beans, or within `@Action` methods within other agents. All are type safe. As far as possible, they use consistent APIs.

*   `SimpleAgentBuilder`: The simplest agent, with no preconditions or postconditions.

*   `ScatterGatherBuilder`: Fork join pattern for parallel processing.

*   `ConsensusBuilder`: A pattern for reaching consensus among multiple sources. Specialization of `ScatterGather`.

*   `RepeatUntil`: Repeats a step until a condition is met.

*   `RepeatUntilAcceptable`: Repeats a step while a condition is met, with a separate evaluator providing feedback.


Creating a simple agent:

```
var agent = SimpleAgentBuilder
    .returning(Joke.class) (1)
    .running(tac -> tac.ai() (2)
        .withDefaultLlm()
        .createObject("Tell me a joke", Joke.class))
    .buildAgent("joker", "This is guaranteed to return a dreadful joke"); (3)
```




* 1: 2
    * Specify the return type.: specify the action to run.Takes a SupplierActionContext<RESULT> OperationContext parameter allowing access to the current AgentProcess.
* 1: 3
    * Specify the return type.: Build an agent with the given name and description.


A more complex example:

```
@Action
FactChecks runAndConsolidateFactChecks(
        DistinctFactualAssertions distinctFactualAssertions,
        ActionContext context) {
    var llmFactChecks = properties.models().stream()
            .flatMap(model -> factCheckWithSingleLlm(model, distinctFactualAssertions, context))
            .toList();
    return ScatterGatherBuilder (1)
            .returning(FactChecks.class) (2)
            .fromElements(FactCheck.class) (3)
            .generatedBy(llmFactChecks) (4)
            .consolidatedBy(this::reconcileFactChecks) (5)
            .asSubProcess(context); (6)
    }
```




* 1: 2
    * Start building a scatter gather agent.: Specify the return type of the overall agent.
* 1: 3
    * Start building a scatter gather agent.: Specify the type of elements to be gathered.
* 1: 4
    * Start building a scatter gather agent.: Specify the list of functions to run in parallel, each generating an element, here of type FactCheck.
* 1: 5
    * Start building a scatter gather agent.: Specify a function to consolidate the results.In this case it will take a list of FactCheck and return a FactCheck and return a FactChecks object.
* 1: 6
    * Start building a scatter gather agent.: Build and run the agent as a subprocess of the current process.This is an alternative to asAgent shown in the SimpleAgentBuilder example.The API is consistent.


#### [](#registering-agent-beans)3.7.2. Registering `Agent` beans

Whereas the `@Agent` annotation causes a class to be picked up immediately by Spring, with the DSL you’ll need an extra step to register an agent with Spring. As shown in the example below, any `@Bean` of `Agent` type results auto registration, just like declaring a class annotated with `@Agent` does.

```
@Configuration
class FactCheckerAgentConfiguration {

    @Bean
    fun factChecker(factCheckerProperties: FactCheckerProperties): Agent {
        return factCheckerAgent(
            llms = listOf(
                LlmOptions(AnthropicModels.CLAUDE_35_HAIKU).withTemperature(.3),
                LlmOptions(AnthropicModels.CLAUDE_35_HAIKU).withTemperature(.0),
            ),
            properties = factCheckerProperties,
        )
    }
}
```


### [](#reference.types)3.8. Core Types

#### [](#llmoptions)3.8.1. LlmOptions

The `LlmOptions` class specifies which LLM to use and its hyperparameters. It’s defined in the [embabel-common](https://github.com/embabel/embabel-common) project and provides a fluent API for LLM configuration:

```
// Create LlmOptions with model and temperature
var options = LlmOptions.withModel(OpenAiModels.GPT_4O_MINI)
    .withTemperature(0.8);

// Use different hyperparameters for different tasks
var analyticalOptions = LlmOptions.withModel(OpenAiModels.GPT_4O_MINI)
    .withTemperature(0.2)
    .withTopP(0.9);
```


**Important Methods:**

*   `withModel(String)`: Specify the model name

*   `withTemperature(Double)`: Set creativity/randomness (0.0-1.0)

*   `withTopP(Double)`: Set nucleus sampling parameter

*   `withTopK(Integer)`: Set top-K sampling parameter

*   `withPersona(String)`: Add a system message persona


#### [](#promptrunner)3.8.2. PromptRunner

All LLM calls in user applications should be made via the `PromptRunner` interface. Once created, a `PromptRunner` can run multiple prompts with the same LLM, hyperparameters, tool groups and `PromptContributors`.

##### [](#getting-a-promptrunner)Getting a PromptRunner

You obtain a `PromptRunner` from an `OperationContext` using the fluent API:

```
@Action
public Story createStory(UserInput input, OperationContext context) {
    // Get PromptRunner with default LLM
    var runner = context.ai().withDefaultLlm();

    // Get PromptRunner with specific LLM options
    var customRunner = context.ai().withLlm(
        LlmOptions.withModel(OpenAiModels.GPT_4O_MINI)
            .withTemperature(0.8)
    );

    return customRunner.createObject("Write a story about: " + input.getContent(), Story.class);
}
```


##### [](#promptrunner-methods)PromptRunner Methods

**Core Object Creation:**

*   `createObject(String, Class<T>)`: Create a typed object from a prompt, otherwise throw an exception. An exception triggers retry. If retry fails repeatedly, re-planning occurs.

*   `createObjectIfPossible(String, Class<T>)`: Try to create an object, return null on failure. This can cause replanning.

*   `generateText(String)`: Generate simple text response




**Tool and Context Management:**

*   `withToolGroup(String)`: Add [tool groups](#reference.tools__tool-groups) for LLM access

*   `withToolObject(Object)`: Add domain objects with [@Tool](#reference.tools) methods

*   `withPromptContributor(PromptContributor)`: Add [context](#reference.prompt-contributors) contributors


**LLM Configuration:**

*   `withLlm(LlmOptions)`: Use specific LLM configuration

*   `withGenerateExamples(Boolean)`: Control example generation


**Returning a Specific Type**

*   `creating(Class<T>)`: Go into the `ObjectCreator` fluent API for returning a particular type.


For example:

```
var story = context.ai()
    .withDefaultLlm()
    .withToolGroup(CoreToolGroups.WEB)
    .creating(Story.class)
    .fromPrompt("Create a story about: " + input.getContent());
```


The main reason to do this is to add strongly typed examples for [few-shot prompting](https://www.promptingguide.ai/techniques/fewshot). For example:

```
var story = context.ai()
    .withDefaultLlm()
    .withToolGroup(CoreToolGroups.WEB)
    .withExample("A children's story", new Story("Once upon a time...")) (1)
    .creating(Story.class)
    .fromPrompt("Create a story about: " + input.getContent());
```




**Advanced Features:**

*   `withTemplate(String)`: Use [Jinja](#reference.templates) templates for prompts

*   `withSubagents(Subagent…​)`: Enable handoffs to other agents

*   `evaluateCondition(String, String)`: Evaluate boolean condition


### [](#reference.tools)3.9. Tools

Tools can be passed to LLMs to allow them to perform actions.

Embabel provides tools to LLMs in several ways:

*   At action or `PromptRunner` level, from a tool group or _tool instance_. A tool instance is an object with `@Tool` methods.

*   At domain object level via `@Tool` methods authored by the application developer.


#### [](#implementing-tool-instances)3.9.1. Implementing Tool Instances

Classes implementing tools can be stateful. They are often domain objects.

Return type restrictions: TODO

Method parameter restrictions: TODO

You can obtain the current `AgentProcess` in a Tool method implementation via `AgentProcess.get()`. This enables tools to bind to the `AgentProcess`, making objects available to other actions. For example:

```
@Tool(description="My Tool")
String bindCustomer(Long id) {
    var customer = customerRepository.findById(id);
    var agentProcess = AgentProcess.get();
    if (agentProcess != null) {
        agentProcess.addObject(customer);
        return "Customer bound to blackboard";
    }
    return "No agent process: Unable to bind customer";
}
```


#### [](#reference.tools__tool-groups)3.9.2. Tool Groups

Embabel introduces the concept of a **tool group**. This is a level of indirection between user intent and tool selection. For example, we don’t ask for Brave or Google web search: we ask for "web" tools, which may be resolved differently in different environments.



Tool groups are often backed by [MCP](#reference.integrations__mcp).

##### [](#configuring-tool-groups-with-spring)Configuring Tool Groups with Spring

Embabel uses Spring’s `@Configuration` and `@Bean` annotations to expose ToolGroups to the agent platform. The framework provides a default `ToolGroupsConfiguration` that demonstrates how to inject MCP servers and selectively expose MCP tools:

```
@Configuration
class ToolGroupsConfiguration(
    private val mcpSyncClients: List<McpSyncClient>,
) {

    @Bean
    fun mathToolGroup() = MathTools()

    @Bean
    fun mcpWebToolsGroup(): ToolGroup {
        return McpToolGroup(
            description = CoreToolGroups.WEB_DESCRIPTION,
            name = "docker-web",
            provider = "Docker",
            permissions = setOf(ToolGroupPermission.INTERNET_ACCESS),
            clients = mcpSyncClients,
            filter = {
                // Only expose specific web tools, exclude rate-limited ones
                (it.toolDefinition.name().contains("brave") ||
                 it.toolDefinition.name().contains("fetch")) &&
                !it.toolDefinition.name().contains("brave_local_search")
            }
        )
    }
}
```


##### [](#key-configuration-patterns)Key Configuration Patterns

**MCP Client Injection:** The configuration class receives a `List<McpSyncClient>` via constructor injection. Spring automatically provides all available MCP clients that have been configured in the application.

**Selective Tool Exposure:** Each `McpToolGroup` uses a `filter` lambda to control which tools from the MCP servers are exposed to agents. This allows fine-grained control over tool availability and prevents unwanted or problematic tools from being used.

**Tool Group Metadata:** Tool groups include descriptive metadata like `name`, `provider`, and `description` to help agents understand their capabilities. The `permissions` property declares what access the tool group requires (e.g., `INTERNET_ACCESS`).

##### [](#creating-custom-tool-group-configurations)Creating Custom Tool Group Configurations

Applications can implement their own `@Configuration` classes to expose custom tool groups:

```
@Configuration
public class MyToolGroupsConfiguration {

    @Bean
    public ToolGroup databaseToolsGroup(DataSource dataSource) {
        return new DatabaseToolGroup(dataSource);
    }

    @Bean
    public ToolGroup emailToolsGroup(EmailService emailService) {
        return new EmailToolGroup(emailService);
    }
}
```


This approach leverages Spring’s dependency injection to provide tool groups with the services and resources they need, while maintaining clean separation of concerns between tool configuration and agent logic.

##### [](#tool-usage-in-action-methods)Tool Usage in Action Methods

The `toolGroups` parameter on `@Action` methods specifies which tool groups are required for that action to execute. The framework automatically provides these tools to the LLM when the action runs.

Here’s an example from the `StarNewsFinder` agent that demonstrates web tool usage:

Java

```
// toolGroups specifies tools that are required for this action to run
@Action(toolGroups = {CoreToolGroups.WEB})
public RelevantNewsStories findNewsStories(
        StarPerson person, Horoscope horoscope, OperationContext context) {
    var prompt = """
            %s is an astrology believer with the sign %s.
            Their horoscope for today is:
                <horoscope>%s</horoscope>
            Given this, use web tools and generate search queries
            to find %d relevant news stories summarize them in a few sentences.
            Include the URL for each story.
            Do not look for another horoscope reading or return results directly about astrology;
            find stories relevant to the reading above.
            """.formatted(
            person.name(), person.sign(), horoscope.summary(), storyCount);

    return context.ai().withDefaultLlm().createObject(prompt, RelevantNewsStories.class);
}
```


Kotlin

```
// toolGroups specifies tools that are required for this action to run
@Action(toolGroups = [CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION])
internal fun findNewsStories(
    person: StarPerson,
    horoscope: Horoscope,
    context: OperationContext,
): RelevantNewsStories =
    context.ai().withDefaultLlm() createObject (
        """
        ${person.name} is an astrology believer with the sign ${person.sign}.
        Their horoscope for today is:
            <horoscope>${horoscope.summary}</horoscope>
        Given this, use web tools and generate search queries
        to find $storyCount relevant news stories summarize them in a few sentences.
        Include the URL for each story.
        Do not look for another horoscope reading or return results directly about astrology;
        find stories relevant to the reading above.
        """.trimIndent()
    )
```


##### [](#key-tool-usage-patterns)Key Tool Usage Patterns

**Tool Group Declaration:** The `toolGroups` parameter on `@Action` methods explicitly declares which tool groups the action needs. This ensures the LLM has access to the appropriate tools when executing that specific action.

**Multiple Tool Groups:** Actions can specify multiple tool groups (e.g., `[CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION]`) when they need different types of capabilities.

**Automatic Tool Provisioning:** The framework automatically makes the specified tools available to the LLM during the action execution. Developers don’t need to manually manage tool availability - they simply declare what’s needed.

**Tool-Aware Prompts:** Prompts should explicitly instruct the LLM to use the available tools. For example, "use web tools and generate search queries" clearly directs the LLM to utilize the web search capabilities.

##### [](#using-tools-at-promptrunner-level)Using Tools at PromptRunner Level

Instead of declaring tools at the action level, you can also specify tools directly on the `PromptRunner` for more granular control:

```
// Add tool groups to a specific prompt
context.promptRunner().withToolGroup(CoreToolGroups.WEB).create(
    """
    Given the topic, generate a detailed report using web research.

    # Topic
    ${reportRequest.topic}
    """.trimIndent()
)

// Add multiple tool groups
context.ai().withDefaultLlm()
    .withToolGroup(CoreToolGroups.WEB)
    .withToolGroup(CoreToolGroups.MATH)
    .createObject("Calculate stock performance with web data", StockReport::class)
```


**Adding Tool Objects with @Tool Methods:**

You can also provide domain objects with `@Tool` methods directly to specific prompts:

```
context.ai()
    .withDefaultLlm()
    .withToolObject(jokerTool)
    .createObject("Create a UserInput object for fun", UserInput.class);

// Add tool object with filtering and custom naming strategy
context.ai()
    .withDefaultLlm()
    .withToolObject(
        ToolObject(calculatorService)
            .withNamingStrategy { "calc_$it" }
            .withFilter { methodName -> methodName.startsWith("compute") }
    ).createObject("Perform calculations", Result.class);
```


**Available PromptRunner Tool Methods:**

*   `withToolGroup(String)`: Add a single tool group by name

*   `withToolGroup(ToolGroup)`: Add a specific ToolGroup instance

*   `withToolGroups(Set<String>)`: Add multiple tool groups

*   `withTools(vararg String)`: Convenient method to add multiple tool groups

*   `withToolObject(Any)`: Add domain object with @Tool methods

*   `withToolObject(ToolObject)`: Add ToolObject with custom configuration


#### [](#tool-objects)3.9.3. Tool Objects

#### [](#tools-on-domain-objects)3.9.4. Tools on Domain Objects

Important

### [](#reference.prompt-contributors)3.10. Structured Prompt Elements

Embabel provides a number of ways to structure and manage prompt content.

**Prompt contributors** are a fundamental way to structure and inject content into LLM prompts. You don’t need to use them—​you can simply build your prompts as strings—​but they can be useful to achieve consistency and reuse across multiple actions or even across multiple agents using the same domain objects.

Prompt contributors implement the `PromptContributor` interface and provide text that gets included in the final prompt sent to the LLM. By default the text will be included in the system prompt message.

#### [](#the-promptcontributor-interface-and-llmreference-subinterface)3.10.1. The `PromptContributor` Interface and `LlmReference` Subinterface

All prompt contributors implement the `PromptContributor` interface with a `contribution()` method that returns a string to be included in the prompt.

Add `PromptContributor` instances to a `PromptRunner` using the `withPromptContributor()` method.

A subinterface of `PromptContributor` is `LlmReference`.

An `LlmReference` is a prompt contributor that can also provide tools via annotated `@Tool` methods. So that tool naming can be disambiguated, an `LlmReference` must also include name and description metadata.

Add `LlmReference` instances to a `PromptRunner` using the `withReference()` method.

Use `LlmReference` if:

*   You want to provide both prompt content and tools from the same object

*   You want to provide specific instructions on how to use these tools, beyond the individual tool descriptions

*   Your data may be best exposed as either prompt content or tools, depending on the context. For example, if you have a list of 10 items you might just put in the prompt and say "Here are all the items: …​". If you have a list of 10,000 objects, you would include advice to use the tools to query them.


#### [](#built-in-convenience-classes)3.10.2. Built-in Convenience Classes

Embabel provides several convenience classes that implement `PromptContributor` for common use cases. These are optional utilities - you can always implement the interface directly for custom needs.

##### [](#persona)Persona

The `Persona` class provides a structured way to define an AI agent’s personality and behavior:

```
val persona = Persona.create(
    name = "Alex the Analyst",
    persona = "A detail-oriented data analyst with expertise in financial markets",
    voice = "Professional yet approachable, uses clear explanations",
    objective = "Help users understand complex financial data through clear analysis"
)
```


This generates a prompt contribution like:

```
You are Alex the Analyst.
Your persona: A detail-oriented data analyst with expertise in financial markets.
Your objective is Help users understand complex financial data through clear analysis.
Your voice: Professional yet approachable, uses clear explanations.
```


##### [](#rolegoalbackstory)RoleGoalBackstory

The `RoleGoalBackstory` class follows the Crew AI pattern and is included for users migrating from that framework:

```
var agent = RoleGoalBackstory.withRole("Senior Software Engineer")
    .andGoal("Write clean, maintainable code")
    .andBackstory("10+ years experience in enterprise software development")
```


This generates:

```
Role: Senior Software Engineer
Goal: Write clean, maintainable code
Backstory: 10+ years experience in enterprise software development
```


#### [](#custom-promptcontributor-implementations)3.10.3. Custom PromptContributor Implementations

You can create custom prompt contributors by implementing the interface directly. This gives you complete control over the prompt content:

```
class CustomSystemPrompt(private val systemName: String) : PromptContributor {
    override fun contribution(): String {
        return "System: $systemName - Current time: ${LocalDateTime.now()}"
    }
}

class ConditionalPrompt(
    private val condition: () -> Boolean,
    private val trueContent: String,
    private val falseContent: String
) : PromptContributor {
    override fun contribution(): String {
        return if (condition()) trueContent else falseContent
    }
}
```


#### [](#examples-from-embabel-agent-examples)3.10.4. Examples from embabel-agent-examples

The [embabel-agent-examples](https://github.com/embabel/embabel-agent-examples) repository demonstrates various agent development patterns and Spring Boot integration approaches for building AI agents with Embabel.

#### [](#best-practices)3.10.5. Best Practices

*   Keep prompt contributors focused and single-purpose

*   Use the convenience classes (`Persona`, `RoleGoalBackstory`) when they fit your needs

*   Implement custom `PromptContributor` classes for domain-specific requirements

*   Consider using dynamic contributors for context-dependent content

*   Test your prompt contributions to ensure they produce the desired LLM behavior


### [](#templates)3.11. Templates

Embabel supports Jinja templates for generating prompts. You do this via the `PromptRunner.withTemplate(String)` method.

This method takes a Spring resource path to a Jinja template. The default location is under `classpath:/prompts/` and the `.jinja` extension is added automatically.

Once you have specified the template, you can create objects using a model map.

An example:

```
val distinctFactualAssertions = context.ai()
    .withLlm(properties.deduplicationLlm())
    // Jinjava template from classpath at prompts/factchecker/consolidate_assertions.jinja
    .withTemplate("factchecker/consolidate_assertions")
    .createObject(
            DistinctFactualAssertions.class,
            Map.of(
                    "assertions", allAssertions,
                    "reasoningWordCount", properties.reasoningWordCount()
            )
    );
```




### [](#reference.agent-process)3.12. The AgentProcess

An `AgentProcess` is created every time an agent is run. It has a unique id.

### [](#processoptions)3.13. ProcessOptions

Agent processes can be configured with `ProcessOptions`.

`ProcessOptions` controls:

*   `contextId`: An identifier of any existing context in which the agent is running.

*   `blackboard`: The blackboard to use for the agent. Allows starting from a particular state.

*   `test`: Whether the agent is running in test mode.

*   `verbosity`: The verbosity level of the agent. Allows fine grained control over logging prompts, LLM returns and detailed planning information

*   `control`: Control options, determining whether the agent should be terminated as a last resort. `EarlyTerminationPolicy` can based on an absolute number of actions or a maximum budget.

*   Delays: Both operations (actions) and tools can have delays. This is useful to avoid rate limiting.


### [](#reference.agent-platform)3.14. The AgentPlatform

An `AgentPlatform` provides the ability to run agents in a specific environment. This is an SPI interface, so multiple implementations are possible.

### [](#reference.invoking)3.15. Invoking Embabel Agents

While many examples show Embabel agents being invoked via `UserInput` through the Embabel shell, they can also be invoked programmatically with strong typing.

This is usually how they’re used in web applications. It is also the most deterministic approach as code, rather than LLM assessment of user input, determines which agent is invoked and how.

#### [](#creating-an-agentprocess-programmatically)3.15.1. Creating an AgentProcess Programmatically

You can create and execute agent processes directly using the `AgentPlatform`:

```
// Create an agent process with bindings
val agentProcess = agentPlatform.createAgentProcess(
    agent = myAgent,
    processOptions = ProcessOptions(),
    bindings = mapOf("input" to userRequest)
)

// Start the process and wait for completion
val result = agentPlatform.start(agentProcess).get()

// Or run synchronously
val completedProcess = agentProcess.run()
val result = completedProcess.last<MyResultType>()
```


You can create processes and populate their input map from varargs objects:

```
// Create process from objects (like in web controllers)
val agentProcess = agentPlatform.createAgentProcessFrom(
    agent = travelAgent,
    processOptions = ProcessOptions(),
    travelRequest,
    userPreferences
)
```


#### [](#using-agentinvocation)3.15.2. Using AgentInvocation

`AgentInvocation` provides a higher-level, type-safe API for invoking agents. It automatically finds the appropriate agent based on the expected result type.

##### [](#basic-usage)Basic Usage

Java

```
// Simple invocation with explicit result type
var invocation =
    AgentInvocation.create(agentPlatform, TravelPlan.class);

TravelPlan plan = invocation.invoke(travelRequest);
```


Kotlin

```
// Type-safe invocation with inferred result type
val invocation: AgentInvocation<TravelPlan> =
    AgentInvocation.create(agentPlatform)

val plan = invocation.invoke(travelRequest)
```


##### [](#invocation-with-named-inputs)Invocation with Named Inputs

```
// Invoke with a map of named inputs
Map<String, Object> inputs = Map.of(
    "request", travelRequest,
    "preferences", userPreferences
);

TravelPlan plan = invocation.invoke(inputs);
```


##### [](#custom-process-options)Custom Process Options

Configure verbosity, budget, and other execution options:

Java

```
var invocation =
    AgentInvocation.builder(agentPlatform)
        .options(options -> options
            .verbosity(verbosity -> verbosity
                .showPrompts(true)
                .showResponses(true)
                .debug(true)))
        .build(TravelPlan.class);

TravelPlan plan = invocation.invoke(travelRequest);
```


Kotlin

```
val processOptions = ProcessOptions(
    verbosity = Verbosity(
        showPrompts = true,
        showResponses = true,
        debug = true
    )
)

val invocation: AgentInvocation<TravelPlan> =
    AgentInvocation.builder(agentPlatform)
        .options(processOptions)
        .build()

val plan = invocation.invoke(travelRequest)
```


##### [](#asynchronous-invocation)Asynchronous Invocation

For long-running operations, use async invocation:

```
CompletableFuture<TravelPlan> future = invocation.invokeAsync(travelRequest);

// Handle result when complete
future.thenAccept(plan -> {
    logger.info("Travel plan generated: {}", plan);
});

// Or wait for completion
TravelPlan plan = future.get();
```


##### [](#agent-selection)Agent Selection

`AgentInvocation` automatically finds agents by examining their goals:

*   Searches all registered agents in the platform

*   Finds agents with goals that produce the requested result type

*   Uses the first matching agent found

*   Throws an error if no suitable agent is available


##### [](#real-world-web-application-example)Real-World Web Application Example

```
@Controller
class TripPlanningController(
    private val agentPlatform: AgentPlatform
) {

    private val activeJobs = ConcurrentHashMap<String, CompletableFuture<TripPlan>>()

    @PostMapping("/plan-trip")
    fun planTrip(
        @ModelAttribute tripRequest: TripRequest,
        model: Model
    ): String {
        // Generate unique job ID for tracking
        val jobId = UUID.randomUUID().toString()

        // Create agent invocation with custom options
        val invocation: AgentInvocation<TripPlan> = AgentInvocation.builder(agentPlatform)
            .options { options ->
                options.verbosity { verbosity ->
                    verbosity.showPrompts(true)
                        .showResponses(false)
                        .debug(false)
                }
            }
            .build()

        // Start async agent execution
        val future = invocation.invokeAsync(tripRequest)
        activeJobs[jobId] = future

        // Set up completion handler
        future.whenComplete { result, throwable ->
            if (throwable != null) {
                logger.error("Trip planning failed for job $jobId", throwable)
            } else {
                logger.info("Trip planning completed for job $jobId")
            }
        }

        model.addAttribute("jobId", jobId)
        model.addAttribute("tripRequest", tripRequest)

        // Return htmx template that will poll for results
        return "trip-planning-progress"
    }

    @GetMapping("/trip-status/{jobId}")
    @ResponseBody
    fun getTripStatus(@PathVariable jobId: String): ResponseEntity<Map<String, Any>> {
        val future = activeJobs[jobId]
            ?: return ResponseEntity.notFound().build()

        return when {
            future.isDone -> {
                try {
                    val tripPlan = future.get()
                    activeJobs.remove(jobId)

                    ResponseEntity.ok(mapOf(
                        "status" to "completed",
                        "result" to tripPlan,
                        "redirect" to "/trip-result/$jobId"
                    ))
                } catch (e: Exception) {
                    activeJobs.remove(jobId)
                    ResponseEntity.ok(mapOf(
                        "status" to "failed",
                        "error" to e.message
                    ))
                }
            }
            future.isCancelled -> {
                activeJobs.remove(jobId)
                ResponseEntity.ok(mapOf("status" to "cancelled"))
            }
            else -> {
                ResponseEntity.ok(mapOf(
                    "status" to "in_progress",
                    "message" to "Planning your amazing trip..."
                ))
            }
        }
    }

    @GetMapping("/trip-result/{jobId}")
    fun showTripResult(
        @PathVariable jobId: String,
        model: Model
    ): String {
        // Retrieve completed result from cache or database
        val tripPlan = tripResultCache[jobId]
            ?: return "redirect:/error"

        model.addAttribute("tripPlan", tripPlan)
        return "trip-result"
    }

    @DeleteMapping("/cancel-trip/{jobId}")
    @ResponseBody
    fun cancelTrip(@PathVariable jobId: String): ResponseEntity<Map<String, String>> {
        val future = activeJobs[jobId]

        return if (future != null && !future.isDone) {
            future.cancel(true)
            activeJobs.remove(jobId)
            ResponseEntity.ok(mapOf("status" to "cancelled"))
        } else {
            ResponseEntity.badRequest()
                .body(mapOf("error" to "Job not found or already completed"))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TripPlanningController::class.java)
        private val tripResultCache = ConcurrentHashMap<String, TripPlan>()
    }
}
```


**Key Patterns:**

*   **Async Execution**: Uses `invokeAsync()` to avoid blocking the web request

*   **Job Tracking**: Maintains a map of active futures for status polling

*   **htmx Integration**: Returns status updates that htmx can consume for UI updates

*   **Error Handling**: Proper exception handling and user feedback

*   **Resource Cleanup**: Removes completed jobs from memory

*   **Process Options**: Configures verbosity and debugging for production use




### [](#reference.api-spi)3.16. API vs SPI

Embabel makes a clean distinction between its API and SPI. The API is the public interface that users interact with, while the SPI (Service Provider Interface) is intended for developers who want to extend or customize the behavior of Embabel, or platform providers.



### [](#reference.spring)3.17. Embabel and Spring

Embabel embraces [Spring](https://spring.io/projects/spring-framework). Spring was revolutionary when it arrived, and two decades on it still defines how most JVM applications are built. You may already know Spring from years of Java or Kotlin development. Or perhaps you’re arriving from Python or another ecosystem. In any case it’s worth noting that Embabel was spearheaded by the creator of Spring himself: the noteworthy Rod Johnson.

Embabel has been assembled using the Spring core platform and then builds upon the [Spring AI](https://spring.io/projects/spring-ai) portfolio project.

We recommend using [Spring Boot](https://spring.io/projects/spring-boot) for building Embabel applications. Not only does it provide a familiar environment for JVM developers, its philosophy is highly relevant for anyone aiming to craft a production-grade agentic AI application.

Why? Because the foundation of the Spring framework is:

*   Composability via discreet, fit-for-purpose reusable units. Dependency injection facilitates this.

*   Cross-cutting abstractions — such as transaction management and security. Aspect-oriented programming (AOP) is what makes this work.


This same foundation makes it possible to craft agentic applications that are composable, testable, and built on enterprise-grade service abstractions. With ~70% of production applications deployed on the JVM, Embabel can bring AI super-powers to existing systems — extending their value rather than replacing them. In this way, Embabel applies the Spring philosophy so that agentic applications are not just clever demos, but truly production-ready systems.

### [](#reference.llms)3.18. Working with LLMs

Embabel supports any LLM supported by Spring AI. In practice, this is just about any LLM.

#### [](#choosing-an-llm)3.18.1. Choosing an LLM

Embabel encourages you to think about LLM choice for every LLM invocation. The `PromptRunner` interface makes this easy. Because Embabel enables you to break agentic flows up into multiple action steps, each step can use a smaller, focused prompt with fewer tools. This means it may be able to use a smaller LLM.

Considerations:

*   **Consider the complexity of the return type you expect** from the LLM. This is typically a good proxy for determining required LLM quality. A small LLM is likely to struggle with a deeply nested return structure.

*   **Consider the nature of the task.** LLMs have different strengths; review any available documentation. You don’t necessarily need a huge, expensive model that is good at nearly everything, at the cost of your wallet and the environment.

*   **Consider the sophistication of tool calling required**. Simple tool calls are fine, but complex orchestration is another indicator you’ll need a strong LLM. (It may also be an indication that you should create a more sophisticated flow using Embabel GOAP.)

*   **Consider trying a local LLM** running under Ollama or Docker.




### [](#reference.customizing)3.19. Customizing Embabel

#### [](#adding-llms)3.19.1. Adding LLMs

You can add custom LLMs as Spring beans of type `Llm`.

Llms are created around Spring AI `ChatModel` instances.

A common requirement is to add an open AI compatible LLM. This can be done by extending the `OpenAiCompatibleModelFactory` class as follows:

```
@Configuration
class CustomOpenAiCompatibleModels(
    @Value("\${MY_BASE_URL:#{null}}")
    baseUrl: String?,
    @Value("\${MY_API_KEY}")
    apiKey: String,
    observationRegistry: ObservationRegistry,
) : OpenAiCompatibleModelFactory(baseUrl = baseUrl, apiKey = apiKey, observationRegistry = observationRegistry) {

    @Bean
    fun myGreatModel(): Llm {
        // Call superclass method
        return openAiCompatibleLlm(
            model = "my-great-model",
            provider = "me",
            knowledgeCutoffDate = LocalDate.of(2025, 1, 1),
            pricingModel = PerTokenPricingModel(
                usdPer1mInputTokens = .40,
                usdPer1mOutputTokens = 1.6,
            )
        )
    }
}
```


#### [](#adding-embedding-models)3.19.2. Adding embedding models

Embedding models can also be added as beans of type `EmbeddingService`.

#### [](#configuration-via-application-properties-or-application-yml)3.19.3. Configuration via `application.properties` or `application.yml`

You can specify Spring configuration, your own configuration and Embabel configuration in the regular Spring configuration files. Profile usage will work as expected.

#### [](#customizing-logging)3.19.4. Customizing logging

You can customize logging as in any Spring application.

For example, in `application.properties` you can set properties like:

```
logging.level.com.embabel.agent.a2a=DEBUG
```


### [](#reference.integrations)3.20. Integrations

#### [](#reference.integrations__mcp)3.20.1. Model Context Protocol (MCP)

##### [](#consuming)Consuming

##### [](#publishing)Publishing

#### [](#reference.integrations__a2a)3.20.2. A2A

### [](#reference.testing)3.21. Testing

Like Spring, Embabel facilitates testing of user applications. The framework provides comprehensive testing support for both unit and integration testing scenarios.



#### [](#unit-testing)3.21.1. Unit Testing

Unit testing in Embabel enables testing individual agent actions without involving real LLM calls.

Embabel’s design means that agents are usually POJOs that can be instantiated with fake or mock objects. Actions are methods that can be called directly with test fixtures. In additional to your domain objects, you will pass a text fixture for the Embabel `OperationContext`, enabling you to intercept and verify LLM calls.

The framework provides `FakePromptRunner` and `FakeOperationContext` to mock LLM interactions while allowing you to verify prompts, hyperparameters, and business logic. Alternatively you can use mock objects. [Mockito](https://site.mockito.org/) is the default choice for Java; [mockk](https://mockk.io/) for Kotlin.

##### [](#java-example-testing-prompts-and-hyperparameters)Java Example: Testing Prompts and Hyperparameters

Here’s a unit test from the [Java Agent Template](http://github.com/embabel/java-agent-template) repository, using Embabel fake objects:

```
class WriteAndReviewAgentTest {

    @Test
    void testWriteAndReviewAgent() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(new Story("One upon a time Sir Galahad . . "));

        var agent = new WriteAndReviewAgent(200, 400);
        agent.craftStory(new UserInput("Tell me a story about a brave knight", Instant.now()), context);

        String prompt = promptRunner.getLlmInvocations().getFirst().getPrompt();
        assertTrue(prompt.contains("knight"), "Expected prompt to contain 'knight'");

        var temp = promptRunner.getLlmInvocations().getFirst().getInteraction().getLlm().getTemperature();
        assertEquals(0.9, temp, 0.01,
                "Expected temperature to be 0.9: Higher for more creative output");
    }

    @Test
    void testReview() {
        var agent = new WriteAndReviewAgent(200, 400);
        var userInput = new UserInput("Tell me a story about a brave knight", Instant.now());
        var story = new Story("Once upon a time, Sir Galahad...");
        var context = FakeOperationContext.create();
        context.expectResponse("A thrilling tale of bravery and adventure!");
        agent.reviewStory(userInput, story, context);

        var promptRunner = (FakePromptRunner) context.promptRunner();
        String prompt = promptRunner.getLlmInvocations().getFirst().getPrompt();
        assertTrue(prompt.contains("knight"), "Expected review prompt to contain 'knight'");
        assertTrue(prompt.contains("review"), "Expected review prompt to contain 'review'");
    }
}
```


##### [](#kotlin-example-testing-prompts-and-hyperparameters)Kotlin Example: Testing Prompts and Hyperparameters

```
/**
 * Unit tests for the WriteAndReviewAgent class.
 * Tests the agent's ability to craft and review stories based on user input.
 */
internal class WriteAndReviewAgentTest {

    /**
     * Tests the story crafting functionality of the WriteAndReviewAgent.
     * Verifies that the LLM call contains expected content and configuration.
     */
    @Test
    fun testCraftStory() {
        // Create agent with word limits: 200 min, 400 max
        val agent = WriteAndReviewAgent(200, 400)
        val context = FakeOperationContext.create()
        val promptRunner = context.promptRunner() as FakePromptRunner

        context.expectResponse(Story("One upon a time Sir Galahad . . "))

        agent.craftStory(
            UserInput("Tell me a story about a brave knight", Instant.now()),
            context
        )

        // Verify the prompt contains the expected keyword
        Assertions.assertTrue(
            promptRunner.llmInvocations.first().prompt.contains("knight"),
            "Expected prompt to contain 'knight'"
        )

        // Verify the temperature setting for creative output
        val actual = promptRunner.llmInvocations.first().interaction.llm.temperature
        Assertions.assertEquals(
            0.9, actual, 0.01,
            "Expected temperature to be 0.9: Higher for more creative output"
        )
    }

    @Test
    fun testReview() {
        val agent = WriteAndReviewAgent(200, 400)
        val userInput = UserInput("Tell me a story about a brave knight", Instant.now())
        val story = Story("Once upon a time, Sir Galahad...")
        val context = FakeOperationContext.create()

        context.expectResponse("A thrilling tale of bravery and adventure!")
        agent.reviewStory(userInput, story, context)

        val promptRunner = context.promptRunner() as FakePromptRunner
        val prompt = promptRunner.llmInvocations.first().prompt
        Assertions.assertTrue(prompt.contains("knight"), "Expected review prompt to contain 'knight'")
        Assertions.assertTrue(prompt.contains("review"), "Expected review prompt to contain 'review'")

        // Verify single LLM invocation during review
        Assertions.assertEquals(1, promptRunner.llmInvocations.size)
    }
}
```


##### [](#key-testing-patterns-demonstrated)Key Testing Patterns Demonstrated

**Testing Prompt Content:**

*   Use `context.getLlmInvocations().getFirst().getPrompt()` to get the actual prompt sent to the LLM

*   Verify that key domain data is properly included in the prompt using `assertTrue(prompt.contains(…​))`


**Testing Tool Group Configuration:**

*   Access tool groups via `getInteraction().getToolGroups()`

*   Verify expected tool groups are present or absent as required


**Testing with Spring Dependencies:**

*   Mock Spring-injected services like `HoroscopeService` using standard mocking frameworks - Pass mocked dependencies to agent constructor for isolated unit testing


##### [](#testing-multiple-llm-interactions)Testing Multiple LLM Interactions

```
@Test
void shouldHandleMultipleLlmInteractions() {
    // Arrange
    var input = new UserInput("Write about space exploration");
    var story = new Story("The astronaut gazed at Earth...");
    ReviewedStory review = new ReviewedStory("Compelling narrative with vivid imagery.");

    // Set up expected responses in order
    context.expectResponse(story);
    context.expectResponse(review);

    // Act
    var writtenStory = agent.writeStory(input, context);
    ReviewedStory reviewedStory = agent.reviewStory(writtenStory, context);

    // Assert
    assertEquals(story, writtenStory);
    assertEquals(review, reviewedStory);

    // Verify both LLM calls were made
    List<LlmInvocation> invocations = context.getLlmInvocations();
    assertEquals(2, invocations.size());

    // Verify first call (writer)
    var writerCall = invocations.get(0);
    assertEquals(0.8, writerCall.getInteraction().getLlm().getTemperature(), 0.01);

    // Verify second call (reviewer)
    var reviewerCall = invocations.get(1);
    assertEquals(0.2, reviewerCall.getInteraction().getLlm().getTemperature(), 0.01);
}
```


You can also use Mockito or mockk directory. Consider this component, using direct injection of `Ai`:

```
@Component
public record InjectedComponent(Ai ai) {

    public record Joke(String leadup, String punchline) {
    }

    public String tellJokeAbout(String topic) {
        return ai
                .withDefaultLlm()
                .generateText("Tell me a joke about " + topic);
    }
}
```


A unit test using Mockito to verify prompt and hyperparameters:

```
class InjectedComponentTest {

    @Test
    void testTellJokeAbout() {
        var mockAi = Mockito.mock(Ai.class);
        var mockPromptRunner = Mockito.mock(PromptRunner.class);

        var prompt = "Tell me a joke about frogs";
        // Yep, an LLM came up with this joke.
        var terribleJoke = """
                Why don't frogs ever pay for drinks?
                Because they always have a tadpole in their wallet!
                """;
        when(mockAi.withDefaultLlm()).thenReturn(mockPromptRunner);
        when(mockPromptRunner.generateText(prompt)).thenReturn(terribleJoke);

        var injectedComponent = new InjectedComponent(mockAi);
        var joke = injectedComponent.tellJokeAbout("frogs");

        assertEquals(terribleJoke, joke);
        Mockito.verify(mockAi).withDefaultLlm();
        Mockito.verify(mockPromptRunner).generateText(prompt);
    }

}
```


#### [](#integration-testing)3.21.2. Integration Testing

Integration testing exercises complete agent workflows with real or mock external services while still avoiding actual LLM calls for predictability and speed.

This can ensure:

*   Agents are picked up by the agent platform

*   Data flow is correct within agents

*   Failure scenarios are handled gracefully

*   Agents interact correctly with each other and external systems

*   The overall workflow behaves as expected

*   LLM prompts and hyperparameters are correctly configured


##### [](#using-embabelmockitointegrationtest)Using EmbabelMockitoIntegrationTest

Embabel provides `EmbabelMockitoIntegrationTest` as a base class that simplifies integration testing with convenient helper methods:

```
/**
* Use framework superclass to test the complete workflow of writing and reviewing a story.
* This will run under Spring Boot against an AgentPlatform instance * that has loaded all our agents.
*/ class StoryWriterIntegrationTest extends EmbabelMockitoIntegrationTest {

    @Test
    void shouldExecuteCompleteWorkflow() {
        var input = new UserInput("Write about artificial intelligence");

        var story = new Story("AI will transform our world...");
        var reviewedStory = new ReviewedStory(story, "Excellent exploration of AI themes.", Personas.REVIEWER);

        whenCreateObject(contains("Craft a short story"), Story.class)
                .thenReturn(story);

        // The second call uses generateText
        whenGenerateText(contains("You will be given a short story to review"))
                .thenReturn(reviewedStory.review());

        var invocation = AgentInvocation.create(agentPlatform, ReviewedStory.class);
        var reviewedStoryResult = invocation.invoke(input);

        assertNotNull(reviewedStoryResult);
        assertTrue(reviewedStoryResult.getContent().contains(story.text()),
                "Expected story content to be present: " + reviewedStoryResult.getContent());
        assertEquals(reviewedStory, reviewedStoryResult,
                "Expected review to match: " + reviewedStoryResult);

        verifyCreateObjectMatching(prompt -> prompt.contains("Craft a short story"), Story.class,
                llm -> llm.getLlm().getTemperature() == 0.9 && llm.getToolGroups().isEmpty());
        verifyGenerateTextMatching(prompt -> prompt.contains("You will be given a short story to review"));
        verifyNoMoreInteractions();
    }
}
```


##### [](#key-integration-testing-features)Key Integration Testing Features

**Base Class Benefits:** - `EmbabelMockitoIntegrationTest` handles Spring Boot setup and LLM mocking automatically - Provides `agentPlatform` and `llmOperations` pre-configured - Includes helper methods for common testing patterns

**Convenient Stubbing Methods:** - `whenCreateObject(prompt, outputClass)`: Mock object creation calls - `whenGenerateText(prompt)`: Mock text generation calls - Support for both exact prompts and `contains()` matching

**Advanced Verification:** - `verifyCreateObjectMatching()`: Verify prompts with custom matchers - `verifyGenerateTextMatching()`: Verify text generation calls - `verifyNoMoreInteractions()`: Ensure no unexpected LLM calls

**LLM Configuration Testing:** - Verify temperature settings: `llm.getLlm().getTemperature() == 0.9` - Check tool groups: `llm.getToolGroups().isEmpty()` - Validate persona and other LLM options

### [](#reference.architecture)3.22. Embabel Architecture

### [](#reference.troubleshooting)3.23. Troubleshooting

This section covers common issues you might encounter when developing with Embabel and provides practical solutions.

#### [](#common-problems-and-solutions)3.23.1. Common Problems and Solutions



* Problem: Compilation Error
    * Solution: Check that you’re using the correct version of Embabel in your Maven or Gradle dependencies. You may be using an API from a later version (even a snapshot). Version mismatches between different Embabel modules can cause compilation issues. Ensure all com.embabel.agent artifacts use the same version, unless you’re following a specific example that does otherwise.
    * Related Docs: Configuration
* Problem: Don’t Know How to Invoke Your Agent
    * Solution: Look at examples of processing UserInput in the documentation. Study AgentInvocation patterns to understand how to trigger your agent flows. The key is understanding how to provide the initial input that your agent expects.
    * Related Docs: Invoking Agents
* Problem: Agent Flow Not Completing
    * Solution: This usually indicates a data flow problem. First, understand Embabel’s type-driven data flow concepts - review how input/output types create dependencies between actions. Then write an integration test to verify your flow works end-to-end. Familiarize yourself with Embabel’s GOAP planning concept.
    * Related Docs: Data Flow Concepts
* Problem: LLM Prompts Look Wrong or Have Incorrect Hyperparameters
    * Solution: Write unit tests to capture and verify the exact prompts being sent to your LLM. This allows you to see the actual prompt content and tune temperature, model selection, and other parameters. Unit testing is the best way to debug LLM interactions.
    * Related Docs: Testing
* Problem: Agent Gets Stuck in Planning
    * Solution: Check that all your actions have clear input/output type signatures. Missing or circular dependencies in your type flow can prevent the planner from finding a valid path to the goal. Review your @Action method signatures. Look at the log output from the planner for clues. Set your ProcessOptions.verbosity to show planning.
    * Related Docs: Type-Driven Flow
* Problem: Tools Not Available to Agent
    * Solution: Ensure you’ve specified the correct toolGroups in your @Action annotation. Tools must be explicitly declared for the action to access them. Check that required tool groups are available in your environment.
    * Related Docs: Tools
* Problem: Agent Runs But Produces Poor Results
    * Solution: Review your prompt engineering and persona configuration. Consider adjusting LLM temperature, model selection, and context provided to actions. Write tests to capture actual vs expected outputs.
    * Related Docs: Testing, LLM Configuration
* Problem: You’re Struggling to Express What You Want in a Plan
    * Solution: Familiarize yourself with custom conditions for complex flow control. For common behavior patterns, consider using atomic actions with Embabel’s typesafe custom builders such as ScatterGatherBuilder and RepeatUntilBuilder instead of trying to express everything through individual actions.
    * Related Docs: DSL and Builders
* Problem: Your Agent Has No Goals and Cannot Execute
    * Solution: Look at the @AchievesGoal annotation and ensure your terminal action is annotated with it. Every agent needs at least one action marked with @AchievesGoal to define what constitutes completion of the agent’s work.
    * Related Docs: Annotations
* Problem: Your Agent Isn’t Visible to an MCP Client Like Claude Desktop
    * Solution: Ensure that your @AchievesGoal annotation includes @Export(remote=true). This makes your agent available for remote invocation through MCP (Model Context Protocol) clients.
    * Related Docs: Annotations, Integrations
* Problem: Your Agent Can’t Use Upstream MCP Tools and You’re Seeing Errors in Logs About Possible Misconfiguration
    * Solution: Check that your Docker configuration is correct if using the default Docker MCP Gateway. Verify that Docker containers are running and accessible. For other MCP configurations, ensure your Spring AI MCP client configuration is correct. See the Spring AI MCP client documentation for detailed setup instructions.
    * Related Docs: Spring AI MCP Client


#### [](#debugging-strategies)3.23.2. Debugging Strategies

##### [](#enable-debug-logging)Enable Debug Logging

Customize Embabel logging in `application.yml` or `application.properties` to see detailed agent execution. For example:

```
logging:
  level:
    com.embabel.agent: DEBUG
```


#### [](#getting-help)3.23.3. Getting Help

The Embabel community is active and helpful. Join our [Discord](https://discord.gg/t6bjkyj93q) server to ask questions and share experiences.

### [](#reference.migrating)3.24. Migrating from other frameworks

Many people start their journey with Python frameworks.

This section covers how to migrate from popular frameworks when it’s time to use a more robust and secure platform with access to existing code and services.

#### [](#migrating-from-crewai)3.24.1. Migrating from CrewAI

CrewAI uses a collaborative multi-agent approach where agents work together on tasks. Embabel provides similar capabilities with stronger type safety and better integration with existing Java/Kotlin codebases.

##### [](#core-concept-mapping)Core Concept Mapping



* CrewAI Concept: Agent Role/Goal/Backstory
    * Embabel Equivalent: RoleGoalBackstory PromptContributor
    * Notes: Convenience class for agent personality
* CrewAI Concept: Sequential Tasks
    * Embabel Equivalent: Typed data flow between actions
    * Notes: Type-driven execution with automatic planning
* CrewAI Concept: Crew (Multi-agent coordination)
    * Embabel Equivalent: Actions with shared PromptContributors
    * Notes: Agents can adopt personalities as needed
* CrewAI Concept: YAML Configuration
    * Embabel Equivalent: Standard Spring @ConfigurationProperties backed by application.yml or profile-specific configuration files
    * Notes: Type-safe configuration with validation


##### [](#migration-example)Migration Example

**CrewAI Pattern:**

```
research_agent = Agent(
    role='Research Specialist',
    goal='Find comprehensive information',
    backstory='Expert researcher with 10+ years experience'
)

writer_agent = Agent(
    role='Content Writer',
    goal='Create engaging content',
    backstory='Professional writer specializing in technical content'
)

crew = Crew(
    agents=[research_agent, writer_agent],
    tasks=[research_task, write_task],
    process=Process.sequential
)
```


**Embabel Equivalent:**

```
@ConfigurationProperties("examples.book-writer")
record BookWriterConfig(
    LlmOptions researcherLlm,
    LlmOptions writerLlm,
    RoleGoalBackstory researcher,
    RoleGoalBackstory writer
) {}

@Agent(description = "Write a book by researching, outlining, and writing chapters")
public record BookWriter(BookWriterConfig config) {

    @Action
    ResearchReport researchTopic(BookRequest request, OperationContext context) {
        return context.ai()
            .withLlm(config.researcherLlm())
            .withPromptElements(config.researcher(), request)
            .withToolGroup(CoreToolGroups.WEB)
            .createObject("Research the topic thoroughly...", ResearchReport.class);
    }

    @Action
    BookOutline createOutline(BookRequest request, ResearchReport research, OperationContext context) {
        return context.ai()
            .withLlm(config.writerLlm())
            .withPromptElements(config.writer(), request, research)
            .createObject("Create a book outline...", BookOutline.class);
    }

    @AchievesGoal(export = @Export(remote = true))
    @Action
    Book writeBook(BookRequest request, BookOutline outline, OperationContext context) {
        // Parallel chapter writing with crew-like coordination
        var chapters = context.parallelMap(outline.chapterOutlines(),
            config.maxConcurrency(),
            chapterOutline -> writeChapter(request, outline, chapterOutline, context));
        return new Book(request, outline.title(), chapters);
    }
}
```


**Key Advantages:**

*   **Type Safety**: Compile-time validation of data flow

*   **Spring Integration**: Leverage existing enterprise infrastructure

*   **Automatic Planning**: GOAP planner handles task sequencing, and is capable of more sophisticated planning

*   **Tool Integration with the JVM**: Native access to existing Java/Kotlin services


#### [](#migrating-from-pydantic-ai)3.24.2. Migrating from Pydantic AI

Pydantic AI provides a Python framework for building AI agents with type safety and validation. Embabel offers similar capabilities in the JVM ecosystem with stronger integration into enterprise environments.

##### [](#core-concept-mapping-2)Core Concept Mapping



* Pydantic AI Concept: @system_prompt decorator
    * Embabel Equivalent: PromptContributor classes
    * Notes: More flexible and composable prompt management
* Pydantic AI Concept: @tool decorator
    * Embabel Equivalent: Equivalent @Tool annotated methods can be included on agent classes and domain objects
    * Notes: Agent class
* Pydantic AI Concept: @Agent annotated record/class
    * Embabel Equivalent: Declarative agent definition with Spring integration
    * Notes: RunContext
* Pydantic AI Concept: Blackboard state, accessible via OperationContext but normally not a concern for user code
    * Embabel Equivalent: SystemPrompt
    * Notes: Custom PromptContributor
* Pydantic AI Concept: Structured prompt contribution system
    * Embabel Equivalent: deps parameter
    * Notes: Spring dependency injection


##### [](#migration-example-2)Migration Example

**Pydantic AI Pattern:**

```
# Based on https://ai.pydantic.dev/examples/bank-support/
from pydantic_ai import Agent, RunContext
from pydantic_ai.tools import tool

@system_prompt
def support_prompt() -> str:
    return "You are a support agent in our bank"

@tool
async def get_customer_balance(customer_id: int, include_pending: bool = False) -> float:
    # Database lookup
    customer = find_customer(customer_id)
    return customer.balance + (customer.pending if include_pending else 0)

agent = Agent(
    'openai:gpt-4-mini',
    system_prompt=support_prompt,
    tools=[get_customer_balance],
)

result = agent.run("What's my balance?", deps={'customer_id': 123})
```


**Embabel Equivalent:**

```
// From embabel-agent-examples/examples-java/src/main/java/com/embabel/example/pydantic/banksupport/SupportAgent.java

record Customer(Long id, String name, float balance, float pendingAmount) {

    @Tool(description = "Find the balance of a customer by id")
    float balance(boolean includePending) {
        return includePending ? balance + pendingAmount : balance;
    }
}

record SupportInput(
    @JsonPropertyDescription("Customer ID") Long customerId,
    @JsonPropertyDescription("Query from the customer") String query) {
}

record SupportOutput(
    @JsonPropertyDescription("Advice returned to the customer") String advice,
    @JsonPropertyDescription("Whether to block their card or not") boolean blockCard,
    @JsonPropertyDescription("Risk level of query") int risk) {
}

@Agent(description = "Customer support agent")
record SupportAgent(CustomerRepository customerRepository) {

    @AchievesGoal(description = "Help bank customer with their query")
    @Action
    SupportOutput supportCustomer(SupportInput supportInput, OperationContext context) {
        var customer = customerRepository.findById(supportInput.customerId());
        if (customer == null) {
            return new SupportOutput("Customer not found with this id", false, 0);
        }
        return context.ai()
            .withLlm(OpenAiModels.GPT_41_MINI)
            .withToolObject(customer)
            .createObject(
                """
                You are a support agent in our bank, give the
                customer support and judge the risk level of their query.
                In some cases, you may need to block their card. In this case, explain why.
                Reply using the customer's name, "%s".
                Currencies are in $.

                Their query: [%s]
                """.formatted(customer.name(), supportInput.query()),
                SupportOutput.class);
    }
}
```


**Key Advantages:**

*   **Enterprise Integration**: Native Spring Boot integration with existing services

*   **Compile-time Safety**: Strong typing catches errors at build time

*   **Automatic Planning**: GOAP planner handles complex multi-step operations

*   **JVM Ecosystem**: Access to mature libraries and enterprise infrastructure


#### [](#migrating-from-langgraph)3.24.3. Migrating from LangGraph

tbd

#### [](#migrating-from-google-adk)3.24.4. Migrating from Google ADK

tbd

### [](#reference.api-evolution)3.25. API Evolution

While Embabel is still pre-GA, we strive to avoid breaking changes.

Because Embabel builds on Spring’s POJO support, framework code dependencies are localized and minimized.

The key surface area is the `Ai` and `PromptRunner` interfaces, which we will strive to avoid breaking.

For maximum stability:

*   Always use the latest stable version rather than a snapshot. Snapshots may change frequently.

*   Avoid using types under the `com.embabel.agent.experimental` package.

*   Avoid using any method or class marked with the `@ApiStatus.Experimental` or `@ApiStatus.Internal` annotations.




[](#agent-design)4\. Design Considerations
------------------------------------------

Embabel is designed to give you the ability to determine the correct balance between LLM autonomy and control from code. This section discusses the design considerations that you can use to achieve this balance.

### [](#domain-objects)4.1. Domain objects

A rich domain model helps build a good agentic system. Domain objects should not merely contain state, but also expose behavior. Avoid the [anemic domain model](https://en.wikipedia.org/wiki/Anemic_domain_model). Domain objects have multiple roles:

1.  _Ensuring type safety and toolability._ Code can access their state; prompts will be strongly typed; and LLMs know what to return.

2.  _Exposing behavior to call in code_, exactly as in any well-designed object-oriented system.

3.  _Exposing tools to LLMs_, allowing them to call domain objects.


The third role _is_ novel in the context of LLMs and Embabel.



Expose methods that LLMs should be able to call using the `@Tool` annotation:

```
@Tool(description = "Build the project using the given command in the root") (1)
fun build(command: String): String {
    val br = ci.buildAndParse(BuildOptions(command, true))
    return br.relevantOutput()
}
```




When an `@Action` method issues a prompt, tool methods on all domain objects are available to the LLM.

You can also add additional tool methods with the `withToolObjects` method on `PromptRunner`.

Domain objects may or may not be persistent. If persistent, they will likely be stored in a familiar JVM technology such as JPA or JDBC. We advocate the use of [Spring Data](https://spring.io/projects/spring-data) patterns and repositories, although you are free to use any persistence technology you like.

### [](#tool-call-choice)4.2. Tool Call Choice

When to use MCP or other tools versus method calls in agents

### [](#mixing-llms)4.3. Mixing LLMs

It’s good practice to use multiple LLMs in your agentic system. Embabel makes it easy. One key benefit of breaking functionality into smaller actions is that you can use different LLMs for different actions, depending on their strengths and weaknesses. You can also the cheapest (greenest) possible LLM for a given task.

[](#contributing)5\. Contributing
---------------------------------

Open source is a wonderful thing. We welcome contributions to the Embabel project.

How to contribute:

*   Familiarize yourself with the project by reading the documentation.

*   Familiarize yourself with the [issue tracker](https://github.com/embabel/embabel-agent/issues/) and open pull requests to ensure you’re not duplicating something.

*   [Sign your commits](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits)

*   Always include a description with your pull requests. **PRs without descriptions will be closed.**

*   Join the Embabel community on Discord at [discord.gg/t6bjkyj93q](https://discord.gg/t6bjkyj93q).


Contributions are not limited to code. You can also help by:

*   Improving the documentation

*   Reporting bugs

*   Suggesting new features

*   Engaging with the community on Discord

*   Creating examples and other materials

*   Talking about Embabel at meetups and conferences

*   Posting about Embabel on social media


When contributing code, **do** augment your productivity using coding agents and LLMs, but avoid these pitfalls:

*   **Excessive LLM comments that add no value**. Code should be self-documenting. Comments are for things that are non-obvious.

*   **Bloated PR descriptions and other content.**


Nothing personal, but such contributions will automatically be rejected.



[](#resources)6\. Resources
---------------------------

### [](#rod-johnsons-blog-posts)6.1. Rod Johnson’s Blog Posts

*   [Embabel: A new Agent Platform For the JVM](https://medium.com/@springrod/embabel-a-new-agent-platform-for-the-jvm-1c83402e0014) - Introduction to the Embabel agent framework, explaining the motivation for building an agent platform specifically for the JVM ecosystem. Covers the key differentiators and benefits of the approach.

*   [The Embabel Vision](https://medium.com/@springrod/the-embabel-vision-967654f13793) - Rod Johnson’s vision for the future of agent frameworks and how Embabel fits into the broader AI landscape. Discusses the long-term goals and strategic direction of the project.

*   [Context Engineering Needs Domain Understanding](https://medium.com/@springrod/context-engineering-needs-domain-understanding-b4387e8e4bf8) - Deep dive into the DICE (Domain-Integrated Context Engineering) concept and why domain understanding is fundamental to effective context engineering in AI systems.


### [](#examples-and-tutorials)6.2. Examples and Tutorials

*   [Creating an AI Agent in Java Using Embabel Agent Framework](https://www.baeldung.com/java-embabel-agent-framework) by Baeldung - A nice introductory example, in Java.

*   [Building Agents With Embabel: A Hands-On Introduction](https://jettro.dev/building-agents-with-embabel-a-hands-on-introduction-4f96d2edeac0) by Jettro Coenradie - An excellent Java tutorial.


#### [](#embabel-agent-examples-repository)6.2.1. Embabel Agent Examples Repository

The [Examples Repository](https://github.com/embabel/embabel-agent-examples) is a comprehensive collection of example agents demonstrating different aspects of the framework:

*   **Beginner Examples**: Simple horoscope agents showing basic concepts

*   **Intermediate Examples**: Multi-LLM research agents with self-improvement

*   **Advanced Examples**: Fact-checking agents with parallel verification and confidence scoring

*   **Integration Examples**: Agents that use web tools, databases, and external APIs


Perfect starting point for learning Embabel development with hands-on examples.

#### [](#java-agent-template)6.2.2. Java Agent Template

*   Pre-configured project structure

*   Example WriteAndReviewAgent demonstrating multi-LLM workflows

*   Build scripts and Docker configuration

*   Getting started documentation


#### [](#kotlin-agent-template)6.2.3. Kotlin Agent Template

[Template repository](https://github.com/embabel/kotlin-agent-template) for Kotlin-based agent development with similar features to the Java template but using idiomatic Kotlin patterns.

### [](#sophisticated-example-tripper-travel-planner)6.3. Sophisticated Example: Tripper Travel Planner

#### [](#tripper-ai-powered-travel-planning-agent)6.3.1. Tripper - AI-Powered Travel Planning Agent

[Tripper](https://github.com/embabel/tripper) is a production-quality example demonstrating advanced Embabel capabilities:

**Features:**

*   Generates personalized travel itineraries using multiple AI models

*   Integrates web search, mapping, and accommodation search

*   Modern web interface built with htmx

*   Containerized deployment with Docker

*   CI/CD pipeline with GitHub Actions


**Technical Highlights:**

*   Uses both Claude Sonnet and GPT-4.1-mini models

*   Demonstrates domain-driven design principles

*   Shows how to build user-facing applications with Embabel

*   Practical example of deterministic planning with AI


**Learning Value:**

*   Real-world application of Embabel concepts

*   Integration patterns with external services

*   Production deployment considerations

*   User interface design for AI applications


### [](#goal-oriented-action-planning-goap)6.4. Goal-Oriented Action Planning (GOAP)

*   Here’s an [Introduction to GOAP](https://medium.com/@vedantchaudhari/goal-oriented-action-planning-34035ed40d0b), the planning algorithm used by Embabel. Explains the core concepts and why GOAP is effective for AI agent planning.


#### [](#small-language-model-agents-nvidia-research)6.4.1. Small Language Model Agents - NVIDIA Research

*   This [Research paper](https://research.nvidia.com/labs/lpr/slm-agents/) discusses the division between "code agency" and "LLM agency" - concepts that inform Embabel’s architecture.


#### [](#ooda-loop-wikipedia)6.4.2. OODA Loop - Wikipedia

Here’s a [Background](https://en.wikipedia.org/wiki/OODA_loop) on the Observe-Orient-Decide-Act loop that underlies Embabel’s replanning approach.

### [](#domain-driven-design)6.5. Domain-Driven Design

*   Martin Fowler’s [Foundational concepts of Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html) provides a good summary of Embabel’s approach to domain modeling.


#### [](#domain-driven-design-tackling-complexity-in-the-heart-of-software)6.5.1. Domain-Driven Design: Tackling Complexity in the Heart of Software

*   Eric Evans' [seminal book](https://www.amazon.com/Domain-Driven-Design-Tackling-Complexity-Software/dp/0321125215) on DDD principles. Essential reading for understanding how to model complex domains effectively.


#### [](#ddd-and-contextual-validation)6.5.2. DDD and Contextual Validation

*   [Advanced DDD concepts](https://www.infoq.com/articles/ddd-contextual-validation/) relevant to building sophisticated domain models for AI agents.


[](#appendix)7\. APPENDIX
-------------------------

[](#appendix__astar-goap-planner)8\. Planning Module
----------------------------------------------------

### [](#abstract)8.1. Abstract

Lower level module for planning and scheduling. Used by Embabel Agent Platform.

### [](#a-goap-planner-algorithm-overview)8.2. A\* GOAP Planner Algorithm Overview

```
The A* GOAP (Goal-Oriented Action Planning) Planner is an implementation of the A* search
algorithm specifically designed for planning sequences of actions to achieve specified goals.
The algorithm efficiently finds the optimal path from an initial world state to a goal state by
exploring potential action sequences and minimizing overall cost.
```


#### [](#core-algorithm-components)8.2.1. Core Algorithm Components

```
The A* GOAP Planner consists of several key components:
```


1.  **A** Search\*: Finds optimal action sequences by exploring the state space

2.  **Forward Planning**: Simulates actions from the start state toward goals

3.  **Backward Planning**: Optimizes plans by working backward from goals

4.  **Plan Simulation**: Verifies that plans achieve intended goals

5.  **Pruning**: Removes irrelevant actions to create efficient plans

6.  **Unknown Condition Handling**: Manages incomplete world state information


#### [](#a-search-algorithm)8.2.2. A\* Search Algorithm

```
The A* search algorithm operates by maintaining:
```


*   **Open List**: A priority queue of states to explore, ordered by f-score

*   **Closed Set**: States already fully explored

*   **g-score**: Cost accumulated so far to reach a state

*   **h-score**: Heuristic estimate of remaining cost to goal

*   **f-score**: Total estimated cost (g-score + h-score)


#### [](#process-flow)8.2.3. Process Flow

1.  **Initialization**:

    *   Begin with the start state in the open list

    *   Set its g-score to 0 and calculate its h-score


2.  **Main Loop**:

    *   While the Open List is not empty:

        *   Select the state with the lowest f-score from the open list

        *   If this state satisfies the goal, construct and return the plan

        *   Otherwise, mark the state as processed (add to closed set)

        *   For each applicable action, generate the next state and add to open list if it better than existing paths



3.  **Path Reconstruction**: When a goal state is found, reconstruct the path by following predecessors

    *   Create a plan consisting of the sequence of actions

        ```
_Reference: link:goap/AStarGoapPlanner.kt[AStarGoapPlanner]:planToGoalFrom:_
```

        
    

#### [](#forward-and-backward-planning-optimization)8.2.4. Forward and Backward Planning Optimization

```
The planner implements a two-pass optimization strategy to eliminate unnecessary actions:
```


##### [](#backward-planning-optimization)Backward Planning Optimization

```
This pass works backward from the goal conditions to identify only actions that contribute to
achieving the goal
```


```
_Reference: link:goap/AStarGoapPlanner.kt[AStarGoapPlanner]:_backwardPlanningOptimization___
```


##### [](#forward-planning-optimization)Forward Planning Optimization

```
This pass simulates the plan from the start state and removes actions that don't make progress
toward the goal:
```


```
_Reference: link:goap/AStarGoapPlanner.kt[AStarGoapPlanner]:_forwardPlanningOptimization___
```


##### [](#plan-simulation)Plan Simulation

```
Plan simulation executes actions in sequence to verify the plan's correctness:
```


```
_Reference: function simulatePlan(startState, actions)_
```


#### [](#pruning-planning-systems)8.2.5. Pruning Planning Systems

```
The planner can prune entire planning systems to remove irrelevant actions:
```


```
function prune(planningSystem):
// Get all plans to all goals
allPlans = plansToGoals(planningSystem)
// Keep only actions that appear in at least one plan
return planningSystem.copy(
actions = planningSystem.actions.filter { action ->
allPlans.any { plan -> plan.actions.contains(action) }
}.toSet()
)
```


##### [](#heuristic-function)Heuristic Function

```
The heuristic function estimates the cost to reach the goal from a given state:
```


#### [](#complete-planning-process)8.2.6. Complete Planning Process

1.  Initialize with start state, actions, and goal conditions
    
2.  Run A\* search to find an initial action sequence
    
3.  Apply backward planning optimization to eliminate unnecessary actions
    
4.  Apply forward planning optimization to further refine the plan
    
5.  Verify the plan through simulation
    
6.  Return the optimized action sequence or null if no valid plan exists
    

### [](#agent-pruning-process)8.3. Agent Pruning Process

```
When pruning an agent for specific goals:
```


1.  Identify all known conditions in the planning system
    
2.  Set initial state based on input conditions
    
3.  Find all possible plans to each goal
    
4.  Keep only actions that appear in at least one plan
    
5.  Create a new agent with the pruned action set
    
    ```
This comprehensive approach ensures agents contain only the actions necessary to achieve their
designated goals, improving efficiency and preventing action leakage between different agents.
```



#### [](#progress-determination-logic-in-a-goap-planning)8.3.1. Progress Determination Logic in A\* GOAP Planning

```
The progress determination logic in method *forwardPlanningOptimization* is a critical part of
the forward planning optimization in the A* GOAP algorithm. This logic ensures that only actions
that meaningfully progress the state toward the goal are included in the final plan.
```


##### [](#progress-determination-expression)Progress Determination Expression

```
  progressMade = nextState != currentState &&
  action.effects.any { (key, value) ->
        goal.preconditions.containsKey(key) &&
        currentState[key] != goal.preconditions[key] &&
        (value == goal.preconditions[key] || key not in nextState)
  }
```


##### [](#detailed-explanation)Detailed Explanation

```
The expression evaluates to true only when an action makes meaningful progress toward achieving
the goal state. Let's break down each component:
```


1.  `nextState != currentState`

    *   Verifies that the action actually changes the world state

    *   Prevents including actions that have no effect


2.  `action.effects.any { …​ }`

    *   Examines each effect the action produces

    *   Returns true if ANY effect satisfies the inner condition


3.  `goal.preconditions.containsKey(key)`

    *   Ensures we only consider effects that relate to conditions required by the goal

    *   Ignores effects that modify conditions irrelevant to our goal


4.  `currentState[key] != goal.preconditions[key]`

    *   Checks that the current condition value differs from what the goal requires

    *   Only counts progress if we’re changing a condition that needs changing


5.  `(value == goal.preconditions[key] || key not in nextState)`

    *   This checks one of two possible ways an action can make progress:

    *   `value == goal.preconditions[key]`

        *   The action changes the condition to exactly match what the goal requires

        *   Direct progress toward goal achievement


    *   `key not in nextState`
        
        *   The action removes the condition from the state entirely
            
        *   This is considered progress if the condition was previously in an incorrect state
            
        *   Allows for actions that clear obstacles or reset conditions