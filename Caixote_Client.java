import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class Caixote_Client{
	
	/************************* Protocol variables **********************/
	
	/* Protocol request variables */	
	private final static int REQUESTSESSIONSTART = 0;
	private final static int REQUESTENDOFSYNC = 1;
	private final static int REQUESTDIRECTORYLOCK = 2;
	private final static int REQUESTLISTFILESONDIRECTORY = 3;
	private final static int REQUESTTIMEFILELASTMODIFICATION = 4;
	private final static int REQUESTTIMEFILEEXISTS = 5;
	
	/* Protocol response variables */
	private final static int REQUESTOK = 100;
	private final static int FILEALREADYINUSE = 101;
	private final static int FILEDOESNTEXIST = 102;
	
	/******************** End of Protocol variables ********************/
	
	/******************************* Main ******************************/
	
	public static void main(String argv[]){
		
		/* Welcome user */
		System.out.println(Caixote_Client.class.getSimpleName() + " starting...");
		
		if (argv.length < 4){
			System.out.println("Error: Too few arguments!");
			System.out.printf("Usage: java %s <hostname> <port> <username> <directory>%n", Caixote_Client.class.getName());
			return;
		}
		
		/* Start by getting launch values*/
		
		String hostname = argv[0];
		int port = Integer.parseInt(argv[1]);
		String username = argv[2];
		String directory = Paths.get(argv[3]).normalize().toString();
				
		System.out.printf("Creating Client Socket for host %s in port %d...%n", hostname, port);
		
		/* Create Client side Socket to establish connection */		
		
		Socket clientSocket = null;
		
		try {
			clientSocket = new Socket(hostname, port);
		} catch (UnknownHostException e) {
			System.out.println("Provided host IP address could not be determined!");
			e.printStackTrace();
			return;
		} catch (IOException e) {
			System.out.println("I/O error ocurred when creating the socket!");
			e.printStackTrace();
			return;
		} catch (IllegalArgumentException e){
			System.out.println("Port parameter is outside the specified range of valid port values,"
					+ " which is between 0 and 65535, inclusive. The application's recomended port number is 40.");
			return;
		}
		
		System.out.println("Connection successfully established!");
		
		System.out.println("Creating Client Socket's input and output streams...");
		
		/* Create output stream to server */
		
		DataOutputStream outToServer = null;
		
		try {
			outToServer = new DataOutputStream(clientSocket.getOutputStream());
		} catch (Exception e) {
			System.out.println("I/O error occured when creating the output stream or socket is not connected!");
			e.printStackTrace();
			System.out.println("Closing socket and ending communications...");
			closeSocket(clientSocket);
			System.out.println("Ending " + Caixote_Client.class.getSimpleName() + ". Bye!");
			return;
		}
		
		/* Create input stream to server */
		
		DataInputStream inFromServer = null;
		
		try {
			inFromServer = new DataInputStream(clientSocket.getInputStream());
		} catch (Exception e) {			
			System.out.println("I/O error occured when creating the input stream or socket is not connected!");
			e.printStackTrace();
			System.out.println("Closing socket and ending communications...");
			closeSocket(clientSocket);
			System.out.println("Ending " + Caixote_Client.class.getSimpleName() + ". Bye!");
			return;
		}
		
		System.out.println("Client Socket's input and output streams created!");
		
		System.out.println("Asking server for clearance to access <directory: " + directory + "> as <username: " + username + ">...");
		
		try{
			
			/* Request session start */		
			outToServer.writeInt(REQUESTSESSIONSTART);
			
			/* First, send an integer with the number of bytes to be sent containing username, then the username */
			byte[] message = username.getBytes();
			outToServer.writeInt(message.length);
			outToServer.write(message);
				
			/* Second, send an integer with the number of bytes to be sent containing directory name, then the directory name */
			message = directory.getBytes();
			outToServer.writeInt(message.length);
			outToServer.write(message);
			
			/* The response will say if the client has clearance, and if not, why (same user already synchronising that file / user has no permissions) */
			int response = inFromServer.readInt();
			
			if (response == FILEALREADYINUSE){
				System.out.println("Server access negated: Directory <" + directory + "> is already in use!");
				
				/* Session is invalid, end communications */				
				System.out.println("Closing socket and ending communications...");				
				closeSocket(clientSocket);				
				System.out.println("Ending " + Caixote_Client.class.getSimpleName() + ". Bye!");
				return;
			}			
			
			/* if clearance was given, start synchronising */
			System.out.println("Clearance was given! Starting synchronisation...");
		
			/* Synchronise files between server and client */
			Path startingDir = Paths.get(System.getProperty("user.dir"), directory).normalize();
			SyncFiles fileVisitor = new SyncFiles(outToServer, inFromServer, username, startingDir);
			Path directoryPath = Paths.get(directory);
			Files.walkFileTree(directoryPath, fileVisitor);
			
			/* Synchronisation is complete */
			System.out.println("Synchronisation complete! Ending communcations with server...");
			outToServer.writeInt(REQUESTENDOFSYNC);
			
		} catch (IOException e) {
			System.out.println("I/O Error when communicating trough session socket!");
			e.printStackTrace();
			System.out.println("Closing socket and ending communications...");
			closeSocket(clientSocket);
			System.out.println("Ending " + Caixote_Client.class.getSimpleName() + ". Bye!");
			return;
		}		
		
		/* End of sync, close socket */
		
		System.out.println("Closing socket and ending communications...");
		
		closeSocket(clientSocket);
		
		System.out.println("Ending " + Caixote_Client.class.getSimpleName() + ". Bye!");
	}	
	
	/**************************** End of  Main  *************************/
	
	/******************** Auxiliary client functions  ******************/
	
	private static void closeSocket(Socket socket){
		try {
			socket.close();
		} catch (IOException e) {
			System.out.println("I/O Error occurred when closing client socket!");
			e.printStackTrace();
		}
		return;
	}
	
	/**************** End of auxiliary client functions  ***************/
	
}