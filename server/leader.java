package server;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;

@SuppressWarnings("serial")
public class leader extends UnicastRemoteObject implements serverIntf {

	private static final int PORT = 1234;
	private int Size;
	private int TM;
	ArrayList<Integer> nodeID;
	boolean debug;

	
	public void nodeStart() throws IOException
	{
		try 
		{
			
			String left, right;
			nodeID = new ArrayList<Integer>(getSize());
			for (int i = 0; i < getSize(); i++)
			{
				int rand = (int) Math.round(Math.random() * 1000);
				if(nodeID.contains(rand))
					i--;
				else
					nodeID.add(rand);
			}

			Collections.shuffle(nodeID);
			System.out.println("Registered Nodes:");

			for (int i = 0; i < getSize(); i++) 
			{
				String ID = nodeID.get(i).toString();

				if (i == 0) {
					left = nodeID.get(getSize() - 1).toString();
					right = nodeID.get(i + 1).toString();
				} else if (i == getSize() - 1) {
					left = nodeID.get(i - 1).toString();
					right = nodeID.get(0).toString();
				} else {
					left = nodeID.get(i - 1).toString();
					right = nodeID.get(i + 1).toString();
				}
				
				
				ProcessBuilder proc=null;
				if(debug==true)
					proc= new ProcessBuilder("java", "client.NodeImpl",ID, left, right,new Integer(1).toString());
				else if(debug!=true)
					proc= new ProcessBuilder("java", "client.NodeImpl",ID, left, right,new Integer(0).toString());
				proc.directory(new File(System.getProperty("user.dir")+ "/bin"));
				try 
				{
					proc.start();
				}
				catch (IOException e1) {e1.printStackTrace();}	
				boolean done = false;
				
				while (!done) 
				{
					
					try
					{
						LocateRegistry.getRegistry(PORT).lookup(ID.toString());
						done = true;
						review(ID);
					}
					catch (Exception e) 
					{						
						
					}
				}
			}
			System.out.println();
			//This is the ring structure
			for (int i = 0; i < getSize(); i++) 
			{
				//now we send a message to each node to start the election process
				System.out.print(nodeID.get(i).toString()+"-");
			}
			System.out.println();
			
			for (int i = 0; i < getSize(); i++) 
			{
				//now we send a message to each node to start the election process
				new indicator(nodeID.get(i).toString());
			}
			System.out.println();
			System.out.println();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public leader(int size,boolean debug) throws RemoteException{
		try
		{
			setSize(size);
			this.debug=debug;
		}
		catch (Exception ex) {ex.printStackTrace();	}
	}
	
	public int getSize() {
		return Size;
	}
	
		
	public void setSize(int Size) {
		this.Size = Size;
	}
	
	public int getTM() {
		return TM;
	}

	public void setTM(int TM) {
		this.TM = TM;
	}

	public synchronized void review(String message) throws RemoteException 
	{
			System.out.println(message);
	}
	
	public void statusreview(String message) throws RemoteException 
	{
		
		new controller(this,message);
	}
	public static void main(String[] args) 
	{
		try
		{
			Registry registry;
			int Pros = Integer.parseInt(args[0]);
			leader l=new leader(Pros,false);			
			registry = LocateRegistry.createRegistry(PORT);
			registry.rebind("L",l);			
			l.nodeStart();					
		}
		catch(Exception e)
		{
			System.out.println("Message: "+e.toString());
		}
	}	
}
