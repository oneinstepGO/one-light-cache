# one-light-cache

## 一、介绍

`one-light-cache` 是一个基于 `Caffeine` 和 `Redisson` 实现的轻量级分布式缓存框架。它提供了统一的缓存管理接口，并解决了分布式环境下的缓存一致性、缓存击穿、缓存雪崩等常见问题。

## 二、主要特性

### 1. 统一缓存管理

- 集中式本地缓存管理器 `LightCacheManager`
- 支持代码和配置文件两种方式构建缓存
- 集成 Redis 作为远程缓存中间层

### 2. 缓存一致性

- 支持 RocketMQ/Redis Pub/Sub 消息通知机制
- 分布式环境下的最终一致性保证
- 基于分布式锁的缓存更新策略

### 3. 缓存保护

- 防止缓存击穿（分布式锁 + 二级缓存）
- 防止缓存雪崩（错峰过期）
- 防止缓存穿透（空值缓存）

### 4. 灵活配置

- 支持 YAML 配置和代码配置
- 自定义缓存参数（容量、过期时间等）
- 支持 Aviator 表达式加载缓存

## 三、快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.oneinstep.light</groupId>
    <artifactId>light-cache-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 启用缓存

```java
@SpringBootApplication
@EnableLightCache
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 配置文件方式

```yaml
light:
  cache:
    producer-group: my-cache-producer-group
    consumer-group: my-cache-consumer-group
    rocketmq-name-server: localhost:9876
    use-redis-as-cache: true
    cache-configs:
      - cacheName: test-user
        initial-capacity: 20
        maximum-size: 100
        expire-after-write: 5000
        load-cache-expression: getUserById(key)
        mq-topic: user_data_change
        mq-type: REDIS
```

### 4. 代码方式

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

// 使用缓存
LightCache<User> cache = cacheManager.getCache("user-cache");
User user = cache.get("123");
```

## 四、核心原理

### 1. 缓存架构

```
+----------------+  
|  应用层        |
+----------------+
        ↓
+----------------+     +-----------------+
| 本地缓存        |     |  Redis缓存       |
| (Caffeine)     |     |  (Redisson)     |
+----------------+     +-----------------+
                              ↓
                       +-----------------+
                       |  数据源          |
                       |  (DB/RPC)       |
                       +-----------------+
```

### 2. 缓存更新机制

- **本地缓存过期**：优先从 Redis 获取，Redis 无数据时使用分布式锁防止缓存击穿
- **数据变更**：通过消息队列通知所有节点更新缓存，保证最终一致性
- **防止击穿**：使用分布式锁确保同一时刻只有一个节点查询数据源
- **空值处理**：对空值设置较短的过期时间，防止缓存穿透

### 3. 使用场景

1. **高并发读取**
    - 商品详情
    - 用户信息
    - 配置数据

2. **分布式环境**
    - 多节点部署
    - 需要缓存一致性
    - 防止缓存击穿

## 五、高级特性

### 1. 自定义数据加载

```java
// 方式一：Lambda 表达式
.fetcher(userId -> userService.getUser(userId))

// 方式二：Aviator 表达式
.loadCacheExpression("getUserById(key)")
```

### 2. 缓存更新策略

```java
// 发送缓存更新消息
DataChangeMsg msg = DataChangeMsg.builder()
    .dataName("user-cache")
    .dataId(userId)
    .type(DataChangeType.UPDATE)
    .build();
mqProducer.sendMsg("user_topic", JSON.toJSONString(msg));
```

### 3. 监控指标

- 缓存命中率
- 加载耗时
- 并发加载次数

## 六、注意事项

1. **缓存一致性**
    - 框架提供最终一致性保证
    - 更新DB后立即发送缓存更新消息
    - 关键业务建议直接查询数据源

2. **性能优化**
    - 合理设置缓存容量和过期时间
    - 避免缓存大对象
    - 定期清理过期数据

3. **异常处理**
    - 缓存加载超时设置
    - 降级处理
    - 日志监控

## 特性

- **统一缓存管理**：集成本地缓存（Caffeine）和分布式缓存（Redis）的统一接口
- **缓存一致性**：支持多种消息队列选项（Redis/RocketMQ/Kafka）实现缓存一致性通知
- **缓存防护**：内置防止缓存穿透、击穿、雪崩的保护机制
- **灵活配置**：支持编程式和配置式的缓存设置

## 快速开始

1. 添加依赖：

```xml
<dependency>
    <groupId>com.oneinstep.light</groupId>
    <artifactId>light-cache-spring-boot-starter</artifactId>
    <version>${version}</version>
</dependency>
```

2. 在 application.yml 中配置：

```yaml
light:
  cache:
    use-redis-as-cache: true  # 是否使用 Redis 作为缓存
    rocketmq-name-server: localhost:9876  # RocketMQ 名称服务器地址（如果使用 RocketMQ）
    kafka-bootstrap-servers: localhost:9092  # Kafka 服务器地址（如果使用 Kafka）
    producer-group: light-cache-producer-group
    consumer-group: light-cache-consumer-group
    cache-configs:
      - cache-name: user-cache
        initial-capacity: 100
        maximum-size: 1000
        expire-after-write: 3600000
        refresh-after-write: 1800000
        mq-type: KAFKA  # 可选值：NO_MQ, REDIS, ROCKETMQ, KAFKA
        mq-topic: user_cache_topic
```

## 核心原理

### 缓存架构

框架采用二级缓存策略：

1. 一级缓存：使用 Caffeine 实现的本地缓存
2. 二级缓存：使用 Redis 实现的分布式缓存
3. 消息通知：支持 Redis/RocketMQ/Kafka 实现缓存一致性

### 缓存更新机制

1. 数据变更时：
    - 应用程序通过 Redis/RocketMQ/Kafka 发送消息
    - 所有节点接收消息并更新本地缓存
2. 缓存一致性通过以下方式维护：
    - Redis 作为分布式锁
    - 消息队列（Redis/RocketMQ/Kafka）进行变更通知

--- 

相关微信公众号文章：[如何设计一个分布式缓存系统？](https://mp.weixin.qq.com/s/y4LRACWVP7f6nkpZbylHpg)

欢迎关注我的公众号“**子安聊代码**”，一起探讨技术。
<div style="text-align: center;">
    <img src="https://mp-img-1300842660.cos.ap-guangzhou.myqcloud.com/1725553610603-faeaaec6-b1b6-4f03-b4e2-7ddbf6fbccdf.jpg" style="width: 100px;" alt="">
</div>
