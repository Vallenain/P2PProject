import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Listener extends Thread {

	LinkedBlockingQueue<JSONObject> input ;
	DatagramSocket socket ;
	byte[] buffer = new byte[1024] ;
	
	public Listener(DatagramSocket udp_socket) {
		input = new LinkedBlockingQueue<JSONObject>();
		socket = udp_socket ;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
		
		while(true){
			DatagramPacket p = new DatagramPacket(buffer,buffer.length);
			try {
				socket.receive(p);
				String message = new String(buffer);
				message = message.trim();
				JSONObject o = (JSONObject)JSONValue.parse(message);
				if(o!=null)
					input.put(o);
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		
		}
	}

}
