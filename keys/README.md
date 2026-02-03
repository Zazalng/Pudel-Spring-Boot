# JWT RSA Keys

This directory should contain RSA key files for JWT token signing and verification.

## Generate RSA Key Pair

Run the following commands to generate the keys:

### Generate Private Key (PKCS#8 format)
```bash
openssl genpkey -algorithm RSA -out pv.key -pkeyopt rsa_keygen_bits:2048
```

### Extract Public Key
```bash
openssl rsa -pubout -in pv.key -out pb.key
```

## File Structure

After generating, this directory should contain:
- `pv.key` - RSA private key (PKCS#8 PEM format) - **KEEP SECRET!**
- `pb.key` - RSA public key (X.509 PEM format)

## Important Security Notes

1. **Never commit private keys to version control!**
2. Add `pv.key` and `pb.key` to your `.gitignore`
3. Use environment variables to specify custom paths in production
4. Ensure proper file permissions (private key should be readable only by the application user)

## Configuration

The application expects:
- Default private key path: `keys/pv.key` (local) or `/app/keys/pv.key` (Docker)
- Default public key path: `keys/pb.key` (local) or `/app/keys/pb.key` (Docker)

Override with environment variables in `.env`:
```bash
JWT_PRIVATE_KEY_FILE=pv.key
JWT_PUBLIC_KEY_FILE=pb.key
JWT_PRIVATE_KEY_PATH=keys/pv.key
JWT_PUBLIC_KEY_PATH=keys/pb.key
```

## Docker Usage

When running with Docker, the keys directory is mounted as a read-only volume:

```yaml
services:
  pudel:
    volumes:
      - ./keys:/app/keys:ro  # Mount local keys directory (read-only for security)
    environment:
      JWT_PRIVATE_KEY_PATH: /app/keys/${JWT_PRIVATE_KEY_FILE:-pv.key}
      JWT_PUBLIC_KEY_PATH: /app/keys/${JWT_PUBLIC_KEY_FILE:-pb.key}
```

### Steps for Docker Deployment:

1. Generate keys locally in the `keys/` directory:
   ```bash
   cd keys
   openssl genpkey -algorithm RSA -out pv.key -pkeyopt rsa_keygen_bits:2048
   openssl rsa -pubout -in pv.key -out pb.key
   ```

2. Ensure proper file permissions:
   ```bash
   chmod 600 keys/pv.key
   chmod 644 keys/pb.key
   ```

3. Start Docker containers:
   ```bash
   docker-compose up -d
   ```

The keys will be automatically mounted into the container at `/app/keys/`.

