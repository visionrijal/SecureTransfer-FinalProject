# Backend API Endpoints Reference

## AuthController

### POST /api/auth/register
- **Body:**
  ```json
  {
    "username": "string",
    "password": "string",
    "publicKey": "string (PEM)"
  }
  ```
- **Success Response:**
  - 200 OK
  - Body: User object (JSON)
- **Error Response:**
  - 400 Bad Request
  - Body:
    ```json
    { "error": "Username already exists", "code": "USERNAME_EXISTS" }
    ```

### POST /api/auth/login
- **Body:**
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **Success Response:**
  - 200 OK
  - Body:
    ```json
    { "token": "jwt-token-string" }
    ```
- **Error Response:**
  - 401 Unauthorized
  - Body:
    ```json
    { "error": "Invalid credentials", "code": "INVALID_CREDENTIALS" }
    ```

## KeyController

### GET /api/keys/{username}
- **Success Response:**
  - 200 OK
  - Body: Public key string (PEM)
- **Error Response:**
  - 404 Not Found
  - Body:
    ```json
    { "error": "User not found", "code": "USER_NOT_FOUND" }
    ```

## FileTransferController

### POST /api/transfer
- **Headers:**
  - Authorization: Bearer <jwt-token>
- **Body:**
  ```json
  {
    "receiver": "string",
    "encryptedFile": "base64-string",
    "encryptedAesKey": "base64-string"
  }
  ```
- **Success Response:**
  - 200 OK
  - Body:
    ```json
    {
      "from": "sender-username",
      "to": "receiver-username",
      "encryptedFile": "base64-string",
      "encryptedAesKey": "base64-string"
    }
    ```
- **Error Response:**
  - 400 Bad Request
  - Body:
    ```json
    { "error": "Receiver not found", "code": "RECEIVER_NOT_FOUND" }
    ```

### POST /api/transfer/initiate
- **Headers:**
  - Authorization: Bearer <jwt-token>
- **Body:**
  ```json
  {
    "code": "string"
  }
  ```
- **Success Response:**
  - 200 OK
  - Body: Transfer session info (see backend model)

---

_This file is auto-generated from backend source for frontend reference. Update as backend evolves._
