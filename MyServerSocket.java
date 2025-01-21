import java.io.*;
import java.net.*;

public class MyServerSocket {

    public ServerSocket server = null;
    public MyServerSocket(String ipAddress) throws Exception {
        if (ipAddress != null && !ipAddress.isEmpty()) 
          this.server = new ServerSocket(0, 1, InetAddress.getByName(ipAddress));
        else 
          this.server = new ServerSocket(0, 1, InetAddress.getLocalHost());
    }

    private void listen() throws Exception {
        int clientAddress = 1;
        while (true) {
            Socket client = this.server.accept();
            System.out.println("\r\nNew connection from Client " + clientAddress);
            new ClientHandler(client, clientAddress).start();
            clientAddress += 1;
        }
    }
    
    private class ClientHandler extends Thread {
        private Socket client;
        private BufferedReader in;       
        private BufferedWriter out;
        private int clientNumber;

        public ClientHandler(Socket client, int clientNumber) throws IOException {
            this.client = client;
            this.clientNumber = clientNumber;
            this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream())); 
    }

    @Override
    public void run() {
        try {
            String data;
            while ((data = in.readLine()) != null) {
                System.out.println("Message from client " + clientNumber + ": " + data);
                String response = "Hello " + data;
                out.write(response);
                out.newLine();
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

public InetAddress getSocketAddress() {
        return this.server.getInetAddress();
    }
    
    public int getPort() {
        return this.server.getLocalPort();
    }

    public static void main(String[] args) throws Exception {
        MyServerSocket app = new MyServerSocket(args[0]);
        System.out.println("Running Server: " + "Host=" + app.getSocketAddress().getHostAddress() + " Port=" + app.getPort());
        app.listen();
    }
}
