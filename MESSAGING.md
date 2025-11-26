# Messaging Architecture

Beancounter uses **Spring Cloud Stream** with a broker-agnostic functional programming model. This allows switching between message brokers (Kafka ↔ RabbitMQ) via a single configuration property, without any code changes.

## Supported Message Brokers

Both binder dependencies are included in the project, but **only one is active at runtime**:

- **Kafka** - Distributed streaming platform (default)
- **RabbitMQ** - Lightweight message broker

## Switching Between Brokers

Change brokers by setting a single environment variable or property:

### Using Kafka (Default)

```bash
# Already configured as default - no changes needed
./gradlew :svc-data:bootRun --args='--spring.profiles.active=local'
```

### Using RabbitMQ

**Option 1: Environment Variable**
```bash
export SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit
export SPRING_RABBITMQ_HOST=kauri.monowai.com
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_USERNAME=guest
export SPRING_RABBITMQ_PASSWORD=guest

./gradlew :svc-data:bootRun --args='--spring.profiles.active=local'
```

**Option 2: Command Line Property**
```bash
./gradlew :svc-data:bootRun --args='--spring.profiles.active=local --spring.cloud.stream.default-binder=rabbit --spring.rabbitmq.host=kauri.monowai.com'
```

**Option 3: Docker/Kubernetes**
```yaml
environment:
  - SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit
  - SPRING_RABBITMQ_HOST=kauri.monowai.com
  - SPRING_RABBITMQ_PORT=5672
  - SPRING_RABBITMQ_USERNAME=guest
  - SPRING_RABBITMQ_PASSWORD=guest
```

## Configuration

### Default Binder Selection

Set in `application.yml`:
```yaml
spring:
  cloud:
    stream:
      default-binder: kafka  # or "rabbit"
```

Override with environment variable:
```bash
SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit
```

### Kafka Configuration

Kafka broker connection (active when `default-binder=kafka`):
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: kafka:9092
```

Override with:
```bash
SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS=kafka.example.com:9092
```

### RabbitMQ Configuration

RabbitMQ connection (active when `default-binder=rabbit`):
```bash
SPRING_RABBITMQ_HOST=kauri.monowai.com
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
```

Or in `application.yml`:
```yaml
spring:
  rabbitmq:
    host: kauri.monowai.com
    port: 5672
    username: guest
    password: guest
```

## Message Flows

### Price Updates Flow
```
External Provider → svc-data (priceConsumer) → Database
                 ↓ (if dividend/split)
                 EventProducer → corporateEvent-out-0 → bc-ca-event-dev
                 ↓
                 svc-event (eventProcessor) → EventPublisher
                 ↓
                 transactionEvent-out-0 → bc-trn-event-dev
                 ↓
                 svc-data (trnEventConsumer) → Database
```

### Transaction Import Flow
```
jar-shell (csvImport-out-0) → bc-trn-csv-dev
                 ↓
                 svc-data (csvImportConsumer) → Database
```

### Portfolio Updates Flow
```
svc-position (portfolioMarketValue-out-0) → bc-pos-mv-dev
                 ↓
                 svc-data (portfolioConsumer) → Database
```

## Destinations (Topics/Exchanges)

| Destination       | Producer      | Consumer(s)   | Purpose                   |
|-------------------|---------------|---------------|---------------------------|
| bc-trn-csv-dev    | jar-shell     | svc-data      | CSV transaction imports   |
| bc-trn-event-dev  | svc-event     | svc-data      | Transaction events        |
| bc-price-dev      | External      | svc-data      | Market price data         |
| bc-ca-event-dev   | svc-data      | svc-event     | Corporate action events   |
| bc-pos-mv-dev     | svc-position  | svc-data      | Portfolio market values   |

### Destination Naming Convention

Pattern: `bc-{service}-{type}-{env}`

- `bc` - Beancounter prefix
- `{service}` - Service name (trn, pos, ca, price)
- `{type}` - Message type (csv, event, mv)
- `{env}` - Environment (dev, staging, prod)

## Functional Consumers and Producers

All consumers and producers use Spring Cloud Stream's functional programming model. They work identically with both Kafka and RabbitMQ.

### svc-data

**Consumers:**
- `csvImportConsumer: Consumer<TrustedTrnImportRequest>` - Processes CSV transaction imports
- `trnEventConsumer: Consumer<TrustedTrnEvent>` - Processes transaction events
- `priceConsumer: Consumer<PriceResponse>` - Persists market data prices
- `portfolioConsumer: Consumer<Portfolio>` - Maintains portfolio data from position updates

**Producers:**
- `EventProducer` (StreamBridge) - Publishes corporate action events via `corporateEvent-out-0`

### svc-position

**Producers:**
- `MarketValueUpdateProducer` (StreamBridge) - Publishes portfolio updates via `portfolioMarketValue-out-0`

### svc-event

**Consumers:**
- `eventProcessor: Consumer<TrustedEventInput>` - Processes corporate action events

**Producers:**
- `EventPublisher` (StreamBridge) - Publishes transaction events via `transactionEvent-out-0`

### jar-shell

**Producers:**
- `KafkaTrnProducer` (StreamBridge) - Sends CSV imports via `csvImport-out-0` (disabled by default)

## Testing

### Unit Tests

Tests use `spring-cloud-stream-test-binder` for broker-agnostic testing:

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class MyConsumerTest {
    // Test uses TestBinder, no real broker needed
    // Works regardless of production broker choice
}
```

Test configuration automatically uses the test binder, so tests don't depend on which broker you use in production.

## Deployment

### Local Development

Default (Kafka):
```bash
./gradlew :svc-data:bootRun --args='--spring.profiles.active=local'
```

With RabbitMQ:
```bash
export SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit
export SPRING_RABBITMQ_HOST=kauri.monowai.com
./gradlew :svc-data:bootRun --args='--spring.profiles.active=local'
```

### Docker Compose

```yaml
services:
  bc-data:
    image: monowai/bc-data
    environment:
      # For Kafka (default)
      - SPRING_CLOUD_STREAM_DEFAULT_BINDER=kafka
      - SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS=kafka:9092

      # Or for RabbitMQ
      # - SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit
      # - SPRING_RABBITMQ_HOST=rabbitmq
      # - SPRING_RABBITMQ_PORT=5672
```

### Kubernetes/Helm

Update `../bc-deploy/charts/*/values.yaml`:

```yaml
env:
  # For Kafka
  - name: SPRING_CLOUD_STREAM_DEFAULT_BINDER
    value: "kafka"
  - name: SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS
    value: "kafka-broker:9092"

  # Or for RabbitMQ
  # - name: SPRING_CLOUD_STREAM_DEFAULT_BINDER
  #   value: "rabbit"
  # - name: SPRING_RABBITMQ_HOST
  #   value: "rabbitmq-service"
  # - name: SPRING_RABBITMQ_PORT
  #   value: "5672"
```

## Broker Comparison

| Feature                | Kafka                | RabbitMQ              |
|------------------------|----------------------|-----------------------|
| Message Model          | Distributed log      | Traditional queuing   |
| Persistence            | Always persisted     | Optional              |
| Throughput             | Very high            | High                  |
| Latency                | Low (ms)             | Very low (μs)         |
| Ordering               | Per partition        | Per queue             |
| Consumer Groups        | Native support       | Via exchanges         |
| Message Retention      | Time-based           | Until consumed        |
| Operational Complexity | Higher               | Lower                 |
| Resource Usage         | Higher               | Lower                 |
| Best For               | Event streaming      | Task queuing          |

## Migration Between Brokers

To migrate from Kafka to RabbitMQ (or vice versa):

1. **Drain existing queues/topics** - Ensure no in-flight messages
2. **Update environment variables** - Set `SPRING_CLOUD_STREAM_DEFAULT_BINDER` and connection properties
3. **Restart all services** - All services must use the same broker
4. **Verify connectivity** - Check health endpoints and logs
5. **Monitor message flow** - Verify consumers are processing messages

**⚠️ WARNING**: All services must use the same broker. Do not run services with mixed brokers.

## Troubleshooting

### No Binder Selected

**Symptom**: Application fails with "No binder available"

**Solution**: Set the default binder:
```bash
export SPRING_CLOUD_STREAM_DEFAULT_BINDER=kafka
# or
export SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit
```

### Connection Refused (Kafka)

**Symptom**: `Connection to node -1 (kafka:9092) could not be established`

**Solution**: Verify Kafka broker address:
```bash
export SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS=localhost:9092
```

### Connection Refused (RabbitMQ)

**Symptom**: `Connection refused` to RabbitMQ

**Solution**: Verify RabbitMQ connection properties:
```bash
export SPRING_RABBITMQ_HOST=kauri.monowai.com
export SPRING_RABBITMQ_PORT=5672
# Test connection
nc -zv kauri.monowai.com 5672
```

### Messages Not Consumed

**Symptom**: Messages sent but not received

**Checklist**:
1. Verify all services use same broker (`SPRING_CLOUD_STREAM_DEFAULT_BINDER`)
2. Check destination names match in producer and consumer
3. Verify consumer group configuration
4. Check broker connectivity for both producer and consumer
5. Review logs for binding errors

### Mixed Broker Error

**Symptom**: Some services on Kafka, others on RabbitMQ

**Solution**: Standardize all services to use the same broker. Check environment variables across all deployments.

## Key Benefits

- ✅ **Single property** switches brokers - `SPRING_CLOUD_STREAM_DEFAULT_BINDER`
- ✅ **No code changes** - Same functional code works with both
- ✅ **No profile files** - Simple environment variable configuration
- ✅ **Standard Spring Boot properties** - Familiar configuration approach
- ✅ **Easy deployment** - Works with Docker, K8s, local dev

## References

- [Spring Cloud Stream Documentation](https://spring.io/projects/spring-cloud-stream)
- [Spring Cloud Stream Kafka Binder](https://docs.spring.io/spring-cloud-stream-binder-kafka/docs/current/reference/html/)
- [Spring Cloud Stream RabbitMQ Binder](https://docs.spring.io/spring-cloud-stream-binder-rabbit/docs/current/reference/html/)
- [Spring Boot Common Properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
