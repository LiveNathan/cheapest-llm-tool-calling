# Cheapest LLM Tool Calling

A Spring AI benchmarking framework that tests and compares LLM providers for multi-turn tool calling with chat memory.

## TL;DR

**Winner:** Google Gemini 2.0 Flash at ~$0.0001 per multi-turn conversation with 100% reliability and 85-100% accuracy.

[Read the full blog post](https://open.substack.com/pub/nathanlively/p/i-benchmarked-9-llms-to-find-cheapest-for-multi-turn-tool-calling-with-spring-ai)

## Why This Exists

Building an AI mixing console assistant required both chat memory (conversation context) and tool calling (actual mixer control). Spring AI's documentation suggests these features conflict, with tool calls not being persisted in memory. This project benchmarks 9 LLM configurations to find which actually works reliably and cheaply.

## Key Findings

| Provider/Model                 | Cost/Run  | Success | Accuracy | Speed |
|--------------------------------|-----------|---------|----------|-------|
| google/gemini-2.0-flash        | $0.000123 | 100%    | 85-100%  | 10-23s |
| openai/gpt-4o-mini             | $0.000165 | 100%    | 64-100%  | 14-53s |
| openai/gpt-4.1-nano            | $0.000116 | 100%    | 64-100%  | 19-44s |
| deepseek/deepseek-chat         | $0.000675 | 100%    | 46-100%  | 34-76s |
| groq/llama-3.1-8b-instant      | $0.000089 | 100%    | 65-100%  | 26-73s |
| groq/llama-3.3-70b-versatile   | $0.001417 | 40-80%  | 64-100%  | 6-13s |

**Disqualified:** Mistral (system message bugs), Groq 70b (reliability issues at scale)

## Prerequisites

- Java 25
- Maven
- API Keys (set as environment variables):
  - `GROQ_API_KEY`
  - `MISTRALAI_API_KEY`
  - `DEEPSEEK_API_KEY`
  - `GEMINI_API_KEY`
  - `OPENAI_API_KEY`

## Setup

```bash
git clone https://github.com/LiveNathan/cheapest-llm-tool-calling.git
cd cheapest-llm-tool-calling
```

## Running Tests

```bash
# Run all benchmarks
./mvnw test

# Run specific test
./mvnw test -Dtest=LlmToolCallingBenchmarkTest#masterCheapestLlmBenchmark
```

## Test Scenarios

### Simple Scenario (4 prompts)
Tests basic multi-turn tool calling with memory:
1. Rename channels 1-2
2. Query what was just named (tests memory)
3. Rename based on previous names (tests memory + reasoning)
4. Read and apply prefix (tests read tool + memory)

### Complex Scenario (6 prompts)
Tests advanced operations with 14+ channel manipulations:
1. Name channels 1-7 (drum kit)
2. Add bass and guitar
3. Read and swap channels (tests read tool)
4. Add vocals on specific channels
5. Bulk prefix all drums (tests memory of what's a drum)
6. Find and rename specific channel

## Architecture

### Test Framework
- `BenchmarkRunner`: Orchestrates test execution with retries, rate limiting, and scoring
- `TestScenario`: Defines prompts, validation, and tool service
- `TestResults`: Aggregates metrics across iterations

### LLM Providers
- **Native Implementations**: DeepSeek, Google Gemini, OpenAI (use provider-specific Spring AI modules)
- **Proxy Implementations**: Groq, Mistral, DeepSeek (use OpenAI client with custom base URL)

### Scoring Algorithm
```java
reliabilityScore = successRate * 50     // 50 points max
accuracyScore = accuracy * 30           // 30 points max
speedScore = min(15, 15000 / avgTime)  // 15 points max
costScore = min(5, 0.05 / avgCost)     // 5 points max
```

## Known Issues

1. **Empty Messages**: Gemini occasionally returns empty messages. Fixed with `EmptyMessageFilterAdvisor`. [Spring AI #4556](https://github.com/spring-projects/spring-ai/issues/4556)
2. **Mistral System Messages**: Native Mistral fails with "system message in wrong place" errors. Spring AI has open issues for this.
3. **Groq Reliability**: Llama 3.3 70b has timeout and `tool_use_failed` errors at scale. Fine for single iterations, unreliable for production.

## Resources

- [Blog Post](https://open.substack.com/pub/nathanlively/p/i-benchmarked-9-llms-to-find-cheapest-for-multi-turn-tool-calling-with-spring-ai)
- [Console Whisperer Demo](https://youtu.be/Kpb2Zm6Bd8A)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Issue #2101](https://github.com/spring-projects/spring-ai/issues/2101) - Tool calls not persisted in memory

## License

MIT
