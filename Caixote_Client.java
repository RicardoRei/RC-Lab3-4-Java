import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class Caixote_Client{
	
	/************************* Protocol responses **********************/
	
	/* Session establishment responses */
	
	private final static int REQUESTVALIDATED = 0;
	private final static int USERNOTOWNER = 1;
	private final static int FILEALREADYINUSE = 2;
	
	/* Synchronisation requests */
	private final static int REQUESTENDOFSYNC = 3;
	private final static int REQUESTTIMEOFFILE = 4;
	private final static int REQUESTSINGLEFILETRANSFER = 5;
	
	/******************** End of Protocol responses ********************/
	
	/******************************* Main ******************************/
	
	public static void main(String argv[]){
		
		/* Welcome user */
		System.out.println(Caixote_Client.class.getSimpleName() + " starting...");
		
		if (argv.length < 4){
			System.out.println("Error: Too few arguments!");
			System.out.printf("Usage: java %s <hostname> <port> <username> <directory>\n", Caixote_Client.class.getName());
			return;
		}
		
		/* Start by getting launch values*/
		
		String hostname = argv[0];
		int port = Integer.parseInt(argv[1]);
		String username = argv[2];
		String directory = argv[3];
				
		if (directory.contains("-")){
			System.out.printf("Invalid directory <%s>! Choose a directory that doesn't include the char '-'!\n", directory);
			return;
		}
	
		System.out.printf("Creating Client Socket for host %s in port %d\n", hostname, port);
		
		/* Create Client side Socket to establish connection */		
		
		Socket clientSocket = null;
		
		try {
			clientSocket = new Socket(hostname, port);
		} catch (UnknownHostException e) {
			System.out.println("Provided host IP address could not be determined");
			e.printStackTrace();
			return;
		} catch (IOException e) {
			System.out.println("I/O error ocurred when creating the socket");
			e.printStackTrace();
			return;
		} catch (IllegalArgumentException e){
			System.out.println("Port parameter is outside the specified range of valid port values,"
					+ " which is between 0 and 65535, inclusive. The application's recomended port number is 40");
			return;
		}
		
		System.out.println("Connection successfully established!");
		
		System.out.println("Creating Client Socket's input and output streams");
		
		/* Create output stream to server */
		
		DataOutputStream outToServer = null;
		
		try {
			outToServer = new DataOutputStream(clientSocket.getOutputStream());
		} catch (Exception e) {
			System.out.println("I/O error occured when creating the output stream or socket is not connected");
			e.printStackTrace();
			closeSocket(clientSocket);
			return;
		}
		
		/* Create input stream to server */
		
		DataInputStream inFromServer = null;
		
		try {
			inFromServer = new DataInputStream(clientSocket.getInputStream());
		} catch (Exception e) {
			System.out.println("I/O error occured when creating the input stream or socket is not connected");
			e.printStackTrace();
			closeSocket(clientSocket);
			return;
		}
		
		System.out.println("Client Socket's input and output streams created!");
		
		System.out.println("Asking server for clearance to access <directory: " + directory + "> as <username: " + username + ">");
		
		/* First, send an integer with the number of bytes to be sent containing username, then the username */
				
		try {
			outToServer.writeInt(username.getBytes().length);
			outToServer.write(username.getBytes());
		} catch (IOException e) {
			System.out.println("I/O error ocurred when sending username information to server");
			e.printStackTrace();
			closeSocket(clientSocket);
			return;
		}		
		
		/* Second, send an integer with the number of bytes to be sent containing directory name, then the directory name */
		
		try {
			outToServer.writeInt(directory.getBytes().length);
			outToServer.write(directory.getBytes());
		} catch (IOException e) {
			System.out.println("I/O error ocurred when sending directory information to server");
			e.printStackTrace();
			closeSocket(clientSocket);
			return;
		}		
		
		/* The response will say if the client has clearance, and if not, why (same user already synchronising that file / user has no permissions) */
		
		boolean hasClearance = false;
		int response;
		
		try {
			response = inFromServer.readInt();
		} catch (IOException e) {
			System.out.println("I/O error ocurred when receiving clearance response from server");
			e.printStackTrace();
			closeSocket(clientSocket);
			return;
		}
		
		if (response == REQUESTVALIDATED)
			hasClearance = true;
		
		else if (response == USERNOTOWNER)
			System.out.println("Server access negated: Username <" + username + "> does not own directory <" + directory + ">");
		
		else if (response == FILEALREADYINUSE)
			System.out.println("Server access negated: Directory <" + directory + "> is already in use");
		
		/* if clearance was given, start synchronising */
		
		if (hasClearance){		
			
			/* Synchronise files on server that aren't on server or are outdated on server and files on client that are outdated on client */
			SyncFilesClient fileVisitor = new SyncFilesClient(outToServer, inFromServer);
			
			Path directoryPath = Paths.get(directory);
			
			try {
				Files.walkFileTree(directoryPath, fileVisitor);
			} catch (IOException e) {
				System.out.println("I/O Error thrown by file visitor");
				e.printStackTrace();
			}
			
			/* End of sync, request server to sync back */
		
			try {
				outToServer.writeInt(REQUESTENDOFSYNC);
			} catch (IOException e) {
				System.out.println("I/O error ocurred when sending end of sync information to server");
				e.printStackTrace();
				closeSocket(clientSocket);
				return;
			}
			
			/* Synchronise files on client that exist only on server */
			
			while (true){
				
				
				try {
					response = inFromServer.readInt();
				} catch (IOException e) {
					System.out.println("I/O error ocurred when receiving clearance response from server");
					e.printStackTrace();
					closeSocket(clientSocket);
					return;
				}
				
				
				if (response == REQUESTENDOFSYNC)
					break;
				
				else if (){
					
				}
			}
		}
		
		/* End of sync, close socket */
		
		System.out.println("Closing client socket");
		
		closeSocket(clientSocket);
		
		System.out.println("Ending " + Caixote_Client.class.getSimpleName());
	}	
	
	/**************************** End of  Main  *************************/
	
	/******************** Auxiliary client functions  ******************/
	
	static void closeSocket(Socket socket){
		try {
			socket.close();
		} catch (IOException e) {
			System.out.println("I/O Error occurred when closing client socket");
			e.printStackTrace();
		}
		return;
	}
	
	/**************** End of auxiliary client functions  ***************/
	
}