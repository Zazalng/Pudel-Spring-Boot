# Voice Features & DAVE Protocol

Guide to voice features and Discord Audio/Voice Encryption (DAVE).

## Overview

Starting **March 1st, 2026**, Discord requires all voice connections to use End-to-End Encryption (E2EE) via the **DAVE protocol**.

Pudel fully supports DAVE through its plugin architecture.

---

## Why DAVE?

Discord [announced](https://discord.com/developers/docs/change-log#deprecating-non-e2ee-voice-calls) that non-encrypted voice connections will be deprecated:

- All bots using voice must implement DAVE
- Connections without DAVE will **fail** after the deadline
- This ensures voice privacy and security

---

## Requirements

### For JDAVE (Recommended)

| Requirement | Version |
|-------------|---------|
| Java | 25+ |
| JDAVE Library | 0.1.2+ |

### For libdave-jvm (Broader Compatibility)

| Requirement | Version |
|-------------|---------|
| Java | 8+ |
| Native Library | Platform-specific |

---

## How Pudel Handles DAVE

Pudel uses a plugin-based approach:

1. **DAVE Provider Registration**: Audio plugins register DAVE implementations
2. **Automatic Selection**: Best provider is selected based on Java version
3. **Transparent Connection**: Voice connections use DAVE automatically

```
Plugin (JDAVE) → VoiceManager → JDA → Discord
                      ↓
                 DAVE Encryption
```

---

## Using Voice in Plugins

### Basic Voice Connection

```java
@Override
public void onEnable(PluginContext context) {
    VoiceManager voice = context.getVoiceManager();
    
    context.registerCommand("join", ctx -> {
        long guildId = ctx.getGuild().getIdLong();
        
        // Always check DAVE first!
        if (!voice.isDAVEAvailable(guildId)) {
            ctx.reply("⚠️ Voice encryption not available. " +
                     "Please install a DAVE provider plugin.");
            return;
        }
        
        // Get user's voice channel
        VoiceChannel channel = ctx.getMember()
            .getVoiceState().getChannel().asVoiceChannel();
        
        if (channel == null) {
            ctx.reply("❌ Join a voice channel first!");
            return;
        }
        
        // Connect
        voice.connect(guildId, channel.getIdLong())
            .thenAccept(status -> {
                switch (status) {
                    case CONNECTED -> ctx.reply("✅ Connected!");
                    case DAVE_REQUIRED -> ctx.reply("❌ DAVE required");
                    case DAVE_ERROR -> ctx.reply("❌ DAVE error");
                    case NO_PERMISSION -> ctx.reply("❌ No permission");
                    default -> ctx.reply("❌ Connection failed");
                }
            });
    });
}
```

### Sending Audio

```java
public class MyAudioProvider implements AudioProvider {
    private final Queue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();
    
    @Override
    public boolean canProvide() {
        return !audioQueue.isEmpty();
    }
    
    @Override
    public byte[] provide20MsAudio() {
        return audioQueue.poll();
    }
    
    @Override
    public boolean isOpus() {
        return true;  // Audio is Opus-encoded
    }
    
    @Override
    public void close() {
        audioQueue.clear();
    }
    
    public void queueAudio(byte[] opusFrame) {
        audioQueue.offer(opusFrame);
    }
}

// Use it
voice.setAudioProvider(guildId, new MyAudioProvider());
```

### Receiving Audio

```java
public class MyAudioReceiver implements AudioReceiver {
    
    @Override
    public void receiveUserAudio(long userId, byte[] audioData) {
        // Process Opus-encoded audio from user
    }
    
    @Override
    public void onUserSpeaking(long userId, boolean speaking) {
        // User started/stopped speaking
    }
    
    @Override
    public void close() {
        // Cleanup
    }
}

voice.setAudioReceiver(guildId, new MyAudioReceiver());
```

---

## Providing Custom DAVE

If you need to bundle DAVE in your plugin:

```java
public class MyDAVEProvider implements DAVEProvider {
    
    @Override
    public String getName() {
        return "MyDAVE";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public boolean isAvailable() {
        return Runtime.version().feature() >= 25;
    }
    
    @Override
    public int getRequiredJavaVersion() {
        return 25;
    }
    
    @Override
    public void initialize() throws DAVEException {
        // Initialize JDAVE or libdave
    }
    
    @Override
    public void shutdown() {
        // Cleanup
    }
    
    @Override
    public Object getNativeImplementation() {
        return jdaveSessionFactory;
    }
}

// Register
voice.registerDAVEProvider("my-plugin", new MyDAVEProvider());
```

---

## LavaPlayer Integration

For music bots, use LavaPlayer with Pudel:

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
AudioSourceManagers.registerRemoteSources(playerManager);

AudioPlayer player = playerManager.createPlayer();

// Bridge to Pudel
public class LavaPlayerBridge implements AudioProvider {
    private final AudioPlayer player;
    private final MutableAudioFrame frame;
    private final ByteBuffer buffer;
    
    public LavaPlayerBridge(AudioPlayer player) {
        this.player = player;
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }
    
    @Override
    public boolean canProvide() {
        return player.provide(frame);
    }
    
    @Override
    public byte[] provide20MsAudio() {
        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        buffer.clear();
        return data;
    }
    
    @Override
    public boolean isOpus() {
        return true;
    }
}

voice.setAudioProvider(guildId, new LavaPlayerBridge(player));
```

---

## Dependencies

Add to your plugin's `pom.xml`:

```xml
<!-- JDAVE (Java 25+) -->
<dependency>
    <groupId>club.minnced</groupId>
    <artifactId>jdave</artifactId>
    <version>0.1.2</version>
</dependency>

<!-- LavaPlayer -->
<dependency>
    <groupId>dev.arbjerg</groupId>
    <artifactId>lavaplayer</artifactId>
    <version>2.2.6</version>
</dependency>

<!-- YouTube Source -->
<dependency>
    <groupId>dev.lavalink.youtube</groupId>
    <artifactId>common</artifactId>
    <version>1.16.0</version>
</dependency>
```

---

## Connection Status Codes

| Status | Meaning |
|--------|---------|
| `CONNECTED` | Successfully connected with DAVE |
| `DAVE_REQUIRED` | Deadline passed, no DAVE available |
| `DAVE_ERROR` | DAVE initialization failed |
| `NO_PERMISSION` | Missing voice permissions |
| `CHANNEL_NOT_FOUND` | Channel doesn't exist |
| `ERROR` | Generic connection error |

---

## Troubleshooting

### "DAVE implementation required"

**Cause:** No DAVE provider registered after deadline.

**Solution:**
- Ensure default plugins are loaded
- Check Java 25+ is installed
- Verify JDAVE library is present

### "Failed to load native libdave"

**Cause:** Native library not found.

**Solution:**
- Check platform compatibility (Win/Linux/Mac)
- Verify library is in `java.library.path`
- Match architecture (x64/arm64)

### "JDAVE requires Java 25"

**Cause:** Running older Java version.

**Solution:**
- Upgrade to Java 25+
- Or use libdave-jvm (Java 8+)

### Audio Not Playing

**Checklist:**
1. `canProvide()` returning `true`?
2. Audio is Opus-encoded?
3. `setAudioProvider()` called after connect?
4. Bot has speak permission?

---

## Timeline

| Date | Event |
|------|-------|
| 2024 | Discord announces DAVE |
| Late 2024 | libdave C-interface released |
| 2025 | JDA 6 adds DAVE support |
| **March 1, 2026** | **DAVE Required** |

---

## Best Practices

1. **Always check DAVE** before connecting
2. **Handle all status codes** gracefully
3. **Clean up on disconnect** - unset providers
4. **Log DAVE status** at plugin startup
5. **Test before deadline** - ensure it works

---

## Resources

- [Discord DAVE Documentation](https://discord.com/developers/docs/topics/voice-connections#dave-protocol)
- [JDAVE GitHub](https://github.com/MinnDevelopment/jdave)
- [JDA Audio Documentation](https://jda.wiki/using-jda/audio/)
- [LavaPlayer Guide](https://github.com/lavalink-devs/lavaplayer)

---

*Voice features ready for the future!* 🎙️
