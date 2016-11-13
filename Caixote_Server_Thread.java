import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Caixote_Server_Thread extends Thread {
	
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
	
	/************************* Thread attributes ***********************/
	
	private Socket connectionSocket;
	private long thread_id = Thread.currentThread().getId();
	private String username;
	private String directory;

	/******************** End of Thread attributes *********************/
	
	/************************* Thread constructor **********************/
	
	/* Class Constructor takes the given socket from server */
	public Caixote_Server_Thread(Socket connectionSocket){
		this.connectionSocket = connectionSocket;
	}
	
	/********************* End of thread constructor *******************/
	
	/************************* Thread "Main"  **************************/
	
	/* Method used to get the thread running */
	public void run(){
		
		/* Welcome user */
		System.out.printf("Thread #%s: Thread is now running\n", String.valueOf(thread_id));
		
		/* Create output stream to server */
				
		DataOutputStream outToClient = null;
		try {
			outToClient = new DataOutputStream(connectionSocket.getOutputStream());
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O error occured when creating the output stream or socket is not connected\n", String.valueOf(thread_id));
			e.printStackTrace();
			closeSocket(connectionSocket);
			return;
		}
		
		/* Create input stream to server */
		
		DataInputStream inFromClient = null;
		try {
			inFromClient = new DataInputStream(connectionSocket.getInputStream());
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O error occured when creating the input stream or socket is not connected\n", String.valueOf(thread_id));
			e.printStackTrace();
			closeSocket(connectionSocket);
			return;
		}		
		
		/* First, receive an integer with the number of bytes sent containing username, then the username */
		
		try {
			int length = inFromClient.readInt();
			byte[] message = new byte[length];
			inFromClient.readFully(message, 0, message.length);
			username = new String(message, 0 , message.length);
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O Error when reading from session socket", String.valueOf(thread_id));
			e.printStackTrace();
			closeSocket(connectionSocket);
			return;
		}
		
		/* Second, receive an integer with the number of bytes sent containing directory name, then the directory name */
		
		try {
			int length = inFromClient.readInt();
			byte[] message = new byte[length];
			inFromClient.readFully(message, 0, message.length);
			directory = new String(message, 0 , message.length);
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O Error when reading from session socket", String.valueOf(thread_id));
			e.printStackTrace();
			closeSocket(connectionSocket);
			return;
		}
		
		/* Third, confirm that the session is valid */
		
		int decision = REQUESTVALIDATED;
		
		if (userIsOwner(directory, username) == false)
			decision = USERNOTOWNER;
		
		else if (directoryIsRunning(directory)){
			decision = FILEALREADYINUSE;
		}
		
		/* Send validation to client */
		
		try {
			outToClient.writeInt(decision);
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O error ocurred when sending session validation to client", String.valueOf(thread_id));
			e.printStackTrace();
			closeSocket(connectionSocket);
			return;
		}
		
		/* if clearance was given, start synchronising */
		
		if (decision == REQUESTVALIDATED){
		
			/* Start synchronising files on client that are out-dated on client and files on server that are only on client or out-dated on server */
			
			int response;
			
			while (true){			
				
				try {
					response = inFromClient.readInt();
				} catch (IOException e) {
					System.out.printf("Thread #%s: I/O error ocurred when receiving request from client", String.valueOf(thread_id));
					e.printStackTrace();
					closeSocket(connectionSocket);
					return;
				}
				
				
				if (response == REQUESTENDOFSYNC)
					break;
				
				else if (){
					
				}
			}
			
			/* Synchronise files on client that are only on server */
			SyncFilesServer fileVisitor = new SyncFilesServer(outToServer, inFromServer);
			
			Path directoryPath = Paths.get(directory);
			
			try {
				Files.walkFileTree(directoryPath, fileVisitor);
			} catch (IOException e) {
				System.out.printf("Thread #%s: I/O error thrown by file visitor", String.valueOf(thread_id));
				e.printStackTrace();
			}
			
			/* End of sync, request server to sync back */
		
			try {
				outToClient.writeInt(REQUESTENDOFSYNC);
			} catch (IOException e) {
				System.out.println("I/O error ocurred when sending end of sync information to server");
				e.printStackTrace();
				closeSocket(connectionSocket);
				return;
			}
		}
		
		/* End of sync, close socket */
				
		closeSocket(connectionSocket);
		
		System.out.printf("Thread #%s: client request completed successfully. Thread terminating\n", String.valueOf(thread_id));
	}
	
	/*********************** End of Thread "Main"  *********************/
	
	/******************** Auxiliary thread functions  ******************/
	
	/* Closes given socket, printing errors on failure */
	static void closeSocket(Socket socket){
		try {
			socket.close();
		} catch (IOException e) {
			System.out.println("I/O Error occurred when closing session socket");
			e.printStackTrace();
		}
	}
	
	/* This method is synchronized so that only one thread can access it at a time */
	private synchronized boolean directoryIsRunning(String directory) {
		return Caixote_Server.occupied_directories_list.contains(directory);
	}
	
	/* This method is synchronized so that only one thread can access it at a time */
	private synchronized void addRunningDirectory(String directory) {
		Caixote_Server.occupied_directories_list.add(directory);
	}
	
	/* This method verifies if the user is the owner of the given directory */
	private boolean userIsOwner(String directory, String username) {
		return directory.startsWith(username + "-");
	}

	
	/**************** End of auxiliary thread functions  ***************/
}