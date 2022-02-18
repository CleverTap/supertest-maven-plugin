# SuperTest Maven Plugin
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=CleverTap_supertest-maven-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=CleverTap_supertest-maven-plugin)

A wrapper for [Maven's Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/),
with advanced re-run capabilities.

## Goals
- Shorten the PR iteration time
- Re-run failed tests with a special profile such that they're isolated, instead of running 
  all tests optimistically again

## Motivation
Maven's Surefire is great, but lacks certain retry control mechanisms. Imagine a Maven project
with thousands of tests, which all use some sort of harness to run, which is shared amongst each
test (one test harness per fork). If a single test pollutes that harness (say changing some static
state within that harness), another test depending on that state will fail. Specifying Surefire's
`rerunFailingTestsCount` wouldn't help here, since the failing test is retried within the same fork.
The obvious solution would be to disable fork reuse, however, doing so would increase the total time
taken to run thousands of tests, since setting up the harness required is expensive.

Therefore, instead of letting Surefire retry the same test over and over again in the same fork,
SuperTest discovers failed tests from Surefire's report directory, and re-runs the failed ones.
Here's where things get interesting: this time, for the few tests that have failed, run them in
pure isolation - set Surefire's `reuseForks` option to `false`. Now, if these tests fail again,
we know for sure that they didn't fail due to pollution in the test harness by other tests.

## Example
A classic example to illustrate this problem is:

```java
public class MyState {
    public static boolean activateFeatureFoo = false;
}

public class BadTest {
    
    @Test
    public void test() {
        MyState.activateFeatureFoo = true;
        // Do something.
        // MyState#activateFeatureFoo wasn't reset! Bad test!
    }
}

public class AnotherTest {
    
    @Test
    public void test() {
        assertFalse(MyState.activateFeatureFoo); // Fails! If BadTest and AnotherTest run within the same fork, AnotherTest will ALWAYS fail.
    }
}
```

With SuperTest, if `AnotherTest` fails (if it ran after `BadTest`), only `AnotherTest` would be 
rerun, and not all the other tests, thereby saving time (especially when modules take an upwards
of 10+ minutes to run thousands of tests).

## Installation

To use SuperTest within your Maven project, ensure that the `mvn` executable is in the 
`PATH` of the original `mvn` invocation. Then, add the plugin to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.clevertap</groupId>
            <artifactId>supertest-maven-plugin</artifactId>
            <version>1.3-SNAPSHOT</version>
            <configuration>
                <!-- Optional profile to activate during a re-run;
                     Example: Activate a profile which disables fork reuse -->
                <rerunProfile>dontReuseForks</rerunProfile>
                <!-- Optional, defaults to 1 if not specified -->
                <retryRunCount>5</retryRunCount>
                <!-- Optional, additional parameters to pass to the mvn command invocation -->
                <mvnTestOpts>-P jacoco -fae</mvnTestOpts>
            </configuration>
        </plugin>
    </plugins>
</build>

<!-- Optional profile, but recommended for test isolation -->
<profiles>
    <profile>
        <id>dontReuseForks</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <reuseForks>false</reuseForks>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Note: The configuration parameters may also be specified directly when invoking `mvn` from the CLI:
```shell
$ mvn supertest:supertest -DrerunProfile=dontReuseForks -DretryRunCount=5 -DmvnTestOpts="-P jacoco -fae"
```

## Development
After making changes to the plugin, update the version in `pom.xml`, run `mvn install` from
the project root, and update the version of the plugin where SuperTest is being used.

## Maintainers
SuperTest Maven Plugin is maintained by the CleverTap Labs team, with contributions
from the entire engineering team.

## License
SuperTest Maven Plugin is licensed under the MIT License. Please see LICENSE
under the root directory.
