# 缓存工具 one-light-cache

## 一. 介绍

`one-light-cache` 是一个简单的缓存工具，基于`Caffeine`和`Redisson`
实现，致力于让使用者更加方便的管理本地缓存和远程缓存，不用考虑缓存刷新、缓存一致性、缓存雪崩以及缓存击穿等问题。
`one-light-cache` 提供`集中式`的本地缓存管理-`LightCacheManager`，支持代码和配置文件等多种构建缓存，并可利用`Redis`作为远程缓存中间层来解决`缓存击穿`、`缓存雪崩`等问题。
同时，`one-light-cache` 提供了`自动刷新缓存`的功能(可用 `Rocketmq` 或者 `Redis Pub/Sub` 作为`MQ` ），用来解决当数据更新后缓存一致性问题（多节点本地缓存一致，本地缓存与数据库一致）。当然，这里的一致性仍然是`最终一致性`，而非`强一致性`。
在数据库更新完成和收到刷新缓存的消息之间的短暂间隙，可能仍会有一小段时间本地缓存和数据库的数据不一致。

## 二. 使用方法

- 1、自行编译、引入依赖

```xml

<dependency>
    <groupId>com.oneinstep.light</groupId>
    <artifactId>light-cache-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

- 2、启动类添加注解 `@EnableLightCache`

```java

@SpringBootApplication
@EnableLightCache
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- 3、可选配置

```yaml
light:
  cache:
    # 生产者组
    producer-group: my-cache-producer-group
    # 消费者组
    consumer-group: my-cache-consumer-group
    # RocketMQ 名称服务器
    rocketmq-name-server: localhost:9876
    # 是否使用 Redis 作为缓存 默认 true
    use-redis-as-cache: true
```

- 4、构建缓存

```java
// 1、先构建并且注册缓存
LightCacheManager.<YourCacheClass> newCacheBuilder()
                    // 缓存名称 必填 不可重复
                    .cacheName(your_cache_name)
                    // 初始化容量
                    .initialCapacity(5)
                    // 最大容量
                    .maximumSize(500)
                    // 写入后多久过期
                    .expireAfterWrite(5000)
                    // 当缓存过期、或者执行缓存刷新时，如何获取数据的Function类。 fetcher 和 loadCacheExpression 二选一
                    .fetcher(userIdStr -> new User())
                    // 使用表达式加载缓存
                    // .loadCacheExpression("return key + \"-User\";")
                    // 数据变更时，通知的消息topic
                    .dataChangeTopic("your_data_change_topic")
                    // 数据变更时，通知的消息类型，默认使用 RocketMQ
                    .mqType(MQType.ROCKETMQ)
                    .buildAndRegister();

// 2、使用缓存 通过 LightCacheManager 获取缓存
@Resource
private LightCacheManager cacheManager;

// or LightCacheManager.getInstance().getCache(your_cache_name);
// 获取缓存
LightCache<YourCacheClass> cache = cacheManager.getCache(your_cache_name);
// 获取数据
YourCacheClass data = cache.get(your_cache_key);


// 3、引入了 one-light-cache 包的服务更新了数据，发送刷新缓存的消息，其它服务的话发送 如下com.oneinstep.light.cache.core.DataChangeMsg 格式的 json string 消息到指定的 topic 即可
@Resource(name = "rocketMQDataChangeMsgProducer") // 如果使用 rocketmq
// @Resource(name = "redisDataChangeMsgProducer") // 如果使用 redis pub/sub
private IDataChangeMsgProducer mqProducer;

DataChangeMsg dataChangeMsg = DataChangeMsg.builder()
                      .dataName(your_cache_name)
                      .dataId(your_data_id)
                      .type(DataChangeMsg.DataChangeType.UPDATE)
                      .build();
mqProducer.sendMsg("your_data_change_topic", JSON.toJSONString(dataChangeMsg));
```

5、使用 `Spring properties/yml 文件` 配置缓存

```yaml
light:
  cache:
    # 生产者组
    producer-group: my-cache-producer-group
    # 消费者组
    consumer-group: my-cache-consumer-group
    # RocketMQ 名称服务器
    rocketmq-name-server: localhost:9876
    # 是否使用 Redis 作为缓存 默认 true
    use-redis-as-cache: true
    # 缓存配置 可配置多个缓存
    cache-configs:
      # 测试用户缓存
      - cacheName: test-user
        # 初始缓存数量
        initial-capacity: 20
        # 最大缓存数量
        maximum-size: 100
        # 5 秒后过期
        expire-after-write: 5000
        # 缓存加载表达式 使用 Aviator 表达式，见：com.oneinstep.light.cache.demo.function.GetUserByIdFunction
        load-cache-expression: getUserById(key)
        # loadCacheExpression: 'return key + "test"'
        # 缓存加载超时时间 超过该时间后，缓存加载失败，将设置null值
        load-cache-timeout: 5000
        # mq 主题
        mq-topic: user_data_change
        # mq 类型，默认使用 RocketMQ
        mq-type: REDIS
```

## 三、原理

- 1、如何保证本地缓存一致性？
  - 本地缓存一致性是指多个节点之间的本地缓存数据保持一致，即多个节点的本地缓存数据是一样的。
  - 首先多个节点的本地缓存数据来源于 Redis，另外当某个节点更新数据或者其它服务更新数据后，可以通过`rocketmq`或者
`redis pub/sub`来发送刷新缓存的消息，其他节点收到消息后，会删除本地缓存，另外有一个节点会负责更新 `Redis` 中间层的缓存。
- 2、如何防止缓存击穿？
  - 缓存击穿是指某个 key 的缓存过期后，恰好有大量的并发请求访问这个 key，导致大量请求直接打到数据库上，造成数据库压力过大。
  - 当节点的本地缓存过期，Redis 中的数据也过期后，这时候会首先申请一个分布式锁，抢到锁的线程才能从数据库或者RPC接口中获取数据，更新 Redis 缓存，最后释放锁。这样就可以保证只有一个节点去查询数据库或者RPC接口并更新 redis 中缓存。
  - 其它未获取到锁的节点，抢到锁后会首先从 Redis 中获取数据（这里最先抢到锁的线程应该已经更新了 Redis 中数据），如果 Redis 中有数据，就直接返回，如果没有数据，就再去查询数据库或者RPC接口。
  - 这样就可以保证只有一个节点去查询数据库，而其它节点直接从 Redis 中获取数据。

## 四、测试用例

- 场景 1:收到刷新缓存的消息

```text
+-------+ +-------+ +-------+ +-------+
        |Node1 | |Node2 | |Node3 | |Node4 |
        +-------+ +-------+ +-------+ +-------+
        | | | |
        |<---刷新请求--->|<---刷新请求----->|<---刷新请求--->|
        | | | |
        |---获取锁------>| | |
        | | | |
        |--从DB获取数据-->| | |
        |---更新Redis--->| | |
        |---释放锁------>| | |
        | |----获取锁------>| |
        | |-从Redis获取数据->| |
        | |---释放锁------->| |
        | | |---获取锁------->|
        | | |-从Redis获取数据->|
        | | |----释放锁------>|
        | | | |

```

- 场景 2: 某个节点内存缓存过期，并且 redis 中的数据也过期

```text
+-------+       +-------+       +-------+       +-------+
| Node2 |       | Node3 |       | Node4 |       | Node1 |
+-------+       +-------+       +-------+       +-------+
    |-检测缓存过期--->｜              |                |
    |---获取锁------>|-检测缓存过期--->|                |
    |--从DB获取数据->｜               |-检测缓存过期--->|
    |---更新Redis-->｜               |                |
    |---释放锁------>|               |                |
    |               |---获取锁------>|                |
    |               |-从Redis获取数据->｜              |
    |               |---释放锁------>|                |
    |               |               |----获取锁------>|
    |               |               |-从Redis获取数据->|
    |               |               |---释放锁------->|
    |               |               |                |
```

## 四、Q & A

1、Q：一般业内最常见的缓存更新策略- `先更新或者删除DB数据，再删除缓存` 存在什么问题？
![缓存一致性问题.png](doc/img/%E7%BC%93%E5%AD%98%E4%B8%80%E8%87%B4%E6%80%A7%E9%97%AE%E9%A2%98.png)
A：可以看到左边的时序图，在其它事务提交事务前，查询到了旧数据，然后由于`GC`或者网络等卡顿，在此期间其它事务已经更新数据并删除缓存，这时候该线程又苏醒过来，将旧数据设置到了缓存。
如何解决这个问题呢？可以使用`延时双删`策略，在更新数据并删除缓存后，等待一段时间再删除缓存，这样可以保证，最多只有一段时间（延时时间）的数据不一致。

2、此项目中的缓存一致性问题分析

- 2.1 某节点或者其它服务更新 DB，并发送 MQ 消息
  ![缓存刷新可能存在的数据不一致.png](doc/img/%E7%BC%93%E5%AD%98%E5%88%B7%E6%96%B0%E5%8F%AF%E8%83%BD%E5%AD%98%E5%9C%A8%E7%9A%84%E6%95%B0%E6%8D%AE%E4%B8%8D%E4%B8%80%E8%87%B4.png)
- 此时存在两个问题：
  - a) 跟上面讲的先更新 DB 再删除缓存的问题一样，可能存在数据不一致，虽说可能性很小
  - b) 由于从更新 DB 到收到 MQ 消息之间存在时间差，可能导致节点在此期间查询到了缓存旧数据
  - c) 由于`MQ`消息可能丢失，导致其它节点没有收到消息，这时候就会导致数据不一致
- A：2.2 某时刻某节点的本地缓存过期，Redis 中的数据也过期，并且此时有大量并发请求访问这个 key，而且刚好收到了刷新缓存的消息
  ![请求本地缓存为空.png](doc/img/%E8%AF%B7%E6%B1%82%E6%9C%AC%E5%9C%B0%E7%BC%93%E5%AD%98%E4%B8%BA%E7%A9%BA.png)
- Q：1、是否存在缓存击穿问题？
  - A：不存在，因为只有一个节点，一个线程（获取到分布式锁的那个）会去查询数据库并更新 Redis 缓存，其它所有线程再次获取锁后，会直接从 Redis 中获取数据
- Q：2、是否存在数据不一致问题？
  - A：不存在，因为只有一个节点会去查询数据库并更新 Redis 缓存，其它所有节点会直接从 Redis 中获取数据，而 Redis 中的数据是最新的
- Q：3、是否存在阻塞问题？
  - A：存在可能性，如果获取锁的那个线程由于`GC`或者网络等原因卡顿，或者获取数据时间较长，可能会导致其它线程一直等待，这时候可以设置一个超时时间，如果超过这个时间，就直接返回 NULL，不再等待
- Q：4、如果获取锁的那个线程挂掉了或者长时间卡顿了怎么办？那么 redis 数据是否会一直是旧数据？
  - A：首先，Redis 数据不会一直是旧数据，因为 Redis 数据也会有过期时间，其次，如果获取锁的那个线程挂掉了或者长时间卡顿了，那么其它节点会再次获取锁（Redisson 锁机制），然后从 DB 中获取数据并更新 Redis 缓存，这样就可以保证 Redis 中的数据是最新的。另外，获取数据时会设置一个超时时间，如果超过这个时间，就直接返回 NULL，不再等待。
