package server;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

import client.clientIntf;

public class indicator implements Runnable
{
	String id;	
	
	public void run()
	{
		try 
		{
			((clientIntf)(LocateRegistry.getRegistry(1111).lookup(id))).send("start,");
		} 
		catch (AccessException e) {	e.printStackTrace();	}
		catch (RemoteException e) {	e.printStackTrace();	} 
		catch (NotBoundException e) {e.printStackTrace();	}
	}
	
	public indicator(String id)
	{
		this.id=id;
		new Thread(this,id).start();
	}
}
