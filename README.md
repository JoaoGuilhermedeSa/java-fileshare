# FileShare

A self-hosted, encrypted file storage REST API built with Java 26 and Spring Boot 4. Files are encrypted with **AES-256-GCM** before touching disk — the server never stores plaintext bytes.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 26+ |
| Maven | 3.9+ |
| Docker & Docker Compose | any recent version |
| PostgreSQL | 17 (provided via Docker) |

> **Note:** The app uses Java 21+ virtual threads (Project Loom) via `spring.threads.virtual.enabled=true`. Java 26 is required to compile.

---

## Getting Started

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts a `postgres:17-alpine` container on port `5432` with:
- Database: `fileshare`
- User: `fileshare`
- Password: `fileshare`

Flyway runs migrations automatically on startup — no manual schema setup needed.

### 2. Configure secrets (important!)

Before running in any non-throwaway environment, update `src/main/resources/application.yml`:

```yaml
fileshare:
  encryption:
    passphrase: "change-me-in-production-use-a-long-random-string"
    salt: "dGhpcyBpcyBhIHNhbHQ="  # base64-encoded 16-byte salt
```

Generate safe values with:

```bash
# Passphrase
openssl rand -base64 32

# Salt
openssl rand -base64 16
```

You can also override these via environment variables at runtime:

```bash
export FILESHARE_ENCRYPTION_PASSPHRASE="your-passphrase"
export FILESHARE_ENCRYPTION_SALT="your-salt"
```

### 3. Build and run

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

---

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `fileshare.storage.root-dir` | `~/fileshare-data` | Root directory for encrypted files on disk |
| `fileshare.encryption.passphrase` | *(must change)* | PBKDF2 passphrase for key derivation |
| `fileshare.encryption.salt` | *(must change)* | Base64-encoded 16-byte PBKDF2 salt |
| `server.port` | `8080` | HTTP port |
| `spring.servlet.multipart.max-file-size` | `10GB` | Max individual file size |

Override any property with the equivalent environment variable (e.g. `FILESHARE_STORAGE_ROOT_DIR`).

---

## API Reference

All endpoints are under `/api/v1/files`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/files` | Upload and encrypt a file |
| `GET` | `/api/v1/files` | List files (paginated) |
| `GET` | `/api/v1/files/search` | Search files |
| `GET` | `/api/v1/files/{id}` | Get file metadata |
| `GET` | `/api/v1/files/{id}/download` | Decrypt and download |
| `DELETE` | `/api/v1/files/{id}` | Delete file |
| `PUT` | `/api/v1/files/{id}/tags` | Replace tag set |

### Upload a file

```bash
curl -X POST http://localhost:8080/api/v1/files \
  -F "file=@report.pdf" \
  -F "directory=/documents/2024" \
  -F "tag.project=alpha" \
  -F "tag.owner=alice"
```

### List files

```
GET /api/v1/files?page=0&size=20
```

Query parameters:
- `page` — zero-based page number (default: 0)
- `size` — items per page (default: 20, max: 50)

### Search files

```
GET /api/v1/files/search?q=report&directory=/documents&from=2024-01-01T00:00:00Z&to=2024-12-31T23:59:59Z&tag.project=alpha
```

Query parameters:
- `q` — filename substring (case-insensitive LIKE)
- `directory` — virtual directory prefix
- `tag.<key>=<value>` — one or more tag filters
- `from` / `to` — ISO-8601 upload date range
- `page` — zero-based page number (default: 0)
- `size` — items per page (default: 20, max: 50)

---

## Frontend UI

A built-in single-page UI is served at **`http://localhost:8080`** — no separate install needed. It's a zero-dependency HTML/CSS/JS file bundled as a static resource.

### Features

- **Drag-and-drop upload** — drop one or more files directly onto the drop zone, or click to browse
- **Virtual directories** — organize files into logical paths (e.g. `/documents/2024`)
- **Tag builder** — attach arbitrary key-value tags to files before uploading
- **File table** — sortable list with name, directory, size, upload date, and tags
- **Search** — filter by filename, directory, and date range in real time
- **Download** — files are decrypted on-the-fly and streamed to the browser
- **Delete** — with confirmation dialog
- **Pagination** — handles large file counts with a smart page navigator
- **Toast notifications** — success/error feedback for every action

Open `http://localhost:8080` after starting the server.

---

## Encryption Details

- **Algorithm:** AES-256-GCM (authenticated encryption)
- **Key derivation:** PBKDF2WithHmacSHA256, 310,000 iterations, 256-bit key
- **Chunk size:** 1 MiB — files are split into fixed-size chunks, each encrypted independently
- **IV:** 12 bytes, randomly generated **per chunk** — nonce reuse within a file is structurally impossible
- **Storage layout:** `{root-dir}/{2-char-prefix}/{uuid}.enc` — the two-char prefix (first two hex chars of the UUID) prevents hot directories at scale

### On-disk format

Each `.enc` file uses a simple self-describing binary frame:

```
Header:
  version     1 byte   (currently 1)
  chunk_size  4 bytes  (big-endian int — 1,048,576)

Per chunk (repeated until EOF):
  enc_len     4 bytes  (big-endian int — length of the following frame)
  iv         12 bytes  (random nonce for this chunk)
  ciphertext  N bytes  (plaintext + 16-byte GCM authentication tag)
```

### Memory profile

Only one chunk is live in the JVM heap at a time during both upload and download. Peak memory per active request is roughly `2 × 1 MiB` (one plaintext buffer + one ciphertext buffer). With 100 concurrent virtual-thread requests that is ~200 MB — independent of file size.

Upload uses a `DigestInputStream` to compute the SHA-256 checksum of the plaintext in a single streaming pass, without a separate read. Download pipes the decrypted stream directly into the HTTP response via `StreamingResponseBody`, so the browser starts receiving bytes before the full file is decrypted.

---

## Running Tests

```bash
mvn test
```

The test suite covers both the legacy byte-array API and the streaming chunked-AEAD API:

| Test | What it verifies |
|---|---|
| `encryptThenDecryptReturnsOriginal` | Basic encrypt/decrypt round-trip |
| `encryptedBytesAreDifferentFromPlaintext` | Ciphertext differs from plaintext |
| `tamperingCipherTextThrowsOnDecrypt` | GCM tag catches tampering |
| `sameInputProducesDifferentCiphertexts` | Random IV per encryption |
| `encryptStreamThenDecryptStreamReturnsOriginal` | Streaming round-trip |
| `encryptStreamHandlesEmptyInput` | Empty file produces valid (header-only) stream |
| `encryptStreamHandlesDataSpanningMultipleChunks` | 2 MiB + 7 bytes crosses chunk boundaries correctly |
| `encryptStreamProducesDifferentCiphertextsForSameInput` | Random IV per chunk |
| `decryptStreamThrowsOnTamperedChunk` | GCM tag catches per-chunk tampering |
| `decryptStreamThrowsOnUnknownVersion` | Format version mismatch is rejected |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 26 |
| Framework | Spring Boot 4.0 |
| Build | Maven |
| Database | PostgreSQL 17 |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Encryption | JDK `javax.crypto` — AES-256-GCM |
| Concurrency | Virtual threads (Project Loom) |
| UUID generation | java-uuid-generator 4.3 |
