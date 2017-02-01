package client;


public class Receive implements Runnable{
	NodeImpl node;
	String msg;	
	
	public Receive(NodeImpl node,String msg)
	{
		this.node=node;
		this.msg=msg;		
		new Thread(this,"message").start();		
	}
	
	public synchronized void run()
	{
		node.message(msg);
	}
			
}
