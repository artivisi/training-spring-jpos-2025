# Queue Monitoring and Throttling

This document explains the queue monitoring and throttling mechanisms implemented in the jPOS ATM switch.

## Overview

The system monitors the Space queue (`txnmgr`) and throttles incoming requests when the queue size exceeds a configured threshold. This prevents the system from being overwhelmed during high load.

## Two Implementation Approaches

### Approach 1: TCP Connection Rejection (Basic)

**Configuration:** Use `10_channel.xml` with `ATMRequestListener`

**Behavior:**
- When queue size ≥ high threshold: QServer stops, **rejecting new TCP connections**
- Clients receive **"Connection Refused"** error at network level
- No ISO-8583 response is sent

**Pros:**
- Simple implementation
- Immediately stops new requests

**Cons:**
- Not graceful - clients don't get proper ISO-8583 response
- Client applications may need special handling for connection errors
- May trigger connection retry storms

### Approach 2: Graceful ISO-8583 Response (Recommended)

**Configuration:** Use `10_channel_with_throttle.xml` with `ThrottleAwareRequestListener`

**Behavior:**
- When queue size ≥ high threshold: System enters throttle mode
- **Connections are still accepted**
- **Requests receive ISO-8583 response with code 91** (System unavailable)
- Clients get proper protocol-level response

**Pros:**
- ✅ Graceful handling - clients receive proper ISO-8583 response
- ✅ Better for client applications - they can handle response code 91
- ✅ Follows ISO-8583 standards for error handling
- ✅ No connection-level errors

**Cons:**
- Slightly more complex implementation

## ISO-8583 Response Codes

When using the **graceful approach** (Approach 2), the following response code is used:

| Code | Description | Usage |
|------|-------------|-------|
| **91** | System malfunction/unavailable | Returned when system is throttled due to high queue size |

Other common ISO-8583 response codes in the system:
- `00` - Approved/Success
- `30` - Format error
- `51` - Insufficient funds
- `57` - Transaction not permitted
- `96` - System error

### Configuring Response Code

You can customize the throttle response code in `10_channel_with_throttle.xml`:

```xml
<property name="throttle-response-code" value="91" />
```

Alternative codes you might consider:
- `91` - System malfunction (recommended)
- `94` - Duplicate transmission
- `96` - System error

## Configuration

### QueueMonitor Settings

File: `src/dist/deploy/15_queue_monitor.xml`

```xml
<queue-monitor class="com.example.atm.QueueMonitor" logger="Q2" realm="queue-monitor">
    <property name="space" value="tspace:default" />
    <property name="queue" value="txnmgr" />
    <property name="check-interval" value="1000" />        <!-- Check every 1 second -->
    <property name="high-threshold" value="100" />         <!-- Throttle when queue ≥ 100 -->
    <property name="low-threshold" value="50" />           <!-- Resume when queue ≤ 50 -->
</queue-monitor>
```

### Request Listener Settings

#### Option 1: Basic (TCP rejection)
File: `src/dist/deploy/10_channel.xml`

```xml
<request-listener class="com.example.atm.ATMRequestListener" logger="Q2" realm="incoming-request-listener">
    <property name="space" value="tspace:default" />
    <property name="queue" value="txnmgr" />
    <property name="timeout" value="60000" />
</request-listener>
```

#### Option 2: Graceful (ISO-8583 response) - RECOMMENDED
File: `src/dist/deploy/10_channel_with_throttle.xml`

```xml
<request-listener class="com.example.atm.ThrottleAwareRequestListener" logger="Q2" realm="incoming-request-listener">
    <property name="space" value="tspace:default" />
    <property name="queue" value="txnmgr" />
    <property name="timeout" value="60000" />
    <property name="throttle-response-code" value="91" />
</request-listener>
```

## How It Works

### Flow Diagram

```
┌─────────────────┐
│  QueueMonitor   │ (checks every 1s)
│  Background     │
│    Thread       │
└────────┬────────┘
         │
         ├─> Check queue size in Space
         │
         ├─> If size ≥ 100: ThrottleManager.setThrottled(true)
         │
         └─> If size ≤ 50:  ThrottleManager.setThrottled(false)

┌─────────────────────────────────────┐
│  Client Request                     │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  QServer (port 8000)                │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  ThrottleAwareRequestListener       │
└──────────┬──────────────────────────┘
           │
           ├─> Check ThrottleManager.isThrottled()
           │
           ├─> If throttled:
           │   ├─> Create ISO-8583 response with code 91
           │   ├─> source.send(response)
           │   └─> Return (request not queued)
           │
           └─> If not throttled:
               ├─> Create Context with REQUEST and SOURCE
               ├─> space.out("txnmgr", ctx, 60000)
               └─> TransactionManager processes request
```

### State Transitions

```
Normal Operation (queue < 100)
    ↓ queue size ≥ 100
Throttled Mode (rejecting requests with code 91)
    ↓ queue size ≤ 50
Normal Operation (accepting requests)
```

## Testing

### Adjust Thresholds for Testing

For easier testing, lower the thresholds in `15_queue_monitor.xml`:

```xml
<property name="high-threshold" value="10" />
<property name="low-threshold" value="5" />
```

### Run Load Test

1. **Start the server:**
   ```bash
   ./gradlew installApp
   build/install/jpos-atm-switch/bin/q2
   ```

2. **In another terminal, run load test:**
   ```bash
   # 20 threads, 10 requests each = 200 total requests
   ./gradlew run --args="com.example.atm.LoadTestClient 20 10"
   ```

3. **Watch the logs for:**
   ```
   Queue size (10) reached high threshold (10). Enabling throttle mode.
   Request rejected (system throttled): MTI=0200
   Queue size (5) dropped to low threshold (5). Disabling throttle mode.
   ```

### Load Test Parameters

```bash
./gradlew run --args="com.example.atm.LoadTestClient [threads] [requests-per-thread]"
```

Example:
```bash
# Heavy load: 50 threads × 20 requests = 1000 requests
./gradlew run --args="com.example.atm.LoadTestClient 50 20"
```

## Monitoring

### Log Messages

**Normal operation:**
```
Queue size: 25 (normal operation)
```

**Throttle activated:**
```
Queue size (100) reached high threshold (100). Enabling throttle mode.
Request rejected (system throttled): MTI=0200
```

**Throttle deactivated:**
```
Queue size (50) dropped to low threshold (50). Disabling throttle mode.
```

## Performance Tuning

### TransactionManager Settings

File: `src/dist/deploy/20_txnmgr.xml`

```xml
<property name="sessions" value="2" />        <!-- Initial worker threads -->
<property name="max-sessions" value="128" />  <!-- Maximum worker threads -->
```

- Increase `sessions` for higher baseline throughput
- Increase `max-sessions` for handling burst traffic
- Monitor queue size to find optimal values

### Queue Thresholds

- **High threshold**: Should be higher than typical peak queue size
- **Low threshold**: Should be ~50% of high threshold to prevent flapping
- **Check interval**: 1000ms (1 second) is reasonable; lower values = faster response but more CPU

## Choosing an Approach

| Use Case | Recommended Approach |
|----------|---------------------|
| Production system with external clients | **Approach 2** (Graceful ISO-8583) |
| Internal testing/development | Either approach works |
| Need to completely stop traffic | Approach 1 (TCP rejection) |
| Client apps expect ISO-8583 responses | **Approach 2** (Graceful ISO-8583) |

**Recommendation:** Use **Approach 2 (Graceful)** for production systems as it provides better client experience and follows ISO-8583 standards.
