# Connection Management Strategy

## Problem Statement

The ATM simulator had connection management issues:

1. **Timeout disconnects without events**: Channel timed out after 5 minutes of inactivity and automatically reconnected without triggering sign-on
2. **No re-sign-on after reconnection**: AutoSignOnService exited after first successful sign-on, so reconnections didn't trigger re-authentication
3. **Server loses terminal registration**: Server didn't know terminal reconnected, causing transaction failures

## Solution: Persistent Connection with Heartbeat (jPOS Best Practice)

Based on jPOS documentation and community best practices, we implemented a **persistent connection with periodic heartbeat messages** approach.

### Why This Approach?

From jPOS community:
> "Making the timeout very big will have a negative impact... causing reconnection during real authorization messages rather than proactively. Implementing regular echo/heartbeat messages (like 0800 network management messages) helps detect connection problems before they affect real transactions."

> "Since the MUX pool forces an echo on each channel every three minutes, not getting 'receive' activity in five minutes raises the possibility of a 'hung' line."

**Benefits:**
- ✅ Detects connection problems proactively (before real transactions fail)
- ✅ Keeps terminal registered on server
- ✅ Standard banking industry practice
- ✅ Lower latency (no reconnection delay)
- ✅ Automatic re-sign-on if connection drops

## Implementation

### 1. AutoSignOnService Enhancement

**File**: `src/main/java/com/example/atm/jpos/service/AutoSignOnService.java`

**Changes:**
- Continuously monitors for reconnections (doesn't exit after first sign-on)
- Tracks session ID to detect when channel reconnects
- Automatically triggers re-sign-on when reconnection detected
- Uses `space.rd()` (read) instead of `space.in()` (take) to peek at ready indicator

**Key Logic:**
```java
// Track session to detect reconnections
String lastSessionId = null;

while (running) {
    Object ready = space.rd(readyIndicator, 30000); // Peek, don't consume
    String currentSessionId = ready.toString();

    boolean isReconnection = lastSessionId != null &&
                           !lastSessionId.equals(currentSessionId);

    if (isReconnection) {
        log.warn("Channel RECONNECTION detected - re-signing on");
        signOnService.signOn();
    }
}
```

### 2. EchoService (NEW)

**File**: `src/main/java/com/example/atm/jpos/service/EchoService.java`

**Purpose**: Sends periodic echo/heartbeat messages to keep connection alive

**Configuration**: `src/main/resources/deploy/20_echo.xml`
```xml
<echo class="com.example.atm.jpos.service.EchoService" logger="Q2">
    <property name="interval" value="180000"/> <!-- 3 minutes -->
</echo>
```

**Behavior:**
- Sends ISO-8583 0800 network management message every 3 minutes
- Processing code: 301 (echo test)
- Only sends if MUX is connected AND terminal is signed on
- Logs warnings if echo fails (connection problems detected)

**Message Format:**
```
MTI: 0800 (Network Management Request)
Field 3: 301000 (Echo test)
Field 7: Transmission date/time
Field 11: STAN
Field 12: Local time
Field 13: Local date
Field 41: Terminal ID
Field 70: 301 (Echo test code)
```

### 3. Channel Configuration Update

**File**: `src/main/resources/application.yml`

**Before:**
```yaml
jpos:
  channel:
    timeout: 300000  # 5 minutes - causes disconnects
```

**After:**
```yaml
jpos:
  channel:
    timeout: 0  # No socket timeout - using heartbeat echo messages instead
```

**Rationale:**
- Socket timeout removed since heartbeat keeps connection alive
- Echo every 3 minutes prevents idle timeout
- Connection stays persistent unless network failure occurs
- If network fails, ChannelAdaptor reconnects and AutoSignOnService detects it

## Connection Lifecycle

### Normal Operation
```
1. Application starts
2. ChannelAdaptor connects to server
3. Places "atm-channel.ready" in Space
4. AutoSignOnService detects ready indicator
5. Sends sign-on message (0800/001)
6. Terminal registered on server
7. EchoService starts sending heartbeat every 3 minutes
8. Connection stays alive indefinitely
```

### Reconnection Scenario
```
1. Network failure occurs
2. ChannelAdaptor detects disconnection
3. Waits 10 seconds (reconnect-delay)
4. Reconnects to server
5. Places new "atm-channel.ready" in Space (different session ID)
6. AutoSignOnService detects session change
7. Automatically sends re-sign-on message (0800/001)
8. Terminal re-registered on server
9. EchoService resumes sending heartbeat
10. Normal operation continues
```

## Testing

To verify reconnection with automatic re-sign-on:

1. **Start ATM simulator and server**
   ```bash
   # Terminal 1: Start server
   cd spring-jpos-atm-server
   mvn spring-boot:run

   # Terminal 2: Start simulator
   cd spring-jpos-atm-simulator
   mvn spring-boot:run
   ```

2. **Observe initial sign-on in logs:**
   ```
   AutoSignOnService: Initial channel ready detected
   AutoSignOnService: Initial sign-on completed successfully
   EchoService: Sending echo/heartbeat message (0800/301)
   ```

3. **Force reconnection (kill server and restart):**
   ```bash
   # Kill server (Ctrl+C), then restart
   mvn spring-boot:run
   ```

4. **Verify automatic re-sign-on:**
   ```
   ChannelAdaptor: Connection lost, reconnecting...
   ChannelAdaptor: Connected to server
   AutoSignOnService: Channel RECONNECTION detected (session changed)
   AutoSignOnService: RE-SIGN-ON completed successfully after reconnection
   EchoService: Echo successful - connection alive
   ```

## Configuration Options

### Echo Interval
Adjust heartbeat frequency in `deploy/20_echo.xml`:
```xml
<property name="interval" value="180000"/> <!-- milliseconds -->
```

**Recommended values:**
- Production: 180000 (3 minutes)
- Testing: 60000 (1 minute)
- Minimum: 30000 (30 seconds)

### Reconnection Delay
Adjust reconnection wait time in `deploy/12_channel.xml`:
```xml
<reconnect-delay>10000</reconnect-delay> <!-- milliseconds -->
```

## Troubleshooting

### Problem: Echo messages timing out
**Symptoms:**
```
EchoService: Echo timeout - connection may be dead
```

**Solutions:**
- Check server is running
- Verify network connectivity
- Check server logs for 0800/301 handling
- Increase echo interval if network is slow

### Problem: Re-sign-on not happening after reconnect
**Symptoms:**
```
Transaction failed: Terminal not signed on
```

**Solutions:**
- Check AutoSignOnService logs for "RECONNECTION detected"
- Verify ChannelAdaptor is placing ready indicator in Space
- Check SignOnService.isSignedOn() logic
- Verify server accepts 0800/001 messages

### Problem: Too many echo messages
**Symptoms:**
```
Server logs flooded with echo messages
```

**Solutions:**
- Increase echo interval (default 3 minutes is standard)
- Verify EchoService isn't creating multiple threads
- Check only one EchoService instance is running

## References

- [jPOS FAQ: LogonManager](https://jpos.org/faq/logon_manager.html)
- [jPOS Blog: You Want a Timeout](https://jpos.org/blog/2014/04/you-want-a-timeout/)
- [jPOS ChannelAdaptor Documentation](https://github.com/jpos/jPOS/blob/tail/doc/src/asciidoc/ch08/channel_adaptor.adoc)
- [jPOS Google Group: QMUX Timeout Best Practices](https://groups.google.com/g/jpos-users/c/JALF2SJUDjM)

## Summary

The improved connection management strategy ensures:
1. ✅ Persistent connection with proactive health monitoring
2. ✅ Automatic re-sign-on after any reconnection
3. ✅ Terminal always registered on server
4. ✅ Early detection of connection problems
5. ✅ Industry-standard banking practice
6. ✅ Zero downtime for users during reconnection

This follows jPOS community best practices and prevents transaction failures due to connection issues.
