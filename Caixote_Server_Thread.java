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
		System.out.printf("Thread #%s: thread is now running!%n", String.valueOf(thread_id));
		
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
		String tempMessage;
		
		while (true){
			try{				
				int request = inFromClient.readInt();
				
				if (request == REQUESTSESSIONSTART){
					/* First, receive an integer with the number of bytes sent containing user name, then the user name */
					message = readFromSocket(inFromClient);
					username = new String(message, 0 , message.length);
					
					/* Second, receive an integer with the number of bytes sent containing directory name, then the directory name */
					message = readFromSocket(inFromClient);
					String directory = new String(message, 0 , message.length);
					String requestedUserDirectory = Paths.get(System.getProperty("user.dir"), username, directory).toString();
					
					/* Third, confirm that the session is valid (directory not in use)*/
					if (lockDirectory(requestedUserDirectory) == false){
						/* Send validation to client */					
						outToClient.writeInt(FILEALREADYINUSE);
						/* Session is invalid, end communications */				
						System.out.printf("Thread #%s: client request in not valid! Ending communications...%n", String.valueOf(thread_id));
						break;
					}
									
					/* Verify if user's home directory is on server file system */
					boolean exists = Files.exists(Paths.get(requestedUserDirectory), LinkOption.NOFOLLOW_LINKS);
					/* if directory exists continue */
					if (exists){
						/* Respond to client with success message */
						outToClient.writeInt(REQUESTOK);
						continue;
					}
					
					/* If file doesn't exist, make that directory (or directories till the last one) */
					new File(requestedUserDirectory).mkdirs();
					
					/* Respond to client with success message */
					outToClient.writeInt(REQUESTOK);
				}
				
				else if (request == REQUESTFILEEXISTS){
					/* First, receive an integer with the number of bytes sent containing file name, then the file name */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Verify if file is on server file system (format the path -> absolute path in server <example> clientFile -> C:\....\clientUserName\clientFile) */
					Path requestedUserFile = Paths.get(System.getProperty("user.dir"), username, tempMessage);
					boolean exists = Files.exists(requestedUserFile, LinkOption.NOFOLLOW_LINKS);
					
					/* Respond to client with success/failure message (failure means file doesn't exist on server file system)*/
					
					if (exists == true)
						outToClient.writeInt(REQUESTOK);
					
					else
						outToClient.writeInt(FILEDOESNTEXIST);					
				}
				
				else if (request == REQUESTMAKEDIR){
					/* This request is only sent if this file does'nt exist on server file system */
					/* First, receive an integer with the number of bytes sent containing directory name, then the directory name */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Format the path -> absolute path in server <example> clientDir -> C:\....\clientUserName\clientDir */
					Path requestedUserDirectory = Paths.get(System.getProperty("user.dir"), username, tempMessage);
					/* Create directory on server file system */
					new File(requestedUserDirectory.toString()).mkdirs();
					
					/* Respond with success message */
					outToClient.writeInt(REQUESTOK);
				}
				
				else if (request == REQUESTLISTDIR){
					/* First, receive an integer with the number of bytes sent containing directory name, then the directory name */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Format the path -> absolute path in server <example> clientDir -> C:\....\clientUserName\clientDir */
					Path requestedUserDirectory = Paths.get(System.getProperty("user.dir"), username, tempMessage);
					File file = new File(requestedUserDirectory.toString());
					File[] fileList = file.listFiles();
					
					/* Notify client that server will be sending fileList.lenght file names */
					outToClient.writeInt(fileList.length);
					
					Path userDir = Paths.get(System.getProperty("user.dir"), username);
					
					for (File f : fileList){
						/* Format the absolute path in server -> relative path <example> C:\....\clientUserName\clientDir -> clientDir*/
						message = userDir.relativize(Paths.get(f.getPath())).toString().getBytes();
						
						/* Send file name */
						sendToSocket(outToClient, message);
						
						/* Send file type (directory / file) */
						if (f.isDirectory()){
							outToClient.writeInt(ISDIRECTORY);
						}
						else {
							outToClient.writeInt(ISFILE);
						}
					}
				}
				
				else if (request == REQUESTDIRECTORYLOCK){
					/* This request is only sent if this directory exists on server directory system */
					/* First, receive an integer with the number of bytes sent containing directory name, then the directory name */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Format the path -> absolute path in server <example> clientDir -> C:\....\clientUserName\clientDir */
					Path requestedUserDirectory = Paths.get(System.getProperty("user.dir"), username, tempMessage);
					
					/* Attempt to lock the directory for personal use. If this file was already lock, this returns false */
					if (lockDirectory(requestedUserDirectory.toString()) == true){
						outToClient.writeInt(REQUESTOK);
					}					
					else
						outToClient.writeInt(FILEALREADYINUSE);
				}
				
				else if (request == REQUESTFILETRANSFER){
					/* This request is only sent if this file does'nt exist on server file system */
					/* First, receive an integer with the number of bytes sent containing file name, then the file name */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Format the path -> absolute path in server <example> clientFile -> C:\....\clientUserName\clientFile */
					Path requestedUserFile = Paths.get(System.getProperty("user.dir"), username, tempMessage);
					
					/* Because file didn't exist, create it */
					File file = new File(requestedUserFile.toString());
					if (file.createNewFile() == false){
						/* On failure, notify client that file couldn't be created */
						System.out.printf("Thread #%s: failure while trying to receive file <%s>!%n", String.valueOf(thread_id), requestedUserFile);
						outToClient.writeInt(FILECOULDNOTBECREATED);
						continue;
					}
					
					/* On success, notify client that file could be created */					
					outToClient.writeInt(REQUESTOK);
					
					/* Now receive file and update it's contents */
					OutputStream fileStream;
					try {
						message = readFromSocket(inFromClient);
						fileStream = new FileOutputStream(requestedUserFile.toString(), false); 
						fileStream.write(message);
						fileStream.close();
					} catch (IOException e1){
						/* On failure, notify client that file couldn't be synchronized */		
						outToClient.writeInt(FILETRANSFERFAILED);
					}
					
					/* On success, notify client that file could be synchronized */		
					outToClient.writeInt(REQUESTOK);
				}
				
				else if (request == REQUESTFILEUPDATE){
					/* First, receive an integer with the number of bytes sent containing file name, then the file name */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Format the path -> absolute path in server <example> clientFile -> C:\....\clientUserName\clientFile */
					Path requestedUserFile = Paths.get(System.getProperty("user.dir"), username, tempMessage);
					
					/* Get the file in local file system */
					File myFile = new File(requestedUserFile.toString());
			        message = new byte[(int) myFile.length()];
			         
			        /* Create the byte array to send */
			        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(myFile));
			        bis.read(message, 0, message.length);
			        bis.close();
			        
			        /* Send it over the socket */
			        sendToSocket(outToClient, message);
				}
				
				else if (request == REQUESTTIMEFILELASTMODIFICATION){
					/* First, receive an integer with the number of bytes sent containing file name, then the file name */
					message = readFromSocket(inFromClient);
					tempMessage = new String(message, 0 , message.length);
					
					/* Format the path -> absolute path in server <example> clientFile -> C:\....\clientUserName\clientFile */
					Path requestedUserFile = Paths.get(System.getProperty("user.dir"), username, tempMessage);
					
					/* Get the file in local file system */
					File file = new File(requestedUserFile.toString());
					long lastModified = file.lastModified();
					
					/* Send it's last modified date over the socket */
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
	
	/* This method is synchronized so that only one thread can access it at a time */
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