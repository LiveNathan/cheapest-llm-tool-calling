# GEMINI.md

## Project Overview

This is a Java project that demonstrates how to use Spring AI to find the cheapest cloud LLM API with tools. It uses
Spring Boot for the application framework and Maven for dependency management. The project is configured to use Groq,
Mistral, and OpenAI models. It includes a `MockWeatherService` as an example of a tool that can be called by the LLM.

## Building and Running

### Prerequisites

* Java 25
* Maven
* Groq API Key (set as an environment variable `GROQ_API_KEY`)

### Build

To build the project, run the following command:

```bash
./mvnw clean install
```

### Run

To run the application, use the following command:

```bash
./mvnw spring-boot:run
```

### Test

To run the tests, use the following command:

```bash
./mvnw test
```

## Development Conventions

* The project follows the standard Maven project structure.
* The code is formatted according to the default Java conventions.
* Tests are written using JUnit 5 and AssertJ.
* The `GroqTest.java` file shows how to use the `ChatClient` to interact with the Groq model and how to use tools.
