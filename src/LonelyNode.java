import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;



public class LonelyNode extends Thread implements PeerSearchSimplified {
	
	protected class Checker implements Runnable {

		String nodeId ;
		
		public Checker (String nodeId){
			this.nodeId = nodeId ;
		}
		@Override
		public void run() {
			checkDeath(nodeId);	
		}
		
	}
	Listener listener ;
	Writer writer ;
	long id ;
	String keyword ;
	ArrayList<Ressource> ressources = new ArrayList<Ressource>() ;
	ArrayList<SearchResult> results = new ArrayList<SearchResult>();
	InetSocketAddress socketAddress ;
	HashMap<Long,NodeState> nodeStates = new HashMap<Long,NodeState>() ;
	ArrayList<JSONObject> nodes = new ArrayList<JSONObject>() ;
	final int defaultPort = 3999 ;
	ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(10) ;
	
	public void setKeyword(String s){
		keyword = s ;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
		while(true){
		try {
			JSONObject o = listener.input.take();
			
			switch((String)o.get("type")){
			
			case "JOINING_NETWORK_SIMPLIFIED" : {
				//add to nodes
				JSONObject newNode = new JSONObject();
				newNode.put("node_id", (String)o.get("node_id"));
				newNode.put("ip_address", (String)o.get("ip_address"));
				nodes.add(newNode);
				
				//send routing info
				sendRoutingInfo(o);
				
				//send network_relay
				JSONObject node = hasCloserNode((String)o.get("node_id"),false);
				if(node != null) {
					sendNetworkRelay(o, node);
				}
				break ; }
				
			case "JOINING_NETWORK_RELAY_SIMPLIFIED" : {
				// send routing info to the gateway
				sendRoutingInfoToGateway(o);
				
				//send network_relay
				JSONObject node = hasCloserNode((String)o.get("node_id"), false);
				if(node != null)
					relayNetworkRelay(o, node);
				break ; }
				
			case "ROUTING_INFO" : {
				
				updateRoutingTable(o);
				if(((String)o.get("gateway_id")).equals(String.valueOf(id))){
					// send to joining node
					relayRoutingInfo(o);
				} else {
					// send to gateway
					JSONObject node = hasCloserNode((String)o.get("gateway_id"));
					if(node != null)
						sendRoutingInfoToGateway(node);
				}
				break ; }
				
			case "LEAVING_NETWORK" :{
				leavingNode(o);
				break ; }
				
			case "INDEX" : {
				
				if(((String)o.get("target_id")).equals(String.valueOf(id))){
					//store the url
					urlToIndex(o);
					
					//acknowledge
					ackIndex(o);
				} else {
					//send to a closer node
					JSONObject node = hasCloserNode((String)o.get("target_id"));
					if( node != null)
						relayIndex(o, node);
				}
					
				break ; }
				
			case "SEARCH" : {
				if(((String)o.get("node_id")).equals(String.valueOf(id))){
					sendResults(o);
					
				} else {
					// send to a closer node
					JSONObject node = hasCloserNode((String)o.get("node_id"));
					if(node!=null)
						relaySearch(o, node);
				}
				break ; }
				
			case "SEARCH_RESPONSE" : {
				if(((String)o.get("node_id")).equals(String.valueOf(id))){
					storeResults(o);
					
				} else {
					// send to a closer node
					JSONObject node = hasCloserNode((String)o.get("node_id"));
					if(node!=null)
						relaySearchResponse(o, node);
				}
				break ; }
				
			case "PING" : {
				//acknowledge the last node
				ack(o);
				
				// continue the process
				if(!((String)o.get("target_id")).equals(String.valueOf(id))){
					JSONObject node = hasCloserNode((String)o.get("target_id"));
					if(node!=null){
						relayPing(o,node);
						
					}
				}
				break ; }
				
			case "ACK" : {
				// check if it's too late
				hasAck((String)o.get("node_id"));
				break ; }
				
			case "ACK_INDEX" : {
				// check if it's too late
				hasAck((String)o.get("node_id"));
				break ; }
			
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		
		}
		
	}
	
	protected void updateRoutingTable (JSONObject o){
		JSONArray newNodes = (JSONArray) JSONValue.parse((String)o.get("route_table"));
		
		for(int i=0; i<newNodes.size(); i++){
			JSONObject tmp = (JSONObject) JSONValue.parse((String)newNodes.get(i));
			boolean isPresent = false ;
			for(int j=0; j<nodes.size(); j++){
				if(((String)nodes.get(i).get("node_id")).equals((String)tmp.get("node_id"))){
					isPresent = true ;
					break ;
				}
			}
			if(!isPresent){
				nodes.add(tmp);
			}
		}
	}
	
	protected void sendRoutingInfo(JSONObject o){
		JSONObject toSend = new JSONObject() ;
		toSend.put("gateway_id", String.valueOf(id));
		toSend.put("type", "ROUTING_INFO");
		toSend.put("node_id", (String)o.get("node_id"));
		toSend.put("ip_address",socketAddress.getAddress().getHostAddress());
		String node_list = JSONValue.toJSONString(nodes);
		toSend.put("routing_info", node_list);
		
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)o.get("ip_address")),defaultPort) ;
			PacketToSend p = new PacketToSend(toSend,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void sendRoutingInfoToGateway(JSONObject o){
		JSONObject toSend = new JSONObject() ;
		toSend.put("gateway_id", (String)o.get("gateway_id"));
		toSend.put("type", "ROUTING_INFO");
		toSend.put("node_id", (String)o.get("node_id"));
		toSend.put("ip_address",socketAddress.getAddress().getHostAddress());
		String node_list = JSONValue.toJSONString(nodes);
		toSend.put("routing_info", node_list);
		
		JSONObject node = hasCloserNode((String)o.get("gateway_id"));
		if(node==null && nodes.size()!=0)
			node = nodes.get(0);
		
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort) ;
			PacketToSend p = new PacketToSend(toSend,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void relayRoutingInfo (JSONObject o) {
		JSONObject toSend = new JSONObject() ;
		toSend.put("gateway_id", String.valueOf(id));
		toSend.put("type", "ROUTING_INFO");
		toSend.put("node_id", (String)o.get("node_id"));
		toSend.put("ip_address",socketAddress.getAddress().getHostAddress());
		toSend.put("route_table", (String)o.get("route_table"));
		
		System.out.println(toSend);
		
		JSONObject node = findNode((String)o.get("node_id"));
		if(node==null)
			return ;
		
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort) ;
			PacketToSend p = new PacketToSend(toSend,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void sendNetworkRelay(JSONObject o, JSONObject node){
		JSONObject toSend = new JSONObject() ;
		toSend.put("type", "JOINING_NETWORK_RELAY_SIMPLIFIED");
		toSend.put("node_id", (String)o.get("node_id"));
		toSend.put("target_id", (String)o.get("target_id"));
		toSend.put("gateway_id", String.valueOf(id));
		
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort) ;
			PacketToSend p = new PacketToSend(toSend,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void relayNetworkRelay(JSONObject o, JSONObject node){
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort) ;
			PacketToSend p = new PacketToSend(o,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	protected JSONObject hasCloserNode(String otherId){
		return hasCloserNode(otherId,true);
	}
	
	protected JSONObject hasCloserNode(String otherId, boolean same){ 
		//remove all dead nodes
		
		int closest = -1 ;
		long dist = Math.abs(id-Integer.parseInt(otherId));
		for(int i=0; i<nodes.size(); i++){
			
			if( (!same && otherId.equals((String)nodes.get(i).get("node_id"))) 
					|| String.valueOf(id).equals((String)nodes.get(i).get("node_id")))
				continue ;
			int tmpId = Integer.parseInt((String)nodes.get(i).get("node_id")) ;
			int tmpDist ;
			if((tmpDist=Math.abs(tmpId-Integer.parseInt(otherId)))<dist){
				dist = tmpDist ;
				closest = i ;
			}
		}
		
		
		if(closest>-1) {
			return nodes.get(closest);
		} else {
			return null ;
		}
	}
	
	protected void leavingNode(JSONObject o){
		String nodeId = (String)o.get("node_id");
		for(int i=0; i<nodes.size(); i++){
			if(nodeId.equals(nodes.get(i).get("node_id"))){
				nodes.remove(i);
				break ;
			}	
		}
	}
	
	protected void urlToIndex(JSONObject o){
		JSONArray urls = (JSONArray)JSONValue.parse((String)o.get("url"));
		
		for(int i=0; i<urls.size(); i++){
			
			boolean isPresent = false ;
			
			for(int j=0; j<ressources.size(); j++){
				
				if(ressources.get(j).getUrl().equals((String)urls.get(i))){
					isPresent = true ;
					ressources.get(j).addOneHit();
					break ;
				}
			}
			
			if(!isPresent){
				ressources.add(new Ressource((String)urls.get(i)));
			}
		}
	}
	
	protected void ackIndex(JSONObject o){
		JSONObject toSend = new JSONObject() ;
		toSend.put("type", "ACK_INDEX");
		toSend.put("node_id", (String)o.get("sender_id")) ;
		toSend.put("keyword", (String)o.get("keyword"));
		
		JSONObject node = hasCloserNode((String)o.get("sender_id"));
		if(node==null){
			node = nodes.get(0);
		}
		
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(toSend, addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
			
			
	}
	
	protected void relayIndex(JSONObject o, JSONObject node){
	
		InetSocketAddress addr;
		try {
			addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(o,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	protected void sendResults(JSONObject o){
		
		JSONObject toSend = new JSONObject();
		toSend.put("type","SEARCH_RESPONSE");
		toSend.put("word", (String)o.get("word"));
		toSend.put("node_id", (String)o.get("sender_id"));
		toSend.put("sender_id",String.valueOf(id));
		JSONArray urls = new JSONArray();
		for(int i=0; i<ressources.size(); i++){
			JSONObject tmp = new JSONObject();
			tmp.put("url", ressources.get(i).getUrl());
			tmp.put("rank", ressources.get(i).getHit());
			urls.add(tmp);
		}
		String response = urls.toJSONString();
	    toSend.put("response", response);
	    
	    JSONObject node = hasCloserNode((String)o.get("sender_id"));
	    if(node==null)
	    	node = nodes.get(0);
	    try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(toSend,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	}
	
	protected void relaySearch(JSONObject o, JSONObject node){
		try {
			InetSocketAddress addr =  new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(o,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void relaySearchResponse(JSONObject o, JSONObject node){
		try {
			InetSocketAddress addr =  new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(o,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void ack(JSONObject o){
		JSONObject toSend = new JSONObject() ;
		toSend.put("type", "ACK");
		toSend.put("node_id", String.valueOf(id));
		toSend.put("ip_address", socketAddress.getAddress().getHostAddress());
	
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)o.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(toSend, addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void relayPing(JSONObject o, JSONObject node){
		
		o.remove("ip_address");
		o.put("ip_address",socketAddress.getAddress().getHostAddress());
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(o, addr);
			nodeStates.put(Long.decode((String)node.get("node_id")), new NodeState("PING"));
			scheduler.schedule(new Checker((String)node.get("node_id")),10, TimeUnit.SECONDS);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void sendPing(String id){
		JSONObject toSend = new JSONObject() ;
		toSend.put("type", "PING");
		toSend.put("target_id", id);
		toSend.put("sender_id", String.valueOf(this.id));
		toSend.put("ip_address", socketAddress.getAddress().getHostAddress());
		JSONObject node = hasCloserNode(id);
		if(node==null)
			node = nodes.get(0);
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(toSend,addr);
			nodeStates.put(Long.decode(id), new NodeState("PING"));
			scheduler.schedule(new Checker(id), 10, TimeUnit.SECONDS);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public long hashCode(String str) {
		  int hash = 0;
		  for (int i = 0; i < str.length(); i++) {
		    hash = hash * 31 + str.charAt(i);
		  }
		  return Math.abs(hash);
		}

	protected void checkDeath(String id){
		Long idL = Long.decode(id);
		NodeState state ;
		if((state = nodeStates.get(idL))!=null){
			switch(state.getType()){
				case "PING" : {
					if((new java.util.Date()).getTime()-state.getTime()>10000){
						//node is dead
						removeNode(id);
						nodeStates.remove(idL);
					}
				}
				case "INDEX" : {
					if((new java.util.Date()).getTime()-state.getTime()>30000){
						nodeStates.remove(idL);
						//send a ping
						sendPing(id);
					}
				}
			}
		}
	}
	
	protected void hasAck(String id){
		Long idL = Long.decode(id);
		nodeStates.remove(idL);
	}
	
	protected void removeNode(String id){
	
		for(int i=0; i<nodes.size(); i++){
			
			if(id.equals((String)nodes.get(i).get("node_id"))){
				nodes.remove(i);
				break ;
			}
		}
	
	}
	
	@Override
	public void init(DatagramSocket udp_socket) {
		// TODO Auto-generated method stub
		listener = new Listener(udp_socket);
		writer = new Writer(udp_socket) ;
		socketAddress = new InetSocketAddress(udp_socket.getLocalAddress(),udp_socket.getLocalPort());
		this.start();
		listener.start();
		writer.start();
	}

	@Override
	public long joinNetwork(InetSocketAddress bootstrap_node,
			String identifier, String target_identifier) {
		
		keyword = identifier ;
		id = hashCode(identifier);
		
		JSONObject toSend = new JSONObject() ;
		toSend.put("type", "JOINING_NETWORK_SIMPLIFIED");
		toSend.put("node_id", String.valueOf(id));
		toSend.put("target_id",String.valueOf(hashCode(target_identifier)));
		toSend.put("ip_address",socketAddress.getAddress().getHostAddress());
		PacketToSend p = new PacketToSend(toSend,bootstrap_node);
		
		try {
			writer.output.put(p);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return hashCode(identifier);
	}

	@Override
	public boolean leaveNetwork(long network_id) {
		JSONObject toSend = new JSONObject();
		toSend.put("type","LEAVING_NETWORK");
		toSend.put("node_id",String.valueOf(id));
		
		for(int i=0; i<nodes.size(); i++){
			try {
				InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)nodes.get(i).get("ip_address")),defaultPort);
				PacketToSend p = new PacketToSend(toSend,addr);
				writer.output.put(p);
			} catch (UnknownHostException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return false;
	}
	
	protected JSONObject findNode(String nodeId){
		for(int i=0; i<nodes.size(); i++){
			if(nodeId.equals((String)nodes.get(i).get("node_id"))){
				return nodes.get(i);
			}
		}
		return null ;
	}

	@Override
	public void indexPage(String url, String[] unique_words) {
		for(int i=0; i<unique_words.length; i++){
			long hash = hashCode(unique_words[i]);
			if(hash==id){
				boolean isPresent = false ;
				for(int j=0; j<ressources.size(); j++){
					if(ressources.get(j).getUrl().equals(url)){
						ressources.get(j).addOneHit();
						isPresent = true ;
						break ;
					}
				}
				if(!isPresent){
					ressources.add(new Ressource(url));
				}
			} else {
				
				JSONObject toSend = new JSONObject() ;
				toSend.put("type", "INDEX");
				toSend.put("target_id", String.valueOf(hash));
				toSend.put("sender_id", String.valueOf(id));
				toSend.put("keyword", unique_words[i]);
				JSONArray link = new JSONArray() ;
				link.add(url);
				toSend.put("link", link.toJSONString());
				
				JSONObject node = hasCloserNode(String.valueOf(hash));
				if(node==null)
					node = nodes.get(0);
				
				try {
					// TODO : set a timer
					InetSocketAddress addr = new InetSocketAddress (InetAddress.getByName((String)node.get("ip_address")),defaultPort);
					PacketToSend p = new PacketToSend(toSend,addr);
					nodeStates.put(hash, new NodeState("INDEX"));
					scheduler.schedule(new Checker(String.valueOf(hash)), 30, TimeUnit.SECONDS);
					writer.output.put(p);
				} catch (UnknownHostException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}
		
	}
	
	protected void storeResults(JSONObject o){
		JSONArray res = (JSONArray)JSONValue.parse((String)o.get("response"));
		long f = 0 ;
		String[] urls = new String[res.size()];
		for(int i=0; i<res.size(); i++){
			JSONObject r = (JSONObject) JSONValue.parse((String)res.get(i));
			f+=Long.decode((String)r.get("rank"));
			urls[i]=(String)r.get("url");
			
		}
		SearchResult sr = new SearchResult((String)o.get("word"),urls,f);
		results.add(sr);
	}
	
	protected void search(String word){
		JSONObject toSend = new JSONObject();
		toSend.put("type", "SEARCH");
		toSend.put("word", word);
		toSend.put("node_id", String.valueOf(hashCode(word)));
		toSend.put("sender_id", String.valueOf(id));
		
		JSONObject node = hasCloserNode(String.valueOf(hashCode(word)));
		if(node==null && nodes.size()>0)
			node=nodes.get(0);
		
		try {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName((String)node.get("ip_address")),defaultPort);
			PacketToSend p = new PacketToSend(toSend,addr);
			writer.output.put(p);
		} catch (UnknownHostException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

	@Override
	public SearchResult[] search(String[] words) {

		results.clear();
		for(int i=0; i<words.length; i++){
			search(words[i]);
		}
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		SearchResult[] res = new SearchResult[results.size()];
		for(int i=0; i<results.size(); i++){
			res[i] = results.get(i);
		}
		results.clear();
		return res ;
	}
	
	public void setId(String id){
		this.id = Long.decode(id);
	}

}
