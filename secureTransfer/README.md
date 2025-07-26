# SecureTransfer Spring Boot Backend

This project is a secure file transfer backend using Spring Boot. It features:

- User registration and login (JWT authentication)
- H2 database for user credentials and RSA public key storage
- REST endpoints for public key management and secure file transfer
- Hybrid encryption: AES for file, AES key encrypted with receiver's RSA public key
- Server does not store files, only relays them

## How to Run

1. Ensure you have Java 17+ and Gradle installed.
2. Run the backend:
   ```
   ./gradlew bootRun
   ```
3. Access H2 console at `/h2-console` (for development only).

## Next Steps
- Implement authentication and user management
- Add endpoints for public key management
- Add secure file transfer endpoints

---
Frontend (JavaFX) will be developed after backend is complete.
