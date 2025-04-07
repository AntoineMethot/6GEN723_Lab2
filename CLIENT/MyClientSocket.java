package CLIENT;
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class MyClientSocket {
    private Socket socket = null;
    private Scanner scanner = null;

    private MyClientSocket(InetAddress serverAddress, int serverPort) throws Exception {
        this.socket = new Socket(serverAddress, serverPort);
        this.scanner = new Scanner(System.in);
    }

    private void sendFile(String filePath, String fileName) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }
    
        BufferedReader fileReader = new BufferedReader(new FileReader(file));
        PrintWriter out = new PrintWriter(this.socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    
        int offset = 0;
        int chunkSize = 500;
        char[] buffer = new char[chunkSize];
        int readCount;
    
        while ((readCount = fileReader.read(buffer)) != -1) {
            boolean isLast = (readCount < chunkSize);
    
            String content = new String(buffer, 0, readCount);
    
            // Pad the content if it's the last chunk
            if (isLast && readCount < chunkSize) {
                int padLength = chunkSize - readCount;
                content += "~".repeat(padLength); // or use another character like "~"
            }
    
            String fileCommand = String.format("FILE|%s|%d|%d|%s", fileName, offset, isLast ? 1 : 0, content);
            out.println(fileCommand);
            out.flush();
            offset += readCount;
    
            try {
                String ack = in.readLine();
                System.out.println("Server response: " + ack);
            } catch (IOException e) {
                System.err.println("Error waiting for server acknowledgment.");
            }
    
            if (isLast) {
                String doneMsg = in.readLine();
                System.out.println("Server response: " + doneMsg);
                break;
            }
        }
    
        fileReader.close();
    }
    

    private void start() throws IOException {
        String input;
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        while (true) {
            input = scanner.nextLine();
            PrintWriter out = new PrintWriter(this.socket.getOutputStream(), true);
            
            // Handle graceful exit
            if (input.equalsIgnoreCase("QUIT")) {
                out.println("QUIT");
                out.flush();
                System.out.println("Disconnecting from server...");
                socket.close(); // close connection cleanly
                break; // exit loop
            }
            
            out.println(input);
            out.flush();
            

            String data = in.readLine();
            System.out.println(data);

            // Handle server's WRITE BEGIN response
            if (data != null && data.startsWith("WRITE|BEGIN|")) {
                String[] parts = data.split("\\|");
                if (parts.length >= 3) {
                    String fileName = parts[2];
                    System.out.println("Beginning file transfer: " + fileName);
                    String uploadFolder = "FileToUpload";
                    String filePath = uploadFolder + File.separator + fileName;

                    File fileToUpload = new File(filePath);
                    if (!fileToUpload.exists()) {
                        System.out.println("File not found in FileToUpload: " + fileName);
                    } else {
                        sendFile(filePath, fileName);
                    }

                }
            }

            if (data != null && data.startsWith("READ|BEGIN")) {
                String[] parts = data.split("\\|");
                if (parts.length >= 3) {
                    String fileName = parts[2];
                    System.out.println("Beginning File Download: " + fileName);

                    String downloadFolder = "downloads";
                    File dir = new File(downloadFolder);
                    if (!dir.exists())
                        dir.mkdir();

                    String filePath = downloadFolder + File.separator + fileName;

                    try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(filePath))) {

                        boolean isLast = false;

                        while (true) {
                            String line = in.readLine(); // from server
                            if (line == null)
                                break;

                            if (line.startsWith("FILE|")) {
                                System.out.println("Received: " + line); // Print the FILE command

                                String[] fileParts = line.split("\\|", 5);
                                if (fileParts.length >= 5) {
                                    String receivedFile = fileParts[1];
                                    int offset = Integer.parseInt(fileParts[2]);
                                    isLast = fileParts[3].equals("1");
                                    String content = fileParts[4];
                                    if (isLast) {
                                        content = content.replaceAll("~+$", "");
                                    }
                                    fileWriter.write(content);
                                    System.out.println("Received chunk at offset " + offset);

                                    // Optional: Send ACK
                                    out.println("ACK|" + offset);
                                    out.flush();

                                    if (isLast) {
                                        System.out.println("Last chunk received. Waiting for DONE confirmation...");
                                        break; // exit loop to wait for final message
                                    }
                                } else {
                                    System.out.println("Invalid FILE message received: " + line);
                                }
                            } else {
                                System.out.println("Unexpected message during file download: " + line);
                            }
                        }

                        // Read final confirmation (READ|DONE|filename)
                        String doneLine = in.readLine();
                        if (doneLine != null && doneLine.startsWith("READ|DONE")) {
                            System.out.println("Server confirmation: " + doneLine); // Print the DONE message
                            System.out.println("File transfer complete: " + fileName);
                        } else {
                            System.out.println("Expected READ|DONE but got: " + doneLine);
                        }

                        System.out.println("File saved to: " + filePath);

                        //clear remaining buffer messages
                        while (in.ready()) {
                            String remainingMessage = in.readLine();
                        }

                    } catch (IOException e) {
                        System.err.println("Error writing file: " + e.getMessage());
                    }
                }
            }

            if (data != null && data.startsWith("READ|REDIRECT")){

                String[] parts = data.split("\\|");
                if (parts.length == 6) {
                    String fileOwnerIP = parts[2];
                    String fileOwnerPort = parts[3];
                    String token = parts[4];  // Add the token from the server
                    String requestedFile = parts[5];
    
                    System.out.println("Redirecting to server: " + fileOwnerIP + ":" + fileOwnerPort);
    
                    // Now connect to the new server and request the file with the token
                    connectToNewServer(fileOwnerIP, Integer.parseInt(fileOwnerPort), requestedFile, token);
                }
            }
        }
    }

    private void connectToNewServer(String serverIP, int serverPort, String requestedFile, String token) {
        try (Socket redirectSocket = new Socket(serverIP, serverPort)) {
            System.out.println("Connected to redirected server: " + serverIP + ":" + serverPort);
    
            PrintWriter redirectOut = new PrintWriter(redirectSocket.getOutputStream(), true);
            BufferedReader redirectIn = new BufferedReader(new InputStreamReader(redirectSocket.getInputStream()));
    
            // Send the READ command with the token to the new server
            redirectOut.println("READ|" + token + "|" + requestedFile);
            redirectOut.flush();
    
            // Receive the file from the redirected server (similar to the original code)
            receiveFileFromServer(redirectIn);
            System.out.println("Connection to peer server closed.");
        } catch (IOException e) {
            System.err.println("Error connecting to redirected server: " + e.getMessage());
        }

    }

    private void receiveFileFromServer(BufferedReader in) {
        try {
            String line;
            String filename = null;
            BufferedWriter fileWriter = null;
    
            while ((line = in.readLine()) != null) {
                if (line.startsWith("FILE|")) {
                    String[] parts = line.split("\\|", 5);
                    if (parts.length >= 5) {
                        filename = parts[1];
                        int offset = Integer.parseInt(parts[2]);
                        boolean isLast = parts[3].equals("1");
                        String content = parts[4];
    
                        // Ensure the "downloads" directory exists
                        File downloadDir = new File("downloads");
                        if (!downloadDir.exists()) {
                            downloadDir.mkdir();
                        }
    
                        // Open file in append mode
                        File file = new File(downloadDir, filename);
                        if (fileWriter == null) {
                            fileWriter = new BufferedWriter(new FileWriter(file, true)); // append mode
                        }
    
                        // Write content to file
                        fileWriter.write(content);
    
                        System.out.println("Received chunk at offset " + offset);
    
                        if (isLast) {
                            System.out.println("Last Packet received. File saved to downloads/" + filename);
                            break;
                        }
                    }
                }
    
                // Drain any remaining lines
                while (in.ready()) {
                    in.readLine();
                }
            }
    
            // Close writer if open
            if (fileWriter != null) {
                fileWriter.close();
            }
    
        } catch (IOException e) {
            System.err.println("Error receiving file from server: " + e.getMessage());
        }
    }
    

    public static void main(String[] args) throws Exception {
        MyClientSocket client = new MyClientSocket(InetAddress.getByName(args[0]), Integer.parseInt(args[1]));

        System.out.println("\r\nConnected to Server: " + client.socket.getInetAddress());
        client.start();
    }
}
