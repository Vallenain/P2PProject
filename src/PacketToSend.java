import java.net.InetSocketAddress;

import org.json.simple.JSONObject;


public class PacketToSend {
	JSONObject message ;
	InetSocketAddress socketAddr ;
	
	public JSONObject getMessage() {
		return message;
	}

	public InetSocketAddress getSocketAddr() {
		return socketAddr;
	}

	public PacketToSend(JSONObject o, InetSocketAddress a){
		message = o ;
		socketAddr = a ;
	}
	
}
