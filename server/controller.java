package server;

import java.rmi.RemoteException;

public class controller implements Runnable 
{
	leader l;
	String msg;
	
	public synchronized void run()
	{
		try
		{
			String msgIndex[]=msg.split(",");
			if(msgIndex[0].equalsIgnoreCase("status"))
			{
				l.setTM(Integer.parseInt(msgIndex[3])+l.getTM());
				l.review(msgIndex[1]+" chooses "+msgIndex[2]+" as ring leader and total msgs exchanged "+msgIndex[3]);
				l.review("Total no. of msgs - "+l.getTM());
			}
		}  
		catch (Exception e)
		{
			try
			{
				l.review(e.toString());				
			}
			catch (RemoteException e1) {e1.printStackTrace();}
		}
	}
	
	public controller(leader l,String msg)
	{
		this.l=l;
		this.msg=msg;
		new Thread(this,"handlestatus").start();
	}
}
