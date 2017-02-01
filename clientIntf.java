package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface clientIntf	extends Remote
{
	void send(String msg) throws RemoteException;	
}
