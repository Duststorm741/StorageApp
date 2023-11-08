package example.com.storageapplication;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

public class MainActivity extends AppCompatActivity {

    private TextView selectedFileTextView;
    private Uri selectedFileUri;

    private StorageReference storageReference;

    private static final long INACTIVITY_TIMEOUT =60000; // 15 seconds in milliseconds
    private Handler inactivityHandler;
    private Runnable inactivityRunnable;

    private FirebaseAuth auth;
    private Button logout_button;
    private TextView userTextView;
    private FirebaseUser user;
    private KeyStore keyStore;

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_ALIAS = "MyAesAlias";





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        logout_button = findViewById(R.id.logout);
        userTextView = findViewById(R.id.user_details);
        user = auth.getCurrentUser();

        // Initialize inactivity detection
        inactivityHandler = new Handler();
        inactivityRunnable = new Runnable() {
            @Override
            public void run() {
                // Redirect to Login activity after inactivity timeout
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(getApplicationContext(), Login.class);
                startActivity(intent);
                finish();

                // Display a toast message when the timer runs out and logs the user out
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Session timed out. You have been logged out.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };


        if (user == null) {
            Intent intent = new Intent(getApplicationContext(), Login.class);
            startActivity(intent);
            finish();
        } else {
            userTextView.setText(user.getEmail());
        }

        // Logout function
        logout_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(getApplicationContext(), Login.class);
                startActivity(intent);
                finish();
            }
        });

        Button selectFileButton = findViewById(R.id.selectFileButton);
        selectedFileTextView = findViewById(R.id.selectedFileTextView);

        storageReference = FirebaseStorage.getInstance().getReference();

        // Attempts to initialize Android Keystore
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Keystore initialization failed", Toast.LENGTH_SHORT).show();
        }

        // Calls Android file selector to choose file
        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, 1);
            }
        });
    }


    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // Reset inactivity timer whenever user interacts with the activity
        resetInactivityTimer();
    }

    private void resetInactivityTimer() {
        // Remove existing callbacks to avoid stacking
        inactivityHandler.removeCallbacksAndMessages(null);
        // Set a new callback to redirect to Login activity after the specified inactivity timeout
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Get the selected file's URI and display it.
            assert data != null;
            selectedFileUri = data.getData();
            selectedFileTextView.setText("Selected File: " + selectedFileUri.getPath());
        }
    }

    // Button click handler for sending the selected file to Firebase Cloud Storage
    public void onClick(View view) {
        if (selectedFileUri == null) {
            // No file selected, exit without sending.
            return;
        }

        try {
            // Obtains key for AES and calls the upload and encrypt function
            SecretKey aesKey = obtainAESKey();
            uploadEncryptedFile(selectedFileUri, aesKey);
        } catch (Exception e) {
            // Upload failed
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "File upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private SecretKey obtainAESKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(AES_ALIAS)) {
            // If key exists return it
            return (SecretKey) keyStore.getKey(AES_ALIAS, null);
        } else {
            // IF no key exists generate one and return it
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            keyGenerator.init(new KeyGenParameterSpec.Builder(
                    AES_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(false)
                    .build());

            return keyGenerator.generateKey();
        }
    }

    private void uploadEncryptedFile(Uri selectedFileUri, SecretKey aesKey) {
        try {
            //Generates a initialization vector
            byte[] iv = new byte [12];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Use AES with key from keystore and the generated IV
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));

            // Initialize the input stream for the selected file
            InputStream inputStream = getContentResolver().openInputStream(selectedFileUri);

            // Create a temporary file for the encrypted data
            String originalFileName = getFileName(selectedFileUri);
            String tempFileName = "encrypted_" + originalFileName;
            File tempFile = new File(getFilesDir(), tempFileName);
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);

            // Initialize the buffer for reading and encrypting the file
            byte[] buffer = new byte[4096];
            int bytesRead;

            // Writes the IV to the file
            fileOutputStream.write(iv);

            // Encrypt the file data
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] encryptedBytes = cipher.update(buffer, 0, bytesRead);
                fileOutputStream.write(encryptedBytes);
            }

            // Finalize the encryption
            byte[] encryptedBytes = cipher.doFinal();
            fileOutputStream.write(encryptedBytes);

            fileOutputStream.close();
            inputStream.close();

            // Add metadata to specify the original file extension to ensure decryption
            StorageMetadata metadata = new StorageMetadata.Builder()
                    .setCustomMetadata("originalExtension", getFileExtension(selectedFileUri))
                    .build();

            // Upload the temporary file to Firebase Storage with metadata
            StorageReference fileRef = storageReference.child(user.getEmail() + "/Storage/" + tempFileName);
            Uri fileUri = Uri.fromFile(tempFile);
            UploadTask uploadTask = fileRef.putFile(fileUri, metadata);

            uploadTask.addOnSuccessListener(taskSnapshot -> {
                tempFile.delete();
                Toast.makeText(getApplicationContext(), "File uploaded successfully", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(exception -> {
                tempFile.delete();
                Toast.makeText(getApplicationContext(), "Upload failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "File upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Add a helper method to get the file extension from a Uri
    private String getFileExtension(Uri uri) {
        String extension = null;
        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            // Uses conflict resolver to retrieve MIME type
            ContentResolver contentResolver = getContentResolver();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            extension = mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            // Handle file URIs if needed
            String path = uri.getPath();
            if (path != null) {
                int lastDot = path.lastIndexOf(".");
                if (lastDot != -1) {
                    extension = path.substring(lastDot + 1);
                }
            }
        }
        return extension;
    }




    //Gets file name for new name when encrypted
    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                    // Remove the file extension
                    int lastDot = result.lastIndexOf(".");
                    if (lastDot != -1) {
                        result = result.substring(0, lastDot);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    //Goes to the file download UI
    public void goToSecondActivity(View view) {
        Intent intent = new Intent(this, SecondActivity.class);
        startActivity(intent);
    }
}
