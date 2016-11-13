import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class Caixote_Server {
	
	/* List of occupied directories */
	public static List<String> occupied_directories_list = new ArrayList<String>();
	
	/******************************* Main  ******************************/
	
	public static void main(String argv[]){
		
		/* List of currently running threads */
		List<Caixote_Server_Thread> thread_list = new ArrayList<Caixote_Server_Thread>();
		
		/* Welcome user */
		System.out.println(Caixote_Server.class.getSimpleName() + " starting...");
		
		if (argv.length < 1){
			System.out.println("Error: Too few arguments!");
			System.out.printf("Usage: java %s <port>\n", Caixote_Server.class.getName());
			return;
		}
		
		/* Start by getting launch values*/
		
		int port = Integer.parseInt(argv[0]);
	
		System.out.printf("Creating Server Welcome Socket in port %d\n", port);
		
		/* Create Server Welcome Socket to wait for clients' requests */
		
		ServerSocket welcomeSocket = null;
		
		try {
			welcomeSocket = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println("I/O error ocurred when creating the socket");
			e.printStackTrace();
			return;
		
		} catch (IllegalArgumentException e){
			System.out.println("Port parameter is outside the specified range of valid port values,"
					+ " which is between 0 and 65535, inclusive. The application's recomended port number is 40");
			return;
		}
		
		System.out.println("Server Welcome Socket successfully created!");
		
		/* Setting Server wait timeout for connections to be serverWaitTimeout seconds */
		
		int serverWaitTimeout = 300;
		
		System.out.printf("Setting Server Welcome Socket timeout for connections to %d seconds\n", serverWaitTimeout);
		
		try {
			welcomeSocket.setSoTimeout(serverWaitTimeout * 1000);
		} catch (SocketException e) {
			System.out.printf("TCP Error: Can't set the timeout to %d seconds!\n", serverWaitTimeout);
			e.printStackTrace();
		}
				
		System.out.println("Server Welcome Socket is now live!");

		/* Server loop to establish connections to be carried out by this server's threads.
		 * Server will wait connections till the timeout is exceeded, then the server will shut down */
		
		while (true) {
			Socket connectionSocket;
			
			System.out.println("Waiting for clients to connect");
			
			try {
				connectionSocket =  welcomeSocket.accept();
			} catch (SocketTimeoutException e){
				System.out.println("Server wait timeout for connections exceeded. No longer accepting more clients");
				break;				
			} catch (IOException e) {
				System.out.println("I/O error occured when waiting for a connection");
				e.printStackTrace();
				break;
			}
			
			System.out.println("New client connected! Starting separate thread for this client");
			
			/* Make a new Thread to carry out the client's requests */
			
			Caixote_Server_Thread thread = new Caixote_Server_Thread(connectionSocket);
			
			/* Add it to this server's list of threads */
			
			thread_list.add(thread);
			
			/* Start the new Thread */
			
			thread.start();			
		}
		
		System.out.println("Waiting for threads to end connections with their clients");

		/* Wait for every thread to shut down */
		
		long threadID = 0;
		
		for (Caixote_Server_Thread thread : thread_list){
			try {
				threadID = thread.getId();
				System.out.printf("Waiting for thread #%s...\n", String.valueOf(threadID));
				thread.join();
				System.out.printf("Thread #%s ended successfully!\n", String.valueOf(threadID));
			} catch (InterruptedException e) {
				System.out.printf("Thread #%s was interrupted!\n", String.valueOf(threadID));
				e.printStackTrace();
			}
		}
		
		System.out.println("Closing server welcome socket");
		
		try {
			welcomeSocket.close();
		} catch (IOException e) {
			System.out.println("I/O error occured when closing server welcome socket");
			e.printStackTrace();
		}
		
		System.out.println("Server welcome socket successfully closed");
		System.out.println("Ending " + Caixote_Server.class.getSimpleName());
	}	
}
