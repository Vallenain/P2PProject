import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.simple.JSONObject;


public class Writer extends Thread{

	LinkedBlockingQueue<PacketToSend> output ;
	DatagramSocket socket ;
	
	public Writer(DatagramSocket udp_socket) {
		output = new LinkedBlockingQueue<PacketToSend>() ;
		socket = udp_socket ;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
		
		while(true){
			try {
				PacketToSend toSend = output.take() ;
				String message = toSend.getMessage().toJSONString() ;
				//System.out.println(message);
				DatagramPacket data = new DatagramPacket(message.getBytes(),message.length(),toSend.getSocketAddr().getAddress(),toSend.getSocketAddr().getPort());
				socket.send(data);
			} catch (InterruptedException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		}
	}
}
