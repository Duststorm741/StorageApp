import java.io.*;
import java.net.*;
import java.nio.file.*;



class Server {
  private ServerSocket server = null;

  public Server(int port, int backlog, String IP) {
    try {
      server = new ServerSocket(port, backlog, InetAddress.getByName(IP));
 

      while (true) {
        Socket socket = server.accept();
        new ClientHandler(socket).start();
      }
    } catch (Exception e) {
      System.out.println("Error: " + e.getMessage());
    }
  }

  public static void main(String args[]) {
    Server server = new Server(5000, 0, "127.0.0.1");
  }

  class ClientHandler extends Thread {
    private Socket socket;


    public ClientHandler(Socket socket) {
      this.socket = socket;
    }


    private String getFileNameFromHeader(String header) {
      int startIndex = header.indexOf("filename:");
      if (startIndex != -1) {
        int endIndex = header.indexOf("\r\n", startIndex);
        if (endIndex != -1) {
          return header.substring(startIndex + 9, endIndex);
        }
      }
      return "default_filename"; // Return a default filename if the header is invalid
    }


    @Override
    public void run() {
      try {
        File receivedFilesDirectory = new File("encrypted_files");

        receivedFilesDirectory.mkdir();

        byte[] buffer = new byte[1024];

        while (true) {
          int headerLength = socket.getInputStream().read(buffer, 0, buffer.length);
          if (headerLength <= 0) {
            break; // No more data to read
          }

          String header = new String(buffer, 0, headerLength);

          ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
          while (true) {
            int bytesRead = socket.getInputStream().read(buffer, 0, buffer.length);
            if (bytesRead <= 0) {
              break; // No more data to read
            }

            // Check for "END_OF_FILE" delimiter and skip it
            String dataChunk = new String(buffer, 0, bytesRead);
            if (dataChunk.contains("END_OF_FILE")) {
              bytesRead -= "END_OF_FILE\r\n".getBytes().length; // Subtract delimiter length
            }

            dataStream.write(buffer, 0, bytesRead);

            // Check again for "END_OF_FILE" delimiter
            if (dataChunk.contains("END_OF_FILE")) {
              break; // End of the current file
            }
          }

          byte[] receivedData = dataStream.toByteArray();

            String fileName = getFileNameFromHeader(header);

            File receivedFile = new File(receivedFilesDirectory, fileName);
            try (FileOutputStream fileOutputStream = new FileOutputStream(receivedFile)) {
              fileOutputStream.write(receivedData, 0, receivedData.length);
            } catch (Exception e) {
              System.out.println("Error saving received file: " + e.getMessage());
            }



        }
      } catch (Exception e) {
        System.out.println("Error receiving file: " + e.getMessage());
      } finally {
        try {
          socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
