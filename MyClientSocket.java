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

        }
    }

public static void main(String[] args) throws Exception {
        MyClientSocket client = new MyClientSocket(InetAddress.getByName(args[0]), Integer.parseInt(args[1]));
        
        System.out.println("\r\nConnected to Server: " + client.socket.getInetAddress());
        client.start();                
    }
}
