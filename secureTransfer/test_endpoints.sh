#!/bin/bash

# Register sender
LOGFILE="/tmp/securetransfer_test.log"
echo "Registering sender..." | tee -a "$LOGFILE"
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"sender","password":"senderpass","publicKey":"senderkey"}' | tee -a "$LOGFILE"
echo -e "\n" | tee -a "$LOGFILE"

# Register receiver
echo "Registering receiver..." | tee -a "$LOGFILE"
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"receiver","password":"receiverpass","publicKey":"receiverkey"}' | tee -a "$LOGFILE"
echo -e "\n" | tee -a "$LOGFILE"

# Login sender and get token
echo "Logging in sender..." | tee -a "$LOGFILE"
SENDER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sender","password":"senderpass"}' | tee -a "$LOGFILE" | grep -o '"token":"[^"}]*"' | cut -d '"' -f4)
echo "Sender Token: $SENDER_TOKEN" | tee -a "$LOGFILE"

# Login receiver and get token
echo "Logging in receiver..." | tee -a "$LOGFILE"
RECEIVER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"receiver","password":"receiverpass"}' | tee -a "$LOGFILE" | grep -o '"token":"[^"}]*"' | cut -d '"' -f4)
echo "Receiver Token: $RECEIVER_TOKEN" | tee -a "$LOGFILE"

# Initiate transfer session by sender
echo "Initiating transfer session..." | tee -a "$LOGFILE"
# Generate a random 6-digit code
UNIQUE_CODE=$(printf "%06d" $(( RANDOM % 1000000 )))
SESSION_RESPONSE=$(curl -s -X POST http://localhost:8080/api/transfer/initiate \
  -H "Authorization: Bearer $SENDER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"'$UNIQUE_CODE'"}' | tee -a "$LOGFILE")
echo "Session Response: $SESSION_RESPONSE" | tee -a "$LOGFILE"
SESSION_ID=$(echo $SESSION_RESPONSE | grep -o '"id":[0-9]*' | cut -d ':' -f2)
if [ -z "$SESSION_ID" ]; then
  echo "ERROR: Session not created. Response: $SESSION_RESPONSE" | tee -a "$LOGFILE"
fi
echo "Session ID: $SESSION_ID" | tee -a "$LOGFILE"
echo "Session Code: $UNIQUE_CODE" | tee -a "$LOGFILE"
echo -e "\n" | tee -a "$LOGFILE"

# Claim transfer session by receiver
if [ -z "$SESSION_ID" ]; then
  echo "Skipping claim: No valid session ID." | tee -a "$LOGFILE"
else
  echo "Claiming transfer session..." | tee -a "$LOGFILE"
  CLAIM_RESPONSE=$(curl -s -X POST http://localhost:8080/api/transfer/verify \
    -H "Authorization: Bearer $RECEIVER_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"code":"'$UNIQUE_CODE'"}' | tee -a "$LOGFILE")
  echo "Claim Response: $CLAIM_RESPONSE" | tee -a "$LOGFILE"
fi
echo -e "\n" | tee -a "$LOGFILE"

# Sender gets session status to find out who claimed it
echo "Getting session status..." | tee -a "$LOGFILE"
if [ -z "$SESSION_ID" ]; then
  STATUS_RESPONSE=""
  echo "Skipping status: No valid session ID." | tee -a "$LOGFILE"
else
  STATUS_RESPONSE=$(curl -s -X GET http://localhost:8080/api/transfer/status/$UNIQUE_CODE \
    -H "Authorization: Bearer $SENDER_TOKEN" | tee -a "$LOGFILE")
  echo "Status Response: $STATUS_RESPONSE" | tee -a "$LOGFILE"
fi
echo -e "\n" | tee -a "$LOGFILE"

# Sender fetches receiver's public key using receiverUsername from status response
RECEIVER_USERNAME=""
SENDER_USERNAME=""
if [ -n "$STATUS_RESPONSE" ]; then
  RECEIVER_USERNAME=$(echo $STATUS_RESPONSE | grep -o '"receiverUsername":"[^"*]"' | cut -d '"' -f4)
  SENDER_USERNAME=$(echo $STATUS_RESPONSE | grep -o '"senderUsername":"[^"*]"' | cut -d '"' -f4)
fi
echo "Receiver Username from status: $RECEIVER_USERNAME" | tee -a "$LOGFILE"
echo "Sender Username from status: $SENDER_USERNAME" | tee -a "$LOGFILE"
if [ -n "$RECEIVER_USERNAME" ]; then
  echo "Fetching receiver public key..." | tee -a "$LOGFILE"
  RECEIVER_KEY_RESPONSE=$(curl -s -X GET http://localhost:8080/api/keys/$RECEIVER_USERNAME \
    -H "Authorization: Bearer $SENDER_TOKEN" | tee -a "$LOGFILE")
  echo "Receiver Public Key Response: $RECEIVER_KEY_RESPONSE" | tee -a "$LOGFILE"
else
  echo "Skipping public key fetch: No receiver username." | tee -a "$LOGFILE"
fi
echo -e "\n" | tee -a "$LOGFILE"



# Prepare atomic delivery test files (file + metadata)
echo "Creating atomic test files..." | tee -a "$LOGFILE"
TEST_FILE1="/tmp/testfile1.bin"
TEST_FILE2="/tmp/testfile2.bin"
head -c 1024 </dev/urandom > "$TEST_FILE1"
head -c 2048 </dev/urandom > "$TEST_FILE2"

# Create metadata+file JSON and base64 encode as 'encrypted blob'
for FILE in "$TEST_FILE1" "$TEST_FILE2"; do
  FILENAME=$(basename "$FILE")
  FILESIZE=$(stat -c %s "$FILE")
  METADATA_JSON="{\"filename\":\"$FILENAME\",\"size\":$FILESIZE}"
  FILEDATA_BASE64=$(base64 -w 0 "$FILE")
  ATOMIC_JSON="{\"metadata\":$METADATA_JSON,\"filedata\":\"$FILEDATA_BASE64\"}"
  echo "$ATOMIC_JSON" > "/tmp/atomic_$FILENAME.json"
  base64 -w 0 "/tmp/atomic_$FILENAME.json" > "/tmp/encrypted_$FILENAME.blob"
done
echo "Atomic test files created: /tmp/encrypted_testfile1.bin.blob, /tmp/encrypted_testfile2.bin.blob" | tee -a "$LOGFILE"
echo -e "\n" | tee -a "$LOGFILE"


# Send atomic files in one request
echo "Sending atomic files..." | tee -a "$LOGFILE"
if [ -z "$SESSION_ID" ]; then
  echo "Skipping send files: No valid session ID." | tee -a "$LOGFILE"
else
  SEND_FILES_RESPONSE=$(curl -s -X POST http://localhost:8080/api/transfer/send \
    -H "Authorization: Bearer $SENDER_TOKEN" \
    -F "sessionId=$UNIQUE_CODE" \
    -F "encryptedAesKey=dGVzdGtleTE=" \
    -F "encryptedAesKey=dGVzdGtleTI=" \
    -F "filename=testfile1.bin" \
    -F "filename=testfile2.bin" \
    -F "encryptedFile=@/tmp/encrypted_testfile1.bin.blob" \
    -F "encryptedFile=@/tmp/encrypted_testfile2.bin.blob" | tee -a "$LOGFILE")
  echo "Send Files Response: $SEND_FILES_RESPONSE" | tee -a "$LOGFILE"
fi
echo -e "\n" | tee -a "$LOGFILE"


# Fetch receiver inbox and verify files

echo "Fetching receiver inbox..." | tee -a "$LOGFILE"
INBOX_RESPONSE=$(curl -s -X GET "http://localhost:8080/api/transfer/inbox?receiver=receiver" \
  -H "Authorization: Bearer $RECEIVER_TOKEN" | tee -a "$LOGFILE")
echo "Inbox Response: $INBOX_RESPONSE" | tee -a "$LOGFILE"

# Extract file IDs and filenames from inbox
FILE_IDS=($(echo "$INBOX_RESPONSE" | grep -o '"id":[0-9]*' | cut -d ':' -f2))
FILENAMES=($(echo "$INBOX_RESPONSE" | grep -o '"filename":"[^"]*"' | cut -d '"' -f4))

# Download and verify each atomic file
for idx in ${!FILE_IDS[@]}; do
  FILE_ID=${FILE_IDS[$idx]}
  FILENAME=${FILENAMES[$idx]}
  BASE64_DATA=$(echo "$INBOX_RESPONSE" | grep -o '{[^}]*"id":'$FILE_ID'[^}]*}' | grep -o '"encryptedFileData":"[^"]*"' | cut -d '"' -f4)
  if [ -n "$BASE64_DATA" ]; then
    echo "$BASE64_DATA" | base64 -d > "/tmp/received_encrypted_$FILENAME.blob" 2>/dev/null
    # Extract file+metadata from atomic blob
    ATOMIC_JSON=$(base64 -d "/tmp/received_encrypted_$FILENAME.blob" 2>/dev/null)
    ORIG_ATOMIC_JSON=$(base64 -d "/tmp/encrypted_$FILENAME.blob" 2>/dev/null)
    # Compare atomic JSONs
    if [ "$ATOMIC_JSON" = "$ORIG_ATOMIC_JSON" ]; then
      echo "Atomic file $FILENAME send/receive test: SUCCESS (atomic data matches)" | tee -a "$LOGFILE"
    else
      echo "Atomic file $FILENAME send/receive test: FAILURE (atomic data does not match)" | tee -a "$LOGFILE"
    fi
  else
    echo "File $FILENAME not found in inbox response." | tee -a "$LOGFILE"
  fi
done
echo -e "\n" | tee -a "$LOGFILE"


# Delete each file and verify deletion


for FILE_ID in "${FILE_IDS[@]}"; do
  echo "Deleting file with id $FILE_ID from inbox..." | tee -a "$LOGFILE"
  DELETE_RESPONSE=$(curl -s -X DELETE http://localhost:8080/api/transfer/inbox/$FILE_ID \
    -H "Authorization: Bearer $RECEIVER_TOKEN" | tee -a "$LOGFILE")
  echo "Delete Response: $DELETE_RESPONSE" | tee -a "$LOGFILE"
done
echo -e "\n" | tee -a "$LOGFILE"

# Check audit logs for session
echo "Checking audit logs for session..." | tee -a "$LOGFILE"
AUDIT_LOGS=$(curl -s -X GET "http://localhost:8080/api/audit?sessionCode=$UNIQUE_CODE" \
  -H "Authorization: Bearer $SENDER_TOKEN" | tee -a "$LOGFILE")
echo "Audit logs for session $UNIQUE_CODE:" | tee -a "$LOGFILE"
echo "$AUDIT_LOGS" | tee -a "$LOGFILE"

# Notification endpoint tests
echo "Fetching notifications for sender..." | tee -a "$LOGFILE"
SENDER_NOTIFICATIONS=$(curl -s -X GET http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $SENDER_TOKEN" | tee -a "$LOGFILE")
echo "Sender notifications:" | tee -a "$LOGFILE"
echo "$SENDER_NOTIFICATIONS" | tee -a "$LOGFILE"

echo "Fetching notifications for receiver..." | tee -a "$LOGFILE"
RECEIVER_NOTIFICATIONS=$(curl -s -X GET http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $RECEIVER_TOKEN" | tee -a "$LOGFILE")
echo "Receiver notifications:" | tee -a "$LOGFILE"
echo "$RECEIVER_NOTIFICATIONS" | tee -a "$LOGFILE"

# Mark first receiver notification as read (if exists)
FIRST_RECEIVER_NOTIFICATION_ID=$(echo "$RECEIVER_NOTIFICATIONS" | grep -o '"id":[0-9]*' | head -n1 | cut -d ':' -f2)
if [ -n "$FIRST_RECEIVER_NOTIFICATION_ID" ]; then
  echo "Marking receiver notification $FIRST_RECEIVER_NOTIFICATION_ID as read..." | tee -a "$LOGFILE"
  MARK_READ_RESPONSE=$(curl -s -X POST http://localhost:8080/api/notifications/read/$FIRST_RECEIVER_NOTIFICATION_ID \
    -H "Authorization: Bearer $RECEIVER_TOKEN" | tee -a "$LOGFILE")
  echo "Mark as read response: $MARK_READ_RESPONSE" | tee -a "$LOGFILE"
else
  echo "No receiver notifications to mark as read." | tee -a "$LOGFILE"
fi

echo "Verifying notification marked as read..." | tee -a "$LOGFILE"
UPDATED_RECEIVER_NOTIFICATIONS=$(curl -s -X GET http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $RECEIVER_TOKEN" | tee -a "$LOGFILE")
if echo "$UPDATED_RECEIVER_NOTIFICATIONS" | jq -e ".[] | select(.id==$FIRST_RECEIVER_NOTIFICATION_ID and .read==true)" >/dev/null; then
  echo "Notification $FIRST_RECEIVER_NOTIFICATION_ID marked as read: SUCCESS" | tee -a "$LOGFILE"
else
  echo "Notification $FIRST_RECEIVER_NOTIFICATION_ID not marked as read: FAILURE" | tee -a "$LOGFILE"
fi

# Verify all files deleted from inbox
echo "Verifying all files deleted from inbox..." | tee -a "$LOGFILE"
INBOX_RESPONSE_AFTER_DELETE=$(curl -s -X GET "http://localhost:8080/api/transfer/inbox?receiver=receiver" \
  -H "Authorization: Bearer $RECEIVER_TOKEN" | tee -a "$LOGFILE")
# Check if the response is an empty array or contains no file objects
if echo "$INBOX_RESPONSE_AFTER_DELETE" | grep -q '"id":'; then
  echo "Some files still present after deletion: FAILURE" | tee -a "$LOGFILE"
elif echo "$INBOX_RESPONSE_AFTER_DELETE" | grep -q '\[\s*\]'; then
  echo "All files successfully deleted from inbox: SUCCESS" | tee -a "$LOGFILE"
else
  echo "Inbox response after delete is empty or contains no files: SUCCESS" | tee -a "$LOGFILE"
fi
echo -e "\n" | tee -a "$LOGFILE"
