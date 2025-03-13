import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.Random;

public class MyServerSocket {

    public ServerSocket server = null;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public MyServerSocket() throws Exception {
        this.server = new ServerSocket(65000, 1, InetAddress.getLocalHost());
    }

    private void listen() throws Exception {
        int clientAddress = 1;
        while (true) {
            Socket client = this.server.accept();
            System.out.println("\r\nNew connection from Client " + clientAddress);

            String token = generateToken(20);
            String clientIP = client.getInetAddress().getHostAddress();
            saveTokenToFile(token, clientIP);

            new ClientHandler(client, clientAddress, token).start();
            clientAddress += 1;
        }
    }

    private synchronized void saveTokenToFile(String token, String ClientIP) {
        long expirationTime = System.currentTimeMillis() + (60 * 60 * 1000); // 5 minutes from now

        try (FileWriter fw = new FileWriter("ClientTokens.txt", true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw)) {

            out.println(token + "|" + ClientIP + "|" + expirationTime);

        } catch (IOException e) {
            System.err.println("Error writing token to file: " + e.getMessage());
        }
    }

    public void connectToAnotherServer(String serverBIP, int serverBPort) {
        try {
            // Create a socket to connect to Server B
            Socket serverBSocket = new Socket(serverBIP, serverBPort);

            // Set up input/output streams for communication
            BufferedReader serverBIn = new BufferedReader(new InputStreamReader(serverBSocket.getInputStream()));
            PrintWriter serverBOut = new PrintWriter(serverBSocket.getOutputStream(), true);

            // Send a message to Server B
            serverBOut.println("Hello from Server A!");

            // Receive a response from Server B
            String response = serverBIn.readLine();
            System.out.println("Response from Server B: " + response);

            // Close the connection to Server B
            serverBSocket.close();
        } catch (IOException e) {
            System.out.println("Error connecting to Server B: " + e.getMessage());
        }
    }

    // ###################################################################################################
    // TOKEN GENERATOR

    public static String generateToken(int length) {
        Random random = new Random(); // Simple Random
        StringBuilder token = new StringBuilder(length);

        // Generate the token
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(CHARACTERS.length());
            token.append(CHARACTERS.charAt(index));
        }

        return token.toString();
    }

    // ##################################################################################################
    // CLIENT HANDER

    private class ClientHandler extends Thread {
        private Socket client;
        private BufferedReader in;
        private BufferedWriter out;
        private int clientNumber;
        private String token;
        private String authorizedFileToWrite = null;

        public ClientHandler(Socket client, int clientNumber, String token) throws IOException {
            this.client = client;
            this.clientNumber = clientNumber;
            this.token = token;
            this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
        }

        @Override
        public void run() {
            try {
                String data;
                while ((data = in.readLine()) != null) {

                    System.out.println("Message from client " + clientNumber + ": " + data);
                    String[] parts = data.split("\\|");
                    String COMMAND = parts.length > 0 ? parts[0] : "";
                    String second = parts.length > 1 ? parts[1] : "";
                    String third = parts.length > 2 ? parts[2] : "";
                    String fourth = parts.length > 3 ? parts[3] : "";
                    String fifth = parts.length > 4 ? parts[4] : "";

                    // ################################################################################### REGISTER COMMAND
                    // REGISTER|IP_CLIENT|
                    if (COMMAND.contains("REGISTER")) {
                        out.write("REGISTERED:" + token);
                        out.newLine();
                        out.flush();
                    }
                    // ######################################################################################## LS COMMAND
                    // LS|JETONCLIENT
                    else if (COMMAND.contains("LS")) {
                        if (second.equals(token)) {
                            File FILES = new File("Files_list.txt");
                            Scanner myReader = new Scanner(FILES);
                            if (FILES.exists()) {
                                String FILEOUTPUT = "LS|K|";
                                while (myReader.hasNextLine()) {
                                    String filedata = myReader.nextLine();
                                    FILEOUTPUT += filedata + "|";
                                }
                                out.write(FILEOUTPUT);
                                out.newLine();
                                out.flush();
                            }
                            myReader.close();
                            out.flush();
                        } else if (!(second.equals(token))) {
                            out.write("LS|UNAUTHORIZED");
                            out.newLine();
                            out.flush();
                        }
                    }
                    // ###################################################################################
                    // WRITE COMMAND
                    // WRITE|JETONCLIENT|NOM_FICHIER
                    else if (COMMAND.contains("WRITE")) {
                        if (second.equals(token)) {
                            out.write("WRITE|BEGIN|" + third);
                            authorizedFileToWrite = third;
                            out.newLine();
                            out.flush();
                        } else if (!(second.equals(token))) {
                            out.write("WRITE|UNAUTHORIZED");
                            out.newLine();
                            out.flush();
                        }
                    }
                    // #################################################################################
                    // FILE COMMAND
                    // FILE|nom_fichier|offset|iLAST|500
                    else if (COMMAND.contains("FILE")) {
                        if (authorizedFileToWrite != null && second.equals(authorizedFileToWrite)) {
                            if (parts.length < 5) {
                                out.write("FILE|ERROR|Invalid format");
                                out.newLine();
                                out.flush();
                                continue;
                            }

                            // Save file information
                            String filename = second;
                            int offset = Integer.parseInt(third);
                            boolean isLast = fourth.equals("1");
                            String content = fifth;

                            File dir = new File("uploads");
                            if (!dir.exists())
                                dir.mkdir();
                            // Write content to file
                            File file = new File(dir, filename);
                            FileWriter fw = new FileWriter(file, true);
                            fw.write(content);
                            fw.close();
                            // Write out length of message
                            System.out.println("Received fragment " + offset + " for file " + filename);
                            out.write("FILE|RECEIVED|" + (content.length()));
                            out.newLine();
                            out.flush();

                            if (isLast) {
                                System.out.println("File transfer complete: " + filename);
                                out.write("FILE|DONE|" + filename);
                                out.newLine();
                                out.flush();

                                // Append to Files_list.txt
                                try (BufferedWriter fileWriter = new BufferedWriter(
                                        new FileWriter("Files_list.txt", true))) {
                                    String serverIP = client.getLocalAddress().getHostAddress();
                                    int serverPort = client.getLocalPort();
                                    fileWriter.write(filename + ":" + serverIP + ":" + serverPort);
                                    fileWriter.newLine();

                                    // Append to Peers_list.txt if not already present
                                    String peerEntry = serverIP + ":" + serverPort;
                                    File peersFile = new File("Peers_list.txt");

                                    boolean peerExists = false;
                                    if (peersFile.exists()) {
                                        try (BufferedReader reader = new BufferedReader(new FileReader(peersFile))) {
                                            String line;
                                            while ((line = reader.readLine()) != null) {
                                                if (line.trim().equals(peerEntry)) {
                                                    peerExists = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                    if (!peerExists) {
                                        try (BufferedWriter peersWriter = new BufferedWriter(
                                                new FileWriter(peersFile, true))) {
                                            peersWriter.write(peerEntry);
                                            peersWriter.newLine();
                                        }
                                    }

                                } catch (IOException e) {
                                    System.err.println("Error writing to Files_list.txt: " + e.getMessage());
                                }
                                // Clear authorization after file is fully received
                                authorizedFileToWrite = null;
                            }

                        } else if (authorizedFileToWrite == null) {
                            out.write("REQUEST WRITE AUTHORIZATION");
                            out.newLine();
                            out.flush();
                        } else if (!second.equals(authorizedFileToWrite)) {
                            System.out.println(second);
                            System.out.println(authorizedFileToWrite);
                            out.write("NOT AUTHORIZED FOR THIS FILE");
                            out.newLine();
                            out.flush();
                        }
                        // ########################################################################### READ COMMAND
                        // READ|jeton_client|nom_fichier|
                    } else if (COMMAND.contains("READ")) {
                        if (second.equals(token)) {

                            String requestedFile = third;
                            File filesList = new File("Files_list.txt");
                            boolean fileFound = false;
                            boolean localFile = false;
                            String fileOwnerIP = "";
                            String fileOwnerPort = "";

                            // CHECK IF FILE EXISTS AND IF ITS LOCAL
                            try (BufferedReader reader = new BufferedReader(new FileReader(filesList))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    String[] fileparts = line.split(":"); // Split the line into [filename, ip, port]
                                    if (fileparts.length == 3 && fileparts[0].equals(requestedFile)) {
                                        fileFound = true;
                                        fileOwnerIP = fileparts[1];
                                        fileOwnerPort = fileparts[2];

                                        // Compare with local server IP and port
                                        String localIP = client.getLocalAddress().getHostAddress();
                                        int localPort = client.getLocalPort();

                                        if (fileOwnerIP.equals(localIP)
                                                && fileOwnerPort.equals(String.valueOf(localPort))) {
                                            localFile = true;
                                            System.out.println("############LOCAL FILE REQUESTED##########");
                                        }
                                        break; // Exit once file is found
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Error reading Files_list.txt: " + e.getMessage());
                            }

                            // Start sending file
                            if (!fileFound) {
                                out.write("READ|FILE NOT FOUND|" + requestedFile);
                                out.newLine();
                                out.flush();
                            } else if (localFile) {
                                // The file is found locally, send it to the client
                                File file = new File("uploads/" + requestedFile);
                                if (!file.exists()) {
                                    out.write("READ|ERROR|File missing from disk");
                                    out.newLine();
                                    out.flush();
                                } else {
                                    out.write("READ|BEGIN|" + requestedFile);
                                    out.newLine();
                                    out.flush();
                                    // Send the file content in chunks
                                    try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                                        int offset = 0;
                                        int chunkSize = 500;
                                        char[] buffer = new char[chunkSize];
                                        int readCount;

                                        while ((readCount = fileReader.read(buffer)) != -1) {
                                            boolean isLast = readCount < chunkSize;
                                            String content = new String(buffer, 0, readCount);
                                            String fileCommand = String.format("FILE|%s|%d|%d|%s", requestedFile,
                                                    offset, isLast ? 1 : 0, content);
                                            out.write(fileCommand);
                                            out.newLine();
                                            out.flush();

                                            offset += readCount;

                                            // Optional: wait for client ACK (if needed)
                                            // String ack = in.readLine();
                                        }

                                        // Optional: send a DONE message when file is fully sent
                                        out.write("READ|DONE|" + requestedFile);
                                        out.newLine();
                                        out.flush();

                                    } catch (IOException e) {
                                        System.err.println("Error sending file: " + e.getMessage());
                                    }
                                }
                            } else {
                                // The file is not found locally, redirect to the server that owns the file
                                try {
                                    // Connect to the file owner server to get a token
                                    Socket redirectSocket = new Socket(fileOwnerIP, Integer.parseInt(fileOwnerPort));
                                    BufferedWriter redirectOut = new BufferedWriter(new OutputStreamWriter(redirectSocket.getOutputStream()));
                                    BufferedReader redirectIn = new BufferedReader(new InputStreamReader(redirectSocket.getInputStream()));

                                    // Request a token from the file owner server
                                    redirectOut.write("REGISTER");
                                    redirectOut.newLine();
                                    redirectOut.flush();

                                    // Read the token from the file owner server
                                    String response = redirectIn.readLine();
                                    String tokenFromOwner = response.split(":")[1];

                                    // Send the redirect message along with the token to the client
                                    out.write("READ|REDIRECT|" + fileOwnerIP + "|" + fileOwnerPort + "|" + tokenFromOwner + "|" + requestedFile);
                                    out.newLine();
                                    out.flush();

                                    // Close the connection to the file owner server
                                    redirectSocket.close();
                                } catch (IOException e) {
                                    System.err.println("Error connecting to file owner server: " + e.getMessage());
                                    out.write("READ|ERROR|Unable to connect to the file owner server");
                                    out.newLine();
                                    out.flush();
                                }
                            }
                        } else if (!(second.equals(token))) {
                            out.write("READ|UNAUTHORIZED");
                            out.newLine();
                            out.flush();
                        }
                        // ################################################## WRONG COMMAND
                    } else {
                        out.write("INVALID COMMAND");
                        out.newLine();
                        out.flush();
                        continue;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // #################################################################### MAIN
    public InetAddress getSocketAddress() {
        return this.server.getInetAddress();
    }

    public int getPort() {
        return this.server.getLocalPort();
    }

    public static void main(String[] args) throws Exception {
        MyServerSocket app = new MyServerSocket();
        System.out.println(
                "Running Server: " + "Host=" + app.getSocketAddress().getHostAddress() + " Port=" + app.getPort());

        // try {
        // File Peers = new File("Peers_list.txt");
        // Scanner myReader = new Scanner(Peers);
        // if (Peers.exists()) {
        // System.out.println("Peers List: ");
        // while (myReader.hasNextLine()) {
        // String data = myReader.nextLine();
        // String[] parts = data.split(":");
        // String ipAddress = parts[0].trim(); // IP address
        // String port = parts[1].trim(); // Port

        // InetAddress address = InetAddress.getByName(ipAddress);
        // boolean reachable = address.isReachable(Integer.parseInt(port));

        // System.out.println(reachable ? data + " is reachable" : data + " is not
        // reachable");
        // }
        // }
        // myReader.close();
        // } catch (FileNotFoundException e) {
        // System.out.println("File not Found");
        // e.printStackTrace();
        // }

        // System.out.println("Connect to another server? (Y/N):");
        // Scanner input = new Scanner(System.in);
        // String connectToServer = input.nextLine();
        // if(connectToServer.equals("Y")){
        // System.out.println("Enter IP and PORT (EX. 192.168.1.1:65000)");
        // String serverAddress = input.nextLine();
        // input.close();

        // String[] serverAddressParts = serverAddress.split(":");
        // String ServerIp = serverAddressParts[0];
        // int ServerPort = Integer.parseInt(serverAddressParts[1]);

        // app.connectToAnotherServer(ServerIp, ServerPort);
        // }
        // else if (connectToServer.equals("N")) {

        // }

        app.listen();
    }
}
