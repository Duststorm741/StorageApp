package example.com.storageapplication;

import android.os.Bundle;
import android.os.Environment;
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
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import java.io.File;

public class SecondActivity extends AppCompatActivity {

    private ArrayAdapter<String> fileListAdapter;
    private StorageReference storageRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        ListView fileListView = findViewById(R.id.fileListView);
        Button backButton = findViewById(R.id.backButton);

        // Initialize Firebase Storage
        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageRef = storage.getReferenceFromUrl("gs://serverproject-55561.appspot.com/Storage");

        // Initialize the ArrayAdapter for the ListView
        fileListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        fileListView.setAdapter(fileListAdapter);

        // Load the list of files from Firebase Storage
        loadFilesFromStorage();

        // Set a click listener for the list items to download files
        fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String fileName = fileListAdapter.getItem(position);
                if (fileName != null) {
                    downloadFile(fileName);
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

    private void downloadFile(String fileName) {
        // Creates a reference to the selected file
        StorageReference fileRef = storageRef.child(fileName);

        // Create a local file in the Downloads folder
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File localFile = new File(downloadsFolder, fileName);

        fileRef.getFile(localFile)
                .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        // File successfully downloaded to the Downloads folder
                        Toast.makeText(SecondActivity.this, "File downloaded to Downloads folder", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Handle errors here
                        Toast.makeText(SecondActivity.this, "Failed to download file", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
