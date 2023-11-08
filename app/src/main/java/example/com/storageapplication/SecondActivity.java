package example.com.storageapplication;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecondActivity extends AppCompatActivity {

    private ArrayAdapter<String> fileListAdapter;
    private StorageReference storageRef;

    FirebaseAuth auth;
    FirebaseUser user;

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_ALIAS = "MyAesAlias";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();

        ListView fileListView = findViewById(R.id.fileListView);
        Button backButton = findViewById(R.id.backButton);

        // Initialize Firebase Storage
        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageRef = storage.getReferenceFromUrl("gs://serverproject-55561.appspot.com/" + user.getEmail() + "/Storage");

        // Initialize the ArrayAdapter for the ListView
        fileListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        fileListView.setAdapter(fileListAdapter);

        // Load the list of files from Firebase Storage
        loadFilesFromStorage();

        // Set a click listener for the list items to download and decrypt files
        fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String fileName = fileListAdapter.getItem(position);
                if (fileName != null) {
                    downloadAndDecryptFile(fileName);
                }
            }
        });

        // Set a click listener for the back button
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Return to the Main Activity
                finish();
            }
        });
    }

    // Loads and lists files from Firebase
    private void loadFilesFromStorage() {
        // List all files in your Firebase Storage folder
        storageRef.listAll()
                .addOnSuccessListener(new OnSuccessListener<ListResult>() {
                    @Override
                    public void onSuccess(ListResult listResult) {
                        for (StorageReference item : listResult.getItems()) {
                            fileListAdapter.add(item.getName());
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Handle errors here
                        Toast.makeText(SecondActivity.this, "Failed to load files from Firebase Storage", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private SecretKey obtainAESKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            // Check if the key with the specified alias exists in the Keystore
            if (keyStore.containsAlias(AES_ALIAS)) {
                Key key = keyStore.getKey(AES_ALIAS, null);
                if (key instanceof SecretKey) {
                    // Key found and is a SecretKey, return it
                    return (SecretKey) key;
                } else {
                    Toast.makeText(SecondActivity.this, "Key alias is incorrect", Toast.LENGTH_SHORT).show();
                    return null;
                }
            } else {
                // Handle the case where the key doesn't exist
                Toast.makeText(SecondActivity.this, "Key does not exist", Toast.LENGTH_SHORT).show();
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exceptions appropriately
            return null;
        }
    }

    private void downloadAndDecryptFile(String fileName) {
        // Obtain the AES key from the keystore
        SecretKey key = obtainAESKey();

        if (key == null) {
            Toast.makeText(SecondActivity.this, "Failed to obtain AES key", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create references to the selected file
        StorageReference fileRef = storageRef.child(fileName);

        // Create local files for the encrypted and decrypted content
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File localEncryptedFile = new File(downloadsFolder, fileName);

        fileRef.getFile(localEncryptedFile)
                .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        try {
                            // Read the downloaded data
                            byte[] ivAndEncryptedData = new byte[(int) localEncryptedFile.length()];
                            FileInputStream inputStream = new FileInputStream(localEncryptedFile);
                            inputStream.read(ivAndEncryptedData);
                            inputStream.close();

                            // Initialize the cipher for decryption with the combined IV
                            byte[] iv = Arrays.copyOfRange(ivAndEncryptedData, 0, 12); // Assuming a 96-bit IV for GCM
                            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));

                            // Decrypt the data (excluding the IV)
                            byte[] encryptedData = Arrays.copyOfRange(ivAndEncryptedData, 12, ivAndEncryptedData.length);
                            byte[] decryptedData = cipher.doFinal(encryptedData);

                            // Get the original file extension from metadata
                            fileRef.getMetadata()
                                    .addOnSuccessListener(new OnSuccessListener<StorageMetadata>() {
                                        @Override
                                        public void onSuccess(StorageMetadata metadata) {
                                            String originalExtension = metadata.getCustomMetadata("originalExtension");
                                            if (originalExtension != null && !originalExtension.isEmpty()) {
                                                // Create a new file with the original file extension
                                                File localDecryptedFile = new File(downloadsFolder, "decrypted_" + fileName + "." + originalExtension);

                                                // Write the decrypted data to the new file
                                                FileOutputStream outputStream = null;
                                                try {
                                                    outputStream = new FileOutputStream(localDecryptedFile);
                                                } catch (FileNotFoundException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                try {
                                                    outputStream.write(decryptedData);
                                                } catch (IOException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                try {
                                                    outputStream.close();
                                                } catch (IOException e) {
                                                    throw new RuntimeException(e);
                                                }

                                                // Delete the encrypted file
                                                if (localEncryptedFile.exists()) {
                                                    localEncryptedFile.delete();
                                                }

                                                Toast.makeText(SecondActivity.this, "File downloaded and decrypted to Downloads folder", Toast.LENGTH_SHORT).show();
                                            } else {
                                                // Handle the case where the original extension is not available
                                                Toast.makeText(SecondActivity.this, "Original extension not found in metadata", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Toast.makeText(SecondActivity.this, "Failed to retrieve metadata", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } catch (Exception e) {
                            Toast.makeText(SecondActivity.this, "Failed to decrypt the file", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Log the exception to help diagnose the issue
                        Log.e("DownloadError", "Failed to download file: " + e.getMessage());

                        // Handle the error, or simply show a toast message
                        Toast.makeText(SecondActivity.this, "Failed to download file", Toast.LENGTH_SHORT).show();
                    }
                });
    }


}
