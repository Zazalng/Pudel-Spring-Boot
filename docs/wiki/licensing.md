# Licensing Guide

Understanding Pudel's licensing model for plugin developers.

## Overview

Pudel uses a **dual-license model with a Plugin Exception**:

| Module | License | Purpose |
|--------|---------|---------|
| `pudel-api` | **MIT** | Plugin Development Kit |
| `pudel-core` | AGPL v3 + Plugin Exception | Bot core |
| `pudel-model` | AGPL v3 | AI/ML components |

---

## The Plugin Exception

The key to commercial plugin development is the **Plugin Exception** in `PLUGIN_EXCEPTION`:

> "As a special exception, plugins that use the Pudel Plugin API (pudel-api)
> to extend Pudel's functionality are NOT considered derivative works of
> Pudel, even when running in the same Java Virtual Machine process."

### What This Means

✅ **You CAN:**
- Create proprietary/closed-source plugins
- Sell plugins commercially
- Create exclusive plugins for specific clients
- Keep your source code completely private
- Use any license for your plugin
- Bundle with other proprietary code

❌ **You CANNOT:**
- Modify `pudel-core` without releasing changes (AGPL applies)
- Claim plugins are part of core Pudel
- Remove attribution from pudel-api

---

## License Types for Plugins

### Open Source Plugins

Publish on marketplace with source code link.

**Requirements:**
- Source code accessible (GitHub, GitLab, etc.)
- Any OSI-approved license (MIT, Apache, GPL, etc.)

**Benefits:**
- Community contributions
- Higher trust from users
- Featured in marketplace

### Proprietary Plugins

Keep source code private, distribute binaries only.

**Requirements:**
- Only use `pudel-api` interfaces
- Don't include `pudel-core` code
- Load via plugin system

**Benefits:**
- Protect intellectual property
- Sell without source disclosure
- Client-specific customizations

### Commercial Plugins

Sell plugins for profit.

**Requirements:**
- Clear license agreement with customers
- No `pudel-core` modifications
- Proper attribution to pudel-api

**Benefits:**
- Revenue generation
- Per-seat licensing
- Enterprise support tiers

---

## Legal Basis

This approach follows established precedents:

| Project | Exception | Allows |
|---------|-----------|--------|
| **OpenJDK** | Classpath Exception | Proprietary Java apps |
| **GCC** | Runtime Library Exception | Proprietary compiled programs |
| **WordPress** | Plugin Policy | Proprietary WordPress plugins |

The FSF (creators of GPL) explicitly supports copyright holders granting additional permissions.

---

## What Qualifies as a Plugin?

A compliant plugin must:

1. **Only depend on `pudel-api`** (MIT licensed)
2. **Not include code from `pudel-core`**
3. **Be loaded dynamically** via plugin system
4. **Interact via public API** interfaces

---

## For Commercial Developers

### Recommended License Structure

Create a license agreement that specifies:

```
MY PLUGIN LICENSE AGREEMENT

1. LICENSE GRANT
   - Single installation per license key
   - Non-transferable without consent
   - No reverse engineering

2. RESTRICTIONS
   - No redistribution
   - No sub-licensing
   - Must maintain attribution

3. SUPPORT
   - 12 months of updates
   - Email support during business hours

4. LIABILITY
   - Provided "as is"
   - No warranty of fitness
```

### License Validation (Optional)

```java
@onEnable
public void onEnable(PluginContext context) {
    if (!validateLicense()) {
        context.log("error", "Invalid license key!");
        throw new RuntimeException("License validation failed");
    }
    // Continue initialization
}

private boolean validateLicense() {
    // Check license file, server, or key
    String key = System.getenv("MY_PLUGIN_LICENSE");
    return licenseService.validate(key);
}
```

### Code Obfuscation (Optional)

Protect your code with ProGuard:

```xml
<plugin>
    <groupId>com.github.wvengen</groupId>
    <artifactId>proguard-maven-plugin</artifactId>
    <version>2.6.0</version>
    <configuration>
        <proguardInclude>proguard.conf</proguardInclude>
    </configuration>
</plugin>
```

---

## FAQ

### Q: Do I need to share my plugin's source code?

**No.** The Plugin Exception explicitly states plugins are NOT derivative works.

### Q: My plugin runs in the same JVM as AGPL code - is that a problem?

**No.** The Plugin Exception specifically addresses this:
> "...even though such plugin may run in the same Java Virtual Machine process as pudel-core."

### Q: Can I charge for my plugins?

**Yes.** Any price point: one-time, subscription, or enterprise licensing.

### Q: What if I modify pudel-core?

Modifications to `pudel-core` (not `pudel-api`) are subject to AGPL v3 and must be open source. The Plugin Exception does NOT apply to core modifications.

### Q: Is this Plugin Exception legally valid?

**Yes.** Copyright holders can grant additional permissions. This is exactly what OpenJDK, GCC, and other major projects do.

---

## Attribution

When using `pudel-api`, include attribution:

```
This plugin uses the Pudel Plugin API (pudel-api)
Copyright (c) 2026 World Standard Group
Licensed under the MIT License
```

---

## Publishing to Marketplace

### Open Source Plugins

1. Create repository with source code
2. Add clear license file
3. Submit via API or dashboard
4. Provide source URL

### Commercial Plugins

1. Create listing with description
2. Set `isCommercial: true`
3. Set `price_cents` (0 for contact-based pricing)
4. Provide contact email
5. Handle sales externally

---

## Support

For licensing questions:
- Create an issue on the repository
- Contact project maintainers
- Consult a qualified attorney for specific legal advice

---

*Build with confidence!* ⚖️
