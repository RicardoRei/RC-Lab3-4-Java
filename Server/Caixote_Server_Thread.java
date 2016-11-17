import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Caixote_Server_Thread extends Thread {
	
	/************************* Protocol variables **********************/
	
	/* Protocol request variables */	
	private final static int REQUESTSESSIONSTART = 0;
	private final static int REQUESTENDOFSYNC = 1;
	private final static int REQUESTDIRECTORYLOCK = 2;
	private final static int REQUESTLISTDIR = 3;
	private final static int REQUESTTIMEFILELASTMODIFICATION = 4;
	private final static int REQUESTFILEEXISTS = 5;
	private final static int REQUESTFILETRANSFER = 6;
	private final static int REQUESTMAKEDIR = 7;
	private final static int REQUESTFILEUPDATE = 8;
	
	/* Protocol response variables */
	private final static int REQUESTOK = 100;
	private final static int FILEALREADYINUSE = 101;
	private final static int FILEDOESNTEXIST = 102;
	private final static int FILECOULDNOTBECREATED = 103;
	private final static int FILETRANSFERFAILED = 104;
	
	/* Protocol control variables */
	private final static int ISDIRECTORY = 200;
	private final static int ISFILE = 201;
	
	/******************** End of Protocol variables ********************/
	
	/************************* Thread attributes ***********************/
	
	private Socket connectionSocket;
	private long thread_id = Thread.currentThread().getId();
	private List<String> thread_occupied_directories_list = new ArrayList<String>();
	private String username;

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
		System.out.printf("Thread #%s: Thread is now running!%n", String.valueOf(thread_id));
		
		/* Create output stream to server */
				
		DataOutputStream outToClient = null;
		try {
			outToClient = new DataOutputStream(connectionSocket.getOutputStream());
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O error occured when creating the output stream!%n", String.valueOf(thread_id));
			e.printStackTrace();
			System.out.printf("Thread #%s: Terminating thread...%n", String.valueOf(thread_id));
			closeSocket(connectionSocket);
			System.out.printf("Thread #%s: Ending %s. Bye!%n", String.valueOf(thread_id), Caixote_Server.class.getSimpleName());
			return;
		}
		
		/* Create input stream to server */
		
		DataInputStream inFromClient = null;
		try {
			inFromClient = new DataInputStream(connectionSocket.getInputStream());
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O error occured when creating the input stream!%n", String.valueOf(thread_id));
			e.printStackTrace();
			System.out.printf("Thread #%s: Terminating thread...%n", String.valueOf(thread_id));
			closeSocket(connectionSocket);
			System.out.printf("Thread #%s: Ending %s. Bye!%n", String.valueOf(thread_id), Caixote_Server.class.getSimpleName());
			return;
		}		
		
		/* Start receiving requests */
		
		byte[] message;
		int response;
		String tempMessage;
		
		while (true){
			try{
				/******** Start of try block *****/
				
				int request = inFromClient.readInt();
				
				if (request == REQUESTSESSIONSTART){
					/* First, receive an integer with the number of bytes sent containing username, then the username */
					message = readFromSocket(inFromClient);
					username = new String(message, 0 , message.length);
					
					/* Second, receive an integer with the number of bytes sent containing directory name, then the directory name */
					message = readFromSocket(inFromClient);
					String directory = new String(message, 0 , message.length);
					String requestedUserDirectory = Paths.get(username, directory).toString();
					
					/* Third, confirm that the session is valid (directory not in use)*/
					
					if (lockDirectory(requestedUserDirectory) == false){
						/* Send validation to client */					
						outToClient.writeInt(FILEALREADYINUSE);
						/* Session is invalid, end communications */				
						System.out.printf("Thread #%s: client request in not valid! Ending communications...%n", String.valueOf(thread_id));
						break;
					}
					
					outToClient.writeInt(REQUESTOK);
					
					/* Verify if User's home directory is on server file system */
					boolean exists = Files.exists(Paths.get(username, requestedUserDirectory), LinkOption.NOFOLLOW_LINKS);
					
					/* if directory exists continue, else make that directory */
					if (exists)
						continue;
					
					new File(requestedUserDirectory).mkdir();
				}
				
				else if (request == REQUESTFILEEXISTS){
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Verify if file is on server file system */
					Path dir = Paths.get(username, tempMessage);
					boolean exists = Files.exists(dir, LinkOption.NOFOLLOW_LINKS);
					
					if (exists == true)
						outToClient.writeInt(REQUESTOK);
					
					else
						outToClient.writeInt(FILEDOESNTEXIST);					
				}
				
				else if (request == REQUESTMAKEDIR){
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);					
					String requestedUserDirectory = Paths.get(username, tempMessage).toString();
					
					/* Create file on server file system */
					new File(requestedUserDirectory).mkdir();
					
					outToClient.writeInt(REQUESTOK);
				}
				
				else if (request == REQUESTLISTDIR){
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);					
					Path requestedUserDirectory = Paths.get(username, tempMessage);
					
					File file = new File(requestedUserDirectory.toString());
					File[] fileList = file.listFiles();
					
					outToClient.writeInt(fileList.length);
					  
					for (File f : fileList){
						message = Paths.get(username).relativize(Paths.get(f.getPath())).toString().getBytes();
						sendToSocket(outToClient, message);
						
						if (f.isDirectory()){
							outToClient.writeInt(ISDIRECTORY);
						}
						else {
							outToClient.writeInt(ISFILE);
						}
					}
				}
				
				else if (request == REQUESTDIRECTORYLOCK){
					/* Client requests access to directory. Read it and try to lock it */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					if (lockDirectory(Paths.get(username, tempMessage).toString()) == true){
						outToClient.writeInt(REQUESTOK);
					}					
					else
						outToClient.writeInt(FILEALREADYINUSE);
				}
				
				else if (request == REQUESTFILETRANSFER){
					/* Receive the name of the file */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					String filename = Paths.get(username, tempMessage).toString();
					
					/* Because file didn't exist, create it */
					File file = new File(filename);
					if (file.createNewFile() == false){
						System.out.printf("Thread #%s: failure while trying to receive file <%s>!%n", String.valueOf(thread_id), filename);
						outToClient.writeInt(FILECOULDNOTBECREATED);
						continue;
					}
					
					outToClient.writeInt(REQUESTOK);
					
					/* Now receive file and update it's contents */
					OutputStream fileStream;
					try {
						message = readFromSocket(inFromClient);
						fileStream = new FileOutputStream(filename, false); 
						fileStream.write(message);
						fileStream.close();
					} catch (IOException e1){
						outToClient.writeInt(FILETRANSFERFAILED);
					}
					
					outToClient.writeInt(REQUESTOK);
				}
				
				else if (request == REQUESTFILEUPDATE){
					/* Receive the name of the file */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					String filename = Paths.get(username, tempMessage).toString();
					
					/* Get the file in local file system*/
					File myFile = new File(filename);
			        message = new byte[(int) myFile.length()];
			         
			        /* Create the byte array to send */
			        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(myFile));
			        bis.read(message, 0, message.length);
			        bis.close();
			        
			        /* Send it over the socket */
			        sendToSocket(outToClient, message);
				}
				
				else if (request == REQUESTTIMEFILELASTMODIFICATION){
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					File file = new File(Paths.get(username, tempMessage).toString());
					long lastModified = file.lastModified();
					
					outToClient.writeLong(lastModified);
				}	
				
				else if (request == REQUESTENDOFSYNC){
					System.out.printf("Thread #%s: client requested end of synchronisation!%n", String.valueOf(thread_id));
					break;
				}
				
				else{
					System.out.printf("Thread #%s: client request in not on protocol list! Ending communications...%n", String.valueOf(thread_id));
					break;
				}
				
			} catch(IOException e) {
				System.out.printf("Thread #%s: I/O Error when communicating trough session socket!%n", String.valueOf(thread_id));
				e.printStackTrace();
				System.out.printf("Thread #%s: Terminating thread...%n", String.valueOf(thread_id));
				closeSocket(connectionSocket);
				System.out.printf("Thread #%s: Ending %s. Bye!%n", String.valueOf(thread_id), Caixote_Server.class.getSimpleName());
				return;	
			}
		}
		
		System.out.printf("Thread #%s: closing socket...%n", String.valueOf(thread_id));
		
		/* End of sync, close socket */				
		closeSocket(connectionSocket);
		System.out.printf("Thread #%s: Terminating %s. Bye!%n", String.valueOf(thread_id), Caixote_Server.class.getSimpleName());
	}
	
	/*********************** End of Thread "Main"  *********************/
	
	/******************** Auxiliary thread functions  ******************/
	
	/* Closes given socket, printing errors on failure */
	private void closeSocket(Socket socket){
		try {
			socket.close();
			for (String s : thread_occupied_directories_list){
				unlockDirectory(s);
			}
		} catch (IOException e) {
			System.out.printf("Thread #%s: I/O Error occurred when closing session socket!%n", String.valueOf(thread_id));
			e.printStackTrace();
		}
	}
	
	/* Reads info from socket */
	private byte[] readFromSocket(DataInputStream inFromClient) throws IOException {
		int length = inFromClient.readInt();
		byte[] message = new byte[length];
		inFromClient.readFully(message, 0, length);
		return message;
	}
	
	/* Sends info through socket */
	private void sendToSocket(DataOutputStream outToClient, byte[] message) throws IOException {
		outToClient.writeInt(message.length);
		outToClient.write(message);
	}
	
	/* This method is synchronised so that only one thread can access it at a time */
	/* Lock directory so no one accesses it at the same time */
	private synchronized boolean lockDirectory(String directory) {
		if (thread_occupied_directories_list.contains(directory))
			return true;
		
		for (String s : Caixote_Server.occupied_directories_list){
			if (directory.startsWith(s)){
				if (thread_occupied_directories_list.contains(s) == false)
					return false;
			}
		}

		thread_occupied_directories_list.add(directory);
		Caixote_Server.occupied_directories_list.add(directory);
		return true;		
	}
	
	/* Unlock directory for other clients */
	private void unlockDirectory(String directory) {
		Caixote_Server.occupied_directories_list.remove(directory);
	}

	
	/**************** End of auxiliary thread functions  ***************/
}