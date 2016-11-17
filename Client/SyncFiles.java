import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class SyncFiles extends SimpleFileVisitor<Path> {
	
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
	
	/********************** File Visitor attributes ********************/
	
	private DataOutputStream outToServer;
	private DataInputStream inFromServer;
	private Path startingDirectory;
	
	/******************* End of File Visitor attributes ****************/
	
	/********************* File Visitor Constructor ********************/
	
	public SyncFiles(DataOutputStream outToServer, DataInputStream inFromServer, Path startingDirectory) {
		this.outToServer = outToServer;
		this.inFromServer = inFromServer;
		this.startingDirectory = startingDirectory;
	}

	/***************** End of File Visitor Constructor *****************/
	
	/****************** File Visitor Override Functions ****************/
	
	/* Lock each directory to be visited: if unsuccessful, skip directory and report to client */
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
    	Path dirName = startingDirectory.relativize(dir);
    	System.out.println("Synchronising directory <" + dirName + ">...");
    	
    	/* First, check if directory exists on server */
        int existsOnServer = fileRequestExists(dirName);
        
    	/* If does'nt exist on server, request to make that directory, else files will be synchronised later */
    	if (existsOnServer == FILEDOESNTEXIST){
    		requestMkdir(dirName);
    	}
    	
    	/* Ask server to lock directory of other's usage */
    	int request = requestLockDir(dirName);
    	    	
    	/* If server sends success response message then traverse this directory */
    	if (request == REQUESTOK)
    		return CONTINUE;
    	
    	/* Else the server denies access to this directory at the moment: do not traverse this directory */
    	
    	System.out.println("Directory synchronisation failed: Directory <" + dirName + "> already being synchronised by this user somewhere else!");
   		return SKIP_SUBTREE;
    }

	/* Synchronise each file that client has  */
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
        if (attr.isSymbolicLink()) {
            return CONTINUE;
        } 
        
        Path relativeFilePath = startingDirectory.relativize(file);
        
    	/* Start by asking if file exists */
        int request = fileRequestExists(relativeFilePath);
        
    	if (request == FILEDOESNTEXIST){
    		/* File does'nt exist on server send it to server */
    		request = requestFileTransfer(relativeFilePath);
        	
        	/* After receiving response from server, check if server could successfully create the file */
        	if (request == FILETRANSFERFAILED){
        		System.out.println("File <" + file + "> could not be transfered to server!");
        		return CONTINUE;
        	}
    	}

    	else{
    		/* File does exist on server, request it's last modified date */
    		long lastModificatedOnServer = requestFileModificationTime(relativeFilePath);
        	long lastModificatedOnClient = attr.lastModifiedTime().toMillis();
        	
        	if (lastModificatedOnClient > lastModificatedOnServer){
        		/* Bring file from server to client */
        		requestUpdateFile(relativeFilePath);
        	}
        	
        	else if (lastModificatedOnClient < lastModificatedOnServer){
        		/* Send file from client to server */
        		request = requestFileTransfer(relativeFilePath);
            	
            	/* After receiving response from server, check if server could successfully create the file */
            	if (request == FILETRANSFERFAILED){
            		System.out.println("File <" + file + "> could not be transfered to server!");
            		return CONTINUE;
            	}
        	}
        	
        	/** if (lastModificatedOnClient == lastModificatedOnServer) : do nothing **/
        }

    	System.out.println("File " + startingDirectory.relativize(file) + " successfully synchronised!");        	
        
        return CONTINUE;
    }

	/* Synchronise remaining files that only server has */
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
    	Path relativeDirPath = startingDirectory.relativize(dir);
    	    			
    	/* Initialise array list with child files and child directories */
    	List <String> childFiles = new ArrayList<>(); 
    	List <String> childDirectories = new ArrayList<>();
    	
    	File file = new File(dir.toString());
		File[] fileList = file.listFiles();
    	
		/* for each file add it to childFiles / for each directory add it to childDirectories */
		for (File f : fileList){
			String filepath = Paths.get(f.getPath()).toString();
			
			if (f.isDirectory()){
				childDirectories.add(filepath);
			}
			else {
				childFiles.add(filepath);
			}
		}
		
    	/* Request to server to send the files under this directory on server's fileSystem */
    	outToServer.writeInt(REQUESTLISTDIR);
    	byte[] message = relativeDirPath.toString().getBytes();
    	sendToSocket(outToServer, message);
    	
    	/* Receive how many files (directories and files) to check */
    	int fileCount = inFromServer.readInt();
		
    	/* Initialise array list with missing child files and child directories */
    	List <String> missingChildFiles = new ArrayList<>(); 
    	List <String> missingChildDirectories = new ArrayList<>();		
    	
    	String tempMessage;
    	
    	for (int i = 0; i < fileCount; i++){
    		message = readFromSocket(inFromServer);
    		int type = inFromServer.readInt();
    		
    		if (type == ISDIRECTORY){
    			tempMessage = new String(message, 0 , message.length);
    			if (childDirectories.contains(tempMessage) == false)
    				missingChildDirectories.add(tempMessage);
    		} 
    		
    		else{
    			tempMessage = new String(message, 0 , message.length);
    			if (missingChildFiles.contains(tempMessage) == false)
    				missingChildFiles.add(tempMessage);
    		}
    	}

    	for (String s : missingChildFiles){
    		requestUpdateFile(Paths.get(s));
    	}
    	
    	for (String s : missingChildDirectories){
    		File newDir = new File(s);
    		newDir.createNewFile();
    		
			/* Synchronise files between server and client */
			Path newStartingDir = Paths.get(startingDirectory.toString(), s);
			SyncFiles fileVisitor = new SyncFiles(outToServer, inFromServer, newStartingDir);
			Files.walkFileTree(Paths.get(s), fileVisitor);
    	}

    	System.out.println("Directory <" + relativeDirPath + "> successfully synchronised!");
    	
        return CONTINUE;
    }
    
	/************** End of File Visitor Override Functions *************/
    
    /***************** Auxiliary File Visitor functions  ***************/
    
	/* Reads info from socket */
	private byte[] readFromSocket(DataInputStream inFromServer) throws IOException {
		int length = inFromServer.readInt();
		byte[] message = new byte[length];
		inFromServer.readFully(message, 0, length);
		return message;
	}
	
	/* Sends info through socket */
	private void sendToSocket(DataOutputStream outToServer, byte[] message) throws IOException {
		outToServer.writeInt(message.length);
		outToServer.write(message);
	}
	
	/* Request server to check if file exists */
	private int fileRequestExists(Path file) throws IOException {
    	outToServer.writeInt(REQUESTFILEEXISTS);
    	
    	byte[] message = file.toString().getBytes();
    	
    	sendToSocket(outToServer, message);
    	
    	return inFromServer.readInt();
	}
	
	/* Request server to make directory */
	private int requestMkdir(Path dir) throws IOException {
    	outToServer.writeInt(REQUESTMAKEDIR);
    	
    	byte[] message = dir.toString().getBytes();
    	
    	sendToSocket(outToServer, message);
    	
    	return inFromServer.readInt();
	}
    
    /* Request server to lock directory of other use*/
    private int requestLockDir(Path dirName) throws IOException {
    	outToServer.writeInt(REQUESTDIRECTORYLOCK);
    	byte[] message = dirName.toString().getBytes();
    	sendToSocket(outToServer, message);
    	
    	return inFromServer.readInt();
	}
    
    /* Request server to receive file to be updated/created on server's file system */
	private int requestFileTransfer(Path file) throws IOException {
    	outToServer.writeInt(REQUESTFILETRANSFER);
    	
    	/* Send name of the file */
    	byte [] message = file.toString().getBytes();
    	sendToSocket(outToServer, message);
    	
    	int response = inFromServer.readInt();
    	
    	/* After receiving response from server, go no further if server can't create the file */
    	if (response == FILECOULDNOTBECREATED){
    		System.out.println("File <" + message.toString() + "> could not be transfered to server!");
    		return FILECOULDNOTBECREATED;
    	}
    	
    	/* If it can, send the file over the socket (each file can have at most 2^31 - 1 bytes = ~2GB)*/
    	File myFile = new File(file.toString());
        message = new byte[(int) myFile.length()];
         
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(myFile));
        bis.read(message, 0, message.length);
        bis.close();
        sendToSocket(outToServer, message);
        
        return inFromServer.readInt();
	}

	/* Request server to send file to be updated/created on client's file system */
	private void requestUpdateFile(Path file) throws IOException {

		outToServer.writeInt(REQUESTFILEUPDATE);
    	
    	/* Send name of the file */
    	byte [] message = file.toString().getBytes();
    	sendToSocket(outToServer, message);
    	
    	/* Now receive file and update it's contents (File must exist on local file system!) */
		OutputStream fileStream;
		message = readFromSocket(inFromServer);
		fileStream = new FileOutputStream(file.toString(), false); 
		fileStream.write(message);
		fileStream.close();
	}
	
	/* Request Server for last modified time of file, in milliseconds */
    private long requestFileModificationTime(Path file) throws IOException {
    	outToServer.writeInt(REQUESTTIMEFILELASTMODIFICATION);
    	
    	/* Send name of the file */
    	byte[] message = file.toString().getBytes();
    	sendToSocket(outToServer, message);
    	
    	return inFromServer.readLong();   
	}

    /************** End of Auxiliary File Visitor functions  ***********/
}