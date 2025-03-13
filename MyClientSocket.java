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
            // If we read less than chunkSize, it's the last chunk
            boolean isLast = (readCount < chunkSize);

            String content = new String(buffer, 0, readCount);
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

                        while (in.ready()) {
                            String remainingMessage = in.readLine();
                        }

                    } catch (IOException e) {
                        System.err.println("Error writing file: " + e.getMessage());
                    }
                }
            }

        }
    }

    public static void main(String[] args) throws Exception {
        MyClientSocket client = new MyClientSocket(InetAddress.getByName(args[0]), Integer.parseInt(args[1]));

        System.out.println("\r\nConnected to Server: " + client.socket.getInetAddress());
        client.start();
    }
}
