package example.com.storageapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
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

    private static final long INACTIVITY_TIMEOUT =60000; // 15 seconds in milliseconds
    private Handler inactivityHandler;
    private Runnable inactivityRunnable;

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

        // Reset inactivity timer whenever user interacts with the activity
        findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                resetInactivityTimer();
                return false;
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

        // Get a reference to the location where you want to store your file in Firebase Storage
        StorageReference fileRef = storageReference.child(R.id.user_details + "/Storage/" + System.currentTimeMillis());

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