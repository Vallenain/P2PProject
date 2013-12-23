import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;


public class Launcher {
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		LonelyNode node = new LonelyNode();
		try {
			node.init(new DatagramSocket(8767));
			
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(args[0].equals("--bootstrap") && args.length==4){
			
		InetSocketAddress addr = new InetSocketAddress(args[1],8767);
		node.joinNetwork(addr, "holiday", "vacances") ; //we assume that the hast of the last parameter is equal to args[3]
														//otherwise we don't know what to do
		} else {
			// first node
			node.setId(args[1]);
		}
		
	}
	
}
