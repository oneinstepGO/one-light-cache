# one-light-cache

## Introduction

`one-light-cache` is a lightweight distributed caching framework built on top of Caffeine and Redisson. It provides a
unified cache management interface and solves common distributed caching challenges like cache consistency, cache
penetration, and cache avalanche.

## Key Features

### 1. Unified Cache Management

- Centralized local cache management through `LightCacheManager`
- Support for both code-based and configuration-based cache creation
- Redis integration as a remote cache layer

### 2. Cache Consistency

- Support for RocketMQ/Redis Pub/Sub messaging
- Eventual consistency guarantee in distributed environments
- Distributed lock-based cache update strategy

### 3. Cache Protection

- Cache penetration prevention (distributed locks + two-level cache)
- Cache avalanche prevention (staggered expiration)
- Cache penetration prevention (null value caching)

### 4. Flexible Configuration

- Support for YAML configuration and code configuration
- Customizable cache parameters (capacity, expiration time, etc.)
- Support for Aviator expressions for cache loading

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.oneinstep.light</groupId>
    <artifactId>light-cache-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Enable Cache

```java
@SpringBootApplication
@EnableLightCache
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. Configuration Method

```yaml
light:
  cache:
    producer-group: my-cache-producer-group
    consumer-group: my-cache-consumer-group
    rocketmq-name-server: localhost:9876
    use-redis-as-cache: true
    kafka-bootstrap-servers: localhost:9092
    cache-configs:
      - cacheName: test-user
        initial-capacity: 20
        maximum-size: 100
        expire-after-write: 5000
        load-cache-expression: getUserById(key)
        mq-topic: user_data_change
        mq-type: REDIS
```

### 4. Code Method

```java
LightCacheManager.<User>newCacheBuilder()
    .cacheName("user-cache")
    .initialCapacity(10)
    .maximumSize(100)
    .expireAfterWrite(5000)
    .fetcher(userId -> userService.getUser(userId))
    .mqTopic("user_data_change")
    .mqType(MQType.REDIS)
    .buildAndRegister();

// Using cache
LightCache<User> cache = cacheManager.getCache("user-cache");
User user = cache.get("123");
```

## Core Principles

### 1. Cache Architecture

```
+----------------+  
|  Application   |
+----------------+
        ↓
+----------------+     +-----------------+
| Local Cache    | →   |  Redis Cache    |
| (Caffeine)     |     |  (Redisson)     |
+----------------+     +-----------------+
                              ↓
                      +-----------------+
                      |  Data Source    |
                      |  (DB/RPC)       |
                      +-----------------+
```

### 2. Cache Update Mechanism

- **Local Cache Expiration**: Prioritize fetching from Redis, use distributed locks when Redis has no data
- **Data Changes**: Notify all nodes through message queues to update cache, ensuring eventual consistency
- **Penetration Prevention**: Use distributed locks to ensure only one node queries the data source
- **Null Value Handling**: Set short expiration times for null values to prevent cache penetration

### 3. Use Cases

1. **High Concurrency Read**
    - Product details
    - User information
    - Configuration data

2. **Distributed Environment**
    - Multi-node deployment
    - Cache consistency requirements
    - Cache penetration prevention

## Advanced Features

### 1. Custom Data Loading

```java
// Method 1: Lambda expression
.fetcher(userId -> userService.getUser(userId))

// Method 2: Aviator expression
.loadCacheExpression("getUserById(key)")
```

### 2. Cache Update Strategy

```java
// Send cache update message
DataChangeMsg msg = DataChangeMsg.builder()
    .dataName("user-cache")
    .dataId(userId)
    .type(DataChangeType.UPDATE)
    .build();
mqProducer.sendMsg("user_topic", JSON.toJSONString(msg));
```

### 3. Monitoring Metrics

- Cache hit rate
- Loading time
- Concurrent loading count

## Notes

1. **Cache Consistency**
    - Framework provides eventual consistency guarantee
    - Send cache update message immediately after updating DB
    - Critical business should query data source directly

2. **Performance Optimization**
    - Set reasonable cache capacity and expiration time
    - Avoid caching large objects
    - Regular cleanup of expired data

3. **Exception Handling**
    - Cache loading timeout settings
    - Fallback processing
    - Log monitoring