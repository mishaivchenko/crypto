---
type: analysis
title: Auto-Configuration Analysis — Audit Round 3 Phase 3
created: 2026-07-13
tags:
  - spring-boot
  - auto-configuration
  - audit
  - engine-app
  - monitor-app
  - telegram-bot-app
related:
  - '[[AUDIT-ROUND-3-03]]'
---

# Auto-Configuration Analysis

## Methodology

Each module was started with the `--debug` flag which enables Spring Boot's `ConditionEvaluationReportLoggingListener`. This logs the complete auto-configuration report showing which configurations matched, which did not, and why.

- **Profiles active**: `local-safe` (all three modules)
- **Spring Boot version**: 3.5.14
- **JDK**: 25.0.2

## Summary Statistics

| Module | Positive Matches | Negative Matches | Exclusions | Unconditional Classes |
|--------|:-:|:-:|:-:|:-:|
| **engine-app** | 168 | ~950 | 0 | 12 |
| **monitor-app** | 262 | ~1200 | 0 | 14 |
| **telegram-bot-app** | 86 | ~550 | 0 | 10 |

### Engine-app (port 8091)
- Lightweight web app with embedded Tomcat
- 168 auto-configuration classes active
- All configurations are default Spring Boot auto-configs — no custom auto-config classes

### Monitor-app (port 8090)
- Full web app with JPA + SQLite + Flyway + OpenFeign
- 262 auto-configuration classes active
- Includes JPA, DataSource, Flyway, Validation, Feign, Cloud auto-configurations

### Telegram-bot-app (no embedded server)
- Non-web app (`spring.main.web-application-type: none`)
- 86 auto-configuration classes active — significantly fewer than web apps
- Web/Servlet/WebMvc/WebSocket auto-configs are correctly NOT active

## Explicit Exclusions

**No `spring.autoconfigure.exclude` settings exist in any YAML, properties, or annotation across all three modules.** No auto-configuration classes are excluded explicitly.

The only `@EnableAutoConfiguration` with `exclude` is in a **test support class** (`JpaSliceTestConfiguration`) — not in production code.

## Custom `@SpringBootApplication` Configurations

### MonitorApplication
```java
@SpringBootApplication(scanBasePackages = {
    "com.crypto.funding.api",
    "com.crypto.funding.application",
    "com.crypto.funding.config",
    "com.crypto.funding.infrastructure",
    "com.crypto.funding.security"
})
@ConfigurationPropertiesScan(basePackages = "com.crypto.funding.config")
@EnableScheduling
@EnableAsync
```
- 5 explicit scan packages — notably **_excludes_ `com.crypto.funding` root** 
- No `exclude` or `excludeName`
- `@EnableScheduling` + `@EnableAsync`

### EngineApplication
```java
@SpringBootApplication
@EnableScheduling
@EnableAsync
@Import(EngineModuleConfiguration.class)
```
- No `scanBasePackages` — uses default (`com.crypto.funding.engine` + subpackages)
- No `exclude` or `excludeName`
- `@Import(EngineModuleConfiguration.class)` — custom module config
- `EngineModuleConfiguration` adds `@ConfigurationPropertiesScan` and `@EnableConfigurationProperties(EngineProperties.class)`

### TelegramBotApplication
```java
@SpringBootApplication(scanBasePackages = "com.crypto.funding.telegram")
@ConfigurationPropertiesScan(basePackages = "com.crypto.funding.telegram.config")
@EnableFeignClients(basePackages = "com.crypto.funding.telegram.client")
@EnableScheduling
```
- Single scan package: `com.crypto.funding.telegram`
- No `exclude` or `excludeName`
- No `@EnableAsync`
- Explicit Feign client scanning

## Notable Auto-Configurations Per Module

### Engine-app Notable Findings

| Auto-Configuration | Status | Notes |
|---|:---:|---|
| **WebSocketServletAutoConfiguration** | ✅ Active | Auto-configured because Tomcat is embedded — creates `WebSocketServletWebServerCustomizer`. Not used — no WebSocket endpoints. |
| **GenericCacheConfiguration** | ✅ Active | Cache auto-config matched because `CacheCondition` passes (no `@EnableCaching`, but cache is on classpath). Creates NoOpCacheManager fallback. |
| **SimpleCacheConfiguration** | ✅ Active | Part of cache auto-config chain |
| **NoOpCacheConfiguration** | ✅ Active | Fallback cache manager |
| **DiskSpaceHealthContributorAutoConfiguration** | ✅ Active | Health indicator registered even though Actuator is unused |
| **SslHealthContributorAutoConfiguration** | ✅ Active | Creates SSL health indicator |
| **GenericCacheConfiguration** | ✅ Active | Cache auto-config from classpath presence |
| **GsonAutoConfiguration** | ❌ Not active | Gson not on classpath (correct) |
| **JPA / DataSource / Flyway** | ❌ Not active | No JPA dependencies (correct) |

### Monitor-app Notable Findings

| Auto-Configuration | Status | Notes |
|---|:---:|---|
| **HibernateJpaAutoConfiguration** | ✅ Active | JPA + Hibernate auto-config |
| **DataSourceAutoConfiguration** | ✅ Active | HikariCP datasource |
| **FlywayAutoConfiguration** | ✅ Active | Flyway migration |
| **ValidationAutoConfiguration** | ✅ Active | Bean Validation |
| **FeignAutoConfiguration** | ✅ Active | OpenFeign HTTP clients |
| **PrometheusMetricsExportAutoConfiguration** | ✅ Active | Micrometer Prometheus registry |
| **WebSocketServletAutoConfiguration** | ✅ Active | Tomcat WebSocket (not used) |
| **SpringDataWebAutoConfiguration** | ✅ Active | Spring Data web support |
| **AutoServiceRegistrationAutoConfiguration** | ✅ Active | Spring Cloud service registration (auto-disabled via `spring.cloud.service-registry.auto-registration.enabled=false` default) |
| **GenericCacheConfiguration** | ✅ Active | Cache auto-config (not used) |
| **GsonAutoConfiguration** | ❌ Not active | Gson not on classpath |

### Telegram-bot-app Notable Findings

| Auto-Configuration | Status | Notes |
|---|:---:|---|
| **GsonAutoConfiguration** | ✅ Active | **Unexpected** — Gson is on classpath via transitive dependency (likely from OpenFeign/Spring Cloud). Creates Gson bean and GsonHttpMessageConverter. |
| **FeignAutoConfiguration** | ✅ Active | OpenFeign clients |
| **GenericCacheConfiguration** | ✅ Active | Cache auto-config — no caching is used |
| **WebSocket / Servlet / WebMvc** | ❌ Not active | Correct — non-web application |
| **DataSource / JPA / Flyway** | ❌ Not active | Correct |

## Unexpected Beans from Transitive Starters

### 1. WebSocket in engine-app (and monitor-app)
**Impact**: Low. `WebSocketServletAutoConfiguration` registers a `WebSocketServletWebServerCustomizer` which is essentially a no-op unless WebSocket handlers are registered. The bean is created but does nothing.

**Recommendation**: No action needed — the bean is harmless. To eliminate, add `@SpringBootApplication(exclude = WebSocketServletAutoConfiguration.class)` in EngineApplication.

### 2. Cache auto-configurations in all modules
**Impact**: Low. `GenericCacheConfiguration` → `NoOpCacheConfiguration` → `SimpleCacheConfiguration` create lightweight cache manager beans that are registered but never used. Each module gets a `ConcurrentMapCacheManager` via `SimpleCacheConfiguration`.

**Recommendation**: No action needed unless startup time optimization is critical. To eliminate, add `spring.cache.type=none` to each module's `application.yml`.

### 3. Gson in telegram-bot-app
**Impact**: Very low. Gson is pulled transitively and an auto-configured `Gson` bean is created alongside the `GsonHttpMessageConverter`. Since the app has no web server, the message converter is registered but never used.

**Recommendation**: No action needed. If the Gson dependency is unwanted and solely transitive, consider excluding it from the dependency declaration. However, presence of Gson alongside Jackson is typical for Feign/Spring Cloud.

### 4. DiskSpace/Ssl Health Indicators in engine-app
**Impact**: Low. `DiskSpaceHealthContributorAutoConfiguration` and `SslHealthContributorAutoConfiguration` create health indicator beans that are registered with the health endpoint. Even though Actuator is not used in engine-app, Actuator endpoints are auto-configured by `@SpringBootApplication` because `spring-boot-starter-actuator` is on the classpath. These health indicators add minimal overhead.

**Recommendation**: If Actuator is retained (see Phase 2 analysis), these are harmless. If Actuator is removed from engine-app, they disappear automatically.

## Spring Cloud Auto-Configurations

Both monitor-app and telegram-bot-app have Spring Cloud auto-configurations active:

| Module | Cloud Auto-configs | Purpose |
|--------|:---:|---|
| monitor-app | 8 | Refresh, Restart, Service Registration, Discovery, LoadBalancer |
| telegram-bot-app | 8 | Same Cloud infrastructure for Feign + LoadBalancer |

The `spring.cloud.compatibility-verifier.enabled=false` setting in all modules correctly disables the compatibility verifier.

## Recommended Improvements

### Low Effort / High Certainty
1. **Add explicit exclusions for unused auto-configurations** (optional):
   ```java
   @SpringBootApplication(exclude = {
       WebSocketServletAutoConfiguration.class
   })
   ```
   Only recommended if reducing startup overhead is a priority.

2. **Set `spring.cache.type=none`** in each module's `application.yml` to prevent cache auto-config from creating unnecessary cache manager beans.

### Observation
The auto-configuration footprint matches declared dependencies. The 168 / 262 / 86 active classes are consistent with the starter sets identified in Phase 2 (Section 6). No auto-configuration was found running unexpectedly that indicates a hidden dependency or misconfiguration — confirming the earlier starter analysis was correct.
