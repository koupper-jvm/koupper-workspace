---
name: test-gen
description: Generate Kotlin tests using JUnit5 + Kotest + Mockk for Koupper. Use when the user says "write tests", "generate tests", "add test coverage", "test this class", or points at a class or function needing tests.
argument-hint: [class, function, or module to test]
---

Generate tests for: $ARGUMENTS

**Stack:** Kotest 5.9.1 (StringSpec or BehaviorSpec), Mockk 1.13.12, JUnit5

**Rules:**
- Prefer StringSpec for unit tests; BehaviorSpec for integration/workflow tests
- Each test is fully independent (`failFast = true` applies)
- Use `shouldBe`, `shouldThrow<T>`, `coEvery`/`coVerify` for coroutines
- Mock external dependencies only; never mock the system under test
- Test class: `<Subject>Test`, package matches source

**Structure:**
```kotlin
class FooTest : StringSpec({
    "should [expected behavior] when [condition]" {
        // arrange
        val mock = mockk<Dependency>()
        every { mock.method() } returns value
        // act
        val result = sut.call()
        // assert
        result shouldBe expected
    }
})
```

**Coverage groups:**
1. Happy path (normal inputs, expected outputs)
2. Edge cases (empty, null, boundary values)
3. Error cases (invalid input, dependency failure)

Output the complete test file with correct package declaration. Nothing else.
