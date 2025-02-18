import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.Random;

public class MyServerSocket {

    public ServerSocket server = null;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public MyServerSocket(String ipAddress) throws Exception {
        if (ipAddress != null && !ipAddress.isEmpty())
            this.server = new ServerSocket(65000, 1, InetAddress.getByName(ipAddress));
        else
            this.server = new ServerSocket(65000, 1, InetAddress.getLocalHost());
    }

    private void listen() throws Exception {
        int clientAddress = 1;
        while (true) {
            Socket client = this.server.accept();
            System.out.println("\r\nNew connection from Client " + clientAddress);
            new ClientHandler(client, clientAddress, generateToken(20)).start();
            clientAddress += 1;
        }
    }

    // ################## TOKEN GENERATOR #######################################################
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

    // ################## CLIENT HANDER #######################################################
    private class ClientHandler extends Thread {
        private Socket client;
        private BufferedReader in;
        private BufferedWriter out;
        private int clientNumber;
        private String token;

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

                    switch (data){
                        case "REGISTER":
                            out.write("REGISTERED: " + token);
                            out.newLine();
                            out.flush();
                            break;
                        default:
                            out.write("INVALID COMMAND");
                            out.newLine();
                            out.flush();    
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ########################### MAIN ###################################################################################
    public InetAddress getSocketAddress() {
        return this.server.getInetAddress();
    }

    public int getPort() {
        return this.server.getLocalPort();
    }

    public static void main(String[] args) throws Exception {
        MyServerSocket app = new MyServerSocket(args[0]);
        System.out.println(
                "Running Server: " + "Host=" + app.getSocketAddress().getHostAddress() + " Port=" + app.getPort());
        try {
            File Peers = new File("Peers_list.txt");
            Scanner myReader = new Scanner(Peers);
            if (Peers.exists()) {
                System.out.println("Peers List: ");
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    String[] parts = data.split(":");
                    String ipAddress = parts[0].trim(); // IP address
                    String port = parts[1].trim(); // Port

                    InetAddress address = InetAddress.getByName(ipAddress);
                    boolean reachable = address.isReachable(Integer.parseInt(port));

                    System.out.println(reachable ? data + " is reachable" : data + "is not reachable");
                }
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not Found");
            e.printStackTrace();
        }

        app.listen();
    }
}
