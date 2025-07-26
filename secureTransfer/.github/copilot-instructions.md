<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

This project is a Spring Boot REST API for secure file transfer. Use H2 only for user authentication and public key storage. Do not store files on the server. Implement endpoints for registration, login (JWT), public key management, and file transfer using hybrid encryption (AES for file, AES key encrypted with receiver's RSA public key).
