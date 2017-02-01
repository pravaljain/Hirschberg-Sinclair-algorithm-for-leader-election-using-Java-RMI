package client;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

import server.serverIntf;

import client.Receive;

@SuppressWarnings("serial")
public class NodeImpl extends UnicastRemoteObject implements client.clientIntf
{
	final int MSG_TYPE=0;
	final int SENDER=1;	
	final int MSG_UID=2;
	final int DIRECTION=3;
	final int HOP_COUNT=4;
	final int REPLY_BACK=5;
	
	final int PORT = 1234;
	
	int UID;
	int left;
	int right;
	int totalMsgs;	
	String rightMsg;				//store the right message for contention
	String leftMsg;					//store the left message for contention
	boolean contentionStarted;		//a flag to indicate whether we have started our contention
	boolean leftReplyBackReceived;	//a flag to signal if the left and right reply back messages are received
	boolean rightReplyBackReceived;
	boolean leftLeaderReceived;
	boolean rightLeaderReceived;
	boolean leaderElected;
	boolean debug;
	boolean replyBackReceived;
	Neighbors contender;
	serverIntf superProcess;	
	
	protected NodeImpl(int uid,int leftId, int rightId,boolean debug) throws RemoteException 
	{
		super();	
		UID=uid;
		left=leftId;
		right=rightId;
		contentionStarted=false;
		leftReplyBackReceived=true;
		rightReplyBackReceived=true;
		replyBackReceived=true;
		leftLeaderReceived=false;
		rightLeaderReceived=false;
		leaderElected=false;
		totalMsgs=0;	//counts only sent messages and relayed messages
		this.debug=debug;
		try 
		{
			superProcess=(serverIntf)LocateRegistry.getRegistry(PORT).lookup("L");
		} catch (NotBoundException e) {	e.printStackTrace();}
	}		
	
	public void send(String msg) throws RemoteException
	{		
		new Receive(this,msg);				
	}  		
	
	public synchronized void message(String msg)
	{
		try 
		{
			String msgIndex[]=msg.split(",");
			if(debug)
				superProcess.review(UID+" Received message "+msg+" at "+UID);		
							
			//examine the string message to know what kind of message has been sent
			if(msgIndex[MSG_TYPE].equalsIgnoreCase("msg"))
			{			
				//if a msg is not a reply back message based on size
				if(msgIndex.length==5)
				{
					//if the received message has my identifier then check for the direction 
					if(msgIndex[MSG_UID].equals(new Integer(UID).toString()))
					{								
						//here we have to check for the condition if we have started the contention
						if(contentionStarted)							
						{												
							//THIS PART IS FOR CHECKONG LEADER ELECTION CONTENTION
							//first check the direction
							if(msgIndex[DIRECTION].equalsIgnoreCase("r"))
							{
								//if this message is received on the left link, then we have seen our own message
								if(msgIndex[SENDER].equals(new Integer(left).toString()))
								{
									//we have received the contention message we sent on our right link																
									if(debug)
										superProcess.review(System.currentTimeMillis()+" "+UID+" Leader indicator message received on left link "+msg);
									rightLeaderReceived=true;									
								}
							}
							else if(msgIndex[DIRECTION].equalsIgnoreCase("l"))
							{
								//if this message is received on the right link, then we have seen our own message
								if(msgIndex[SENDER].equals(new Integer(right).toString()))
								{
									//we have received the contention message we sent on our left link							
									if(debug)
										superProcess.review(UID+" Leader indicator message received on right link "+msg);
									leftLeaderReceived=true;
								}
							}
							if(leftLeaderReceived==true && rightLeaderReceived==true)
							{
								//stop the contention and declare leader
								leaderElected=true;								
								//send out the elected leader message with our UID
								try
								{
									clientIntf leftNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(left).toString());
									clientIntf rightNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(right).toString());									
									rightNode.send("leader,"+UID+","+UID);							
									leftNode.send("leader,"+UID+","+UID);
									
									//notify the waiting threads
									leaderElected=true;
									notifyAll();									
									
									//unbind the leader from the registry
									LocateRegistry.getRegistry(1234).unbind(new Integer(UID).toString());
									
									boolean done = false;
									while (!done) 
									{
										try
										{
											LocateRegistry.getRegistry(PORT).lookup(new Integer(UID).toString());																
										}
										catch (Exception e) 
										{						
											done = true;
											break;
										}
									}									
									//send message to superprocess that leader is elected												
									superProcess.statusreview("status,"+UID+","+UID+","+totalMsgs);
									System.exit(0);
								}
								catch (NotBoundException e) {	e.printStackTrace();}
							}
						}
					}
					//if the MSG_UID is != our UID
					else
					{
						//we check to see if the UID in the MSG is greater than or less than our UID
						if(Integer.parseInt(msgIndex[MSG_UID])>UID)
						{
							//if it is greater than our UID then we just relay the message depending on the hop count
							//replacing the sender ID with ours
							if(Integer.parseInt(msgIndex[HOP_COUNT])!=1)
							{
								//if its not 1 then we are not the last node on the path so we decrease the HOP_COUNT by 1 and relay
								//the message in the direction it is supposed to								
								String M="msg,"+UID+","+msgIndex[MSG_UID]+","+msgIndex[DIRECTION]+","+(Integer.parseInt(msgIndex[HOP_COUNT])-1);
								
								//if we are supposed to relay to the right link
								if(msgIndex[DIRECTION].equalsIgnoreCase("r"))
								{
									try
									{
										clientIntf rightNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(right).toString());
										rightNode.send(M);
										totalMsgs+=1;
									}
									catch (NotBoundException e) {e.printStackTrace();}
									
									if(debug)
										superProcess.review(UID+" Relaying message on the right link. My UID < MSG_UID  "+M);
								}
								//if we are supposed to relay the left link
								else if(msgIndex[DIRECTION].equalsIgnoreCase("l"))
								{																	
									try
									{
										clientIntf leftNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(left).toString());
										leftNode.send(M);
										totalMsgs+=1;
									}
									catch (NotBoundException e) {e.printStackTrace();}
									
									if(debug)
										superProcess.review(UID+" Relaying message on the left link. My UID < MSG_UID  "+M);
								}
							}
							//if the hop count is 1,  
							else if(Integer.parseInt(msgIndex[HOP_COUNT])==1)
							{
								//if the hop count is 1, then we have to relay it back on the opposite link it came in
								//to the sender								
								if(msgIndex[DIRECTION].equalsIgnoreCase("r"))
								{									
									String M = "msg,"+UID+","+msgIndex[MSG_UID]+","+"l,1,RB";
									//implement the lookup and send code
									try 
									{
										clientIntf leftNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(left).toString());
										leftNode.send(M);
										totalMsgs+=1;
									}
									catch (NotBoundException e) {e.printStackTrace();	}
									
									if(debug)
										superProcess.review(UID+" Replying message on the left link. My UID < MSG_UID and HOP_COUNT 1 "+M);
								}
								else if(msgIndex[DIRECTION].equalsIgnoreCase("l"))
								{									
									String M = "msg,"+UID+","+msgIndex[MSG_UID]+","+"r,1,RB";
									try
									{
										clientIntf rightNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(right).toString());
										rightNode.send(M);
										totalMsgs+=1;
									}
									catch (NotBoundException e) {e.printStackTrace();}
									
									if(debug)
										superProcess.review(UID+" Replying message on the right link. My UID < MSG_UID and HOP_COUNT 1 "+M);
								} 
							}
						}
						//if the MSG_UID is less than our UID then we drop this message by not doing anything
						if(Integer.parseInt(msgIndex[MSG_UID])<UID)
						{
							if(debug)
								superProcess.review(UID+" Dropped message "+msg+". My UID > MSG_UID");
						}																							 
					}					
				}
				//if the reply back field is not empty
				else if(msgIndex.length==6 && msgIndex[REPLY_BACK].equalsIgnoreCase("rb"))
				{
					//if a message is a reply back message and we've STARTED our contention, then we examine to see if the MSG_UID == our UID
					if(contentionStarted==true && (msgIndex[MSG_UID].equals(new Integer(UID).toString())))
					{					
						//see if this message was received on the right/left link
						if(msgIndex[SENDER].equals(new Integer(right).toString()))
						{							
							rightReplyBackReceived=true;							
							if(debug)
								superProcess.review(UID+" Reply Back received on right link and rightReplyBackValue is "+rightReplyBackReceived+" and leftReplyBackValue is"+leftReplyBackReceived);
						}
						if(msgIndex[SENDER].equals(new Integer(left).toString()))
						{
							leftReplyBackReceived=true;
							if(debug)
								superProcess.review(UID+" Reply Back received on left link and rightReplyBackValue is "+rightReplyBackReceived+" and leftReplyBackValue is"+leftReplyBackReceived);
						}
						if(rightReplyBackReceived==true && leftReplyBackReceived==true)
						{
							replyBackReceived=true;
							contentionStarted=false;
							if(debug)
								superProcess.review(UID+" Inside wake up process, rightReplyBackReceived is "+rightReplyBackReceived+" leftReplyBackReceived is "+leftReplyBackReceived);
							if(debug)
								superProcess.review(UID+" Wake up the contender thread for message "+msg);
							
							//wake up the waiting thread
							notifyAll();
						}
					}
					//if a message is a reply back message and the MSG_UID>our UID, we just relay
					//the msg
					else if(!(msgIndex[MSG_UID].equals(new Integer(UID).toString())) && (Integer.parseInt(msgIndex[MSG_UID])>UID))
					{									
						//if the node left to us sent it, then we send it to our right
						if(msgIndex[SENDER].equals(new Integer(left).toString()))
						{									
							String M = "msg,"+UID+","+msgIndex[MSG_UID]+","+msgIndex[DIRECTION]+",1,RB";
							try 
							{
								clientIntf rightNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(right).toString());
								rightNode.send(M);
								totalMsgs+=1;
							}
							catch (NotBoundException e) {	e.printStackTrace();}
							
							if(debug)
								superProcess.review(UID+" Relaying REPLY_BACK message on the right link. "+M);
						}
						else if(msgIndex[SENDER].equals(new Integer(right).toString()))
						{									
							String M = "msg,"+UID+","+msgIndex[MSG_UID]+","+msgIndex[DIRECTION]+",1,RB";
							try
							{
								clientIntf leftNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(left).toString());
								leftNode.send(M);
								totalMsgs+=1;
							}
							catch (NotBoundException e) {e.printStackTrace();	}
							
							if(debug)
								superProcess.review(UID+" Relaying REPLY_BACK message on the left link. "+M);
						}
					}							
				}
			}
			if(msgIndex[MSG_TYPE].equalsIgnoreCase("start"))
			{
				//we start the thread for contention
				contender=new Neighbors(this);
				contender.getneighborThread().start();								
			}
			if(msgIndex[MSG_TYPE].equalsIgnoreCase("leader"))
			{
				//this check prevents redundant processing for LEADER messages previously received from either left/right link
				if(leaderElected==false)
				{
					//if we have received our leader message
					leaderElected=true;				
					//then we propagate around the ring by relaying the message in the other link other than the one it received this message from.
					try
					{																 //to prevent forwarding to leader who might've already
																					 //be not bounded 
						if(msgIndex[SENDER].equals(new Integer(left).toString()) && !(new Integer(right).toString().equals(msgIndex[2])))
						{
							if(!(msgIndex[SENDER].equals(new Integer(right).toString())))
									((clientIntf)(LocateRegistry.getRegistry(PORT).lookup(new Integer(right).toString()))).send("leader,"+UID+","+msgIndex[2]);							
						}															  //to prevent forwarding to leader who might've already
																					  //be not bounded
						if(msgIndex[SENDER].equals(new Integer(right).toString()) && !(new Integer(left).toString().equals(msgIndex[2])))
						{
							if(!(msgIndex[SENDER].equals(new Integer(left).toString())))
								((clientIntf)(LocateRegistry.getRegistry(PORT).lookup(new Integer(left).toString()))).send("leader,"+UID+","+msgIndex[2]);							
						}
																		
						//reply the status back telling that the LEADER has been elected and the total no. of messages relayed/sent by us
						if(debug)
							superProcess.review(UID+" Leader has been elected and the leader is "+msgIndex[2]);
						
						superProcess.statusreview("status,"+UID+","+msgIndex[2]+","+totalMsgs);
						
						//unregister ourselves from the rmi registry 
						LocateRegistry.getRegistry(1234).unbind(new Integer(UID).toString());						
						boolean done = false;
						//wait till we are unbounded
						while (!done) 
						{
							try
							{
								LocateRegistry.getRegistry(PORT).lookup(new Integer(UID).toString());								
							}
							catch (Exception e) 
							{						
								done = true;
								break;
							}
						}					
						
						//exit the process
						//wake up any threads that might be waiting on this object/method
						notifyAll();												
						System.exit(0);						
					}
					catch (NotBoundException e) {	superProcess.review(UID+" "+e.toString());e.printStackTrace();}
				}
				else					
				{	
					if(debug)
						superProcess.review(UID+" Received leader message after already receiving it");
					//check if this object is already unbounded
					boolean done = false;
					while (!done) 
					{
						try
						{
							LocateRegistry.getRegistry(PORT).lookup(new Integer(UID).toString());
						}
						catch (Exception e) 
						{						
							done = true;
							break;
						}
					}						
				}
				//same as above
				notifyAll();
				System.exit(0);
			}
		}
		catch (RemoteException e)
		{			
			try 
			{
				superProcess.review("Exception here"+e.toString());
			}
			catch (AccessException e1) {e1.printStackTrace();}
			catch (RemoteException e1) {e1.printStackTrace();}			
		}
	}

	public synchronized void contendForLeadership(int phase)
	{
		try 
		{							
			if(replyBackReceived==true && leaderElected!=true)
			{	
				if(debug)
					superProcess.review(UID+" Contending on phase "+phase);
				
				rightMsg="msg,"+UID+","+UID+",r,"+(int)(Math.pow(2,phase));
				leftMsg="msg,"+UID+","+UID+",l,"+(int)(Math.pow(2,phase));
				if(debug)
					superProcess.review(UID+" 's messages are "+leftMsg+" "+rightMsg);
				clientIntf leftNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(left).toString());			
				clientIntf rightNode=(clientIntf)LocateRegistry.getRegistry(PORT).lookup(new Integer(right).toString());
							
				rightReplyBackReceived=leftReplyBackReceived=false;
				replyBackReceived=false;
				
				//	now send the right and left node the message
				leftNode.send(leftMsg);
				rightNode.send(rightMsg);
				
				//count sent messages
				totalMsgs+=2;														
				
				contentionStarted=true;
				
				//notification not necessary as this thread exits normally
				notifyAll();
				
				if(debug)
					superProcess.review(UID+" Under wait() check after sending contention messages. Contender thread going on wait.");					
			}
			if(replyBackReceived==false)
			{			
				//we put this thread on wait
				wait();										
			}
		}
		catch (RemoteException e) {	e.printStackTrace();	}
		catch (InterruptedException e) {	e.printStackTrace(); 	}
		catch (NotBoundException e) {	e.printStackTrace();	}
	}
			
	public static void main(String args[]) throws RemoteException
	{
		try 
		{
			boolean debug=false;
			if(Integer.parseInt(args[3])==1)
				debug=true;
			LocateRegistry.getRegistry(1234).rebind(args[0],new NodeImpl(Integer.parseInt(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]),debug));
		}
		catch (NumberFormatException e) {	e.printStackTrace();}			
	}
	
}