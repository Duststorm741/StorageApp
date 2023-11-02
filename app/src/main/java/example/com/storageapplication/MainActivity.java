package example.com.storageapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private TextView selectedFileTextView;
    private Uri selectedFileUri;

    private StorageReference storageReference;

    FirebaseAuth auth;
    Button logout_button;
    TextView userTextView;
    FirebaseUser user;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        logout_button = findViewById(R.id.logout);
        userTextView = findViewById(R.id.user_details);
        user = auth.getCurrentUser();

        if (user == null){
            Intent intent = new Intent(getApplicationContext(), Login.class);
            startActivity(intent);
            finish();
        }
        else {
            userTextView.setText(user.getEmail());
        }

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

        storageReference = FirebaseStorage.getInstance().getReference(); // Initialize Firebase Storage

        // Button click listener to open the file picker dialog.
        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Open a file picker dialog to select a file.
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, 1);
            }
        });
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

        // Get a reference to the location where you want to store your file in Firebase Storage
        StorageReference fileRef = storageReference.child("Storage/" + System.currentTimeMillis());

        try {
            // Open an InputStream from the selected file and upload it to Firebase Storage.
            InputStream stream = getContentResolver().openInputStream(selectedFileUri);
            assert stream != null;
            UploadTask uploadTask = fileRef.putStream(stream);

            uploadTask.addOnSuccessListener(taskSnapshot -> {
                Toast.makeText(getApplicationContext(), "File uploaded successfully", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(exception -> {
                Toast.makeText(getApplicationContext(), "Upload failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "File selection failed", Toast.LENGTH_SHORT).show();
        }
    }

    public void goToSecondActivity(View view) {
        Intent intent = new Intent(this, SecondActivity.class);
        startActivity(intent);
    }

}