package client;

import java.rmi.RemoteException;

public class Neighbors implements Runnable 
{
	NodeImpl node;
	Thread neighborThread;
	
	public synchronized void run()
	{
		int phase=-1;
		try 
		{
			while(node.leaderElected!=true)
			{
				if(node.replyBackReceived==true)
				{
					phase+=1;
					node.contendForLeadership(phase);
				}
			}
			if(node.debug)
				node.superProcess.review(node.UID+" QUITTING CONTENTION FROM HERE");
		}
		catch (RemoteException e) {	e.printStackTrace();	}
	}
	
	public Thread getneighborThread() {
		return neighborThread;
	}
	
	public Neighbors(NodeImpl node)
	{
		this.node=node;
		neighborThread=new Thread(this,"neighborthread");
	}
}
