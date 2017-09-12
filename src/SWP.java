/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

public class SWP {

	/*
	 * ========================================================================*
	 * the following are provided, do not change them!!
	 * ========================================================================
	 */
	// the following are protocol constants.
	public static final int MAX_SEQ = 7; // should be 2^n-1 - Note that sender's window size starts from 0 and grows to MAX_SEQ. Receiver's window is always fixed in size and equal to MAX_SEQ
	public static final int NR_BUFS = (MAX_SEQ + 1) / 2;

	// the following are protocol variables
	private int oldest_frame = 0; // initial value is only for the simulator
	private PEvent event = new PEvent();
	private Packet out_buf[] = new Packet[NR_BUFS]; //Buffer for outgoing transmission
	
	/*** Added ***/
	//Added in_buf[] which serves as an input buffer for inbound transmission
	private Packet in_buf [] = new Packet [NR_BUFS];

	// the following are used for simulation purpose only
	private SWE swe = null;
	private String sid = null;

	// Constructor
	public SWP(SWE sw, String s) {
		swe = sw;
		sid = s;
	}

	// the following methods are all protocol related
	private void init() {
		for (int i = 0; i < NR_BUFS; i++) {
			out_buf[i] = new Packet();
			//Added
			in_buf[i] = new Packet();
		}
	}

	private void wait_for_event(PEvent e) {
		swe.wait_for_event(e); // may be blocked
		oldest_frame = e.seq; // set timeout frame seq
	}

	private void enable_network_layer(int nr_of_bufs) {
		// network layer is permitted to send if credit is available
		swe.grant_credit(nr_of_bufs);
	}

	private void from_network_layer(Packet p) {
		swe.from_network_layer(p);
	}

	private void to_network_layer(Packet packet) {
		swe.to_network_layer(packet);
	}

	private void to_physical_layer(PFrame fm) {
		System.out.println("SWP: Sending frame: seq = " + fm.seq + " ack = " + fm.ack + " kind = "
				+ PFrame.KIND[fm.kind] + " info = " + fm.info.data);
		System.out.flush();
		swe.to_physical_layer(fm);
	}

	private void from_physical_layer(PFrame fm) {
		PFrame fm1 = swe.from_physical_layer();
		fm.kind = fm1.kind;
		fm.seq = fm1.seq;
		fm.ack = fm1.ack;
		fm.info = fm1.info;
	}

	/*
	 * =========================================================================
	 * ==* implement your Protocol Variables and Methods below:
	 * =========================================================================
	 * =
	 */

	/*** Variables added - Set variables to private***/
	private boolean no_nak = true; /* no nak has been sent yet */

	/** Methods added ***/
	static boolean between(int a, int b, int c) {
		/* Same as between in protocol5, but shorter and more obscure*/
		////Checks the circular condition of the frame numbers
		//Checks if frame number falls within the window
		return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a));
	}
	
	private void send_frame(int frame_kind, int frame_number, int frame_expected, Packet buffer[])
	{
		/*Construct and send a data, ack or nak frame */
		
		PFrame s = new PFrame(); //Create PFrame object
		s.kind = frame_kind; 
	
		if (frame_kind == PFrame.DATA)
			s.info = buffer[frame_number%NR_BUFS];
		
		s.seq = frame_number; //Set frame number as the sequence number
		s.ack = (frame_expected+MAX_SEQ)%(MAX_SEQ+1);  //Not sure - is this setting acknowledgement number? E.g. Frame 0, ACK 1?
		
		if (frame_kind == PFrame.NAK)
			no_nak = false;
		
		to_physical_layer(s); //Transmit the frame to the physical layer
		
		if (frame_kind == PFrame.DATA)
			start_timer(frame_number%NR_BUFS); //Not sure - Start timer when transmitting to receiver window?
		
		stop_ack_timer(); //no need for separate acknowledgement frame - Not sure is it because no data is going to be transmitted?
		
	}

	public void protocol6() {
		
		/**Declaring variables and array for Protocol 6**/
		int ack_expected;          //Frame number of expected acknowledgement - Lower edge of sender's window. -
		int next_frame_to_send;    //Next outgoing frame to be sent to the receiver - Upper edge of sender's window + 1. -  
		int  frame_expected;       //Frame expected on the receiver's window - Lower edge of receiver's window - 
		int too_far;               //Defines the position where the receiver can receive no more frames. Perhaps over the limit? - Upper edge of receiver's window + 1
		int i;                     //Defines the loop index into buffer pool
		PFrame r = new PFrame();                  //Scratch variable
		 		
		init();

		boolean arrived [] = new boolean [NR_BUFS]; //Inbound bit map; Used to keep tracks of frames arrived
		
		enable_network_layer(NR_BUFS); //Initialize the network layer
		
		/**Initializing variables for Protocol 6**/
		ack_expected = 0;          //Next acknowledgement expected on the inbound stream
		next_frame_to_send = 0;    //Number of next outgoing frame
		frame_expected = 0; 
		too_far = NR_BUFS;
		
		for(i=0; i<NR_BUFS; i++){
			//Set arrived array to false to show that no packets has arrived yet
			arrived[i] = false; 
		}
		
		while (true) {
			wait_for_event(event); //Five different possibilities
			
			switch (event.type) {
			
			case (PEvent.NETWORK_LAYER_READY): //accept, save and transmit a new frame
				
				from_network_layer(out_buf[next_frame_to_send%NR_BUFS]); //Fetch a new packet
			    send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf); //Transmit the frame
			    inc (next_frame_to_send); //advance upper window edge
			    
				break;
				
			case (PEvent.FRAME_ARRIVAL): //Arrival of data or control frame
				from_physical_layer(r); //Fetch incoming frame from the physical layer
			
			if(r.kind == PFrame.DATA){
				//Arrival of an undamaged frame
				
				//Checks that the frame that arrived is not the expected one and that it doesn't have negative acknowledgement
				//Not sure what this does - if the frame is not expected and tha tthere are no negative acknowledgements is 
				//why does it send the NAK and frame_expected to the outgoing buffer???
				if ((r.seq != frame_expected) && no_nak)
					send_frame (PFrame.NAK, 0, frame_expected, out_buf);
				
				else
					start_ack_timer();
				
				
				//Should be the success case
				//Checks that the frame received is within the expected frames at the receiver's window 
				//Checks that the frame has not been received previously, else the frame would be discarded (not sure?)			
	            if (between(frame_expected, r.seq, too_far) && arrived[r.seq % NR_BUFS] == false) {
	            	
	            	//The frames may be accepted in any particular oder
	            	arrived[r.seq % NR_BUFS] = true; //mark buffer as full
	            	in_buf[r.seq % NR_BUFS] = r.info; //insert data into the buffer
	            	
	            	while (arrived[frame_expected % NR_BUFS]){
	            		//Pass frames and advance window
	            		to_network_layer(in_buf[frame_expected % NR_BUFS]);
	            		no_nak = true;
	            		arrived[frame_expected % NR_BUFS] = false;
	            		inc(frame_expected); //Advance lower edge of receiver's window
	            		inc(too_far); //Advance upper edge of receiver's window
	            		start_ack_timer(); //To see if a separate acknowledgement is needed - Not sure
	            	}
	            	
	            }
				
			}
			
			//
			if((r.kind==PFrame.NAK) && between(ack_expected,(r.ack+1)%(MAX_SEQ+1),next_frame_to_send))
				send_frame(PFrame.DATA, (r.ack+1) % (MAX_SEQ + 1), frame_expected, out_buf);
			
			//
			while (between(ack_expected, r.ack, next_frame_to_send)) {
				stop_timer(ack_expected % NR_BUFS); /* frame arrived intact */
				inc(ack_expected); /* advance the lower edge of sender’s window */
				
			}
				break;
				
			case (PEvent.CKSUM_ERR):
				//If there's a checksum error and if there's no negative acknowledgement that is sent for the damaged or lost frame
				if (no_nak)
					//Send negative acknowledgement
					send_frame(PFrame.NAK, 0, frame_expected, out_buf); /* damaged frame */
			
				break;
				
			case (PEvent.TIMEOUT):
				//If and when the timer times out, the frame will be retransmitted to the receiver 
				send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf); /* we timed out */
				break;
				
			case (PEvent.ACK_TIMEOUT):
				//When the acknowledgement timer times out, a separate acknowledgement frame will be sent
				//Rather than waiting for another frame to piggyback on it will be faster
				send_frame(PFrame.ACK,0,frame_expected, out_buf); /* ack timer expired; send ack */
				break;
				
				
			default:
				System.out.println("SWP: undefined event type = " + event.type);
				System.out.flush();
			}
		}
	}
	


	private int inc(int frame_number) {
		// TODO Auto-generated method stub
		//Increments (cynically) the frame number passed to it and returns it
		frame_number = ((frame_number+1)%(MAX_SEQ+1));
		
		return frame_number;
	}

	/*
	 * Note: when start_timer() and stop_timer() are called, the "seq" parameter
	 * must be the sequence number, rather than the index of the timer array, of
	 * the frame associated with this timer,
	 */

	private void start_timer(int seq) {

	}

	private void stop_timer(int seq) {

	}

	private void start_ack_timer() {

	}

	private void stop_ack_timer() {

	}
	
	

}// End of class

/*
 * Note: In class SWE, the following two public methods are available: .
 * generate_acktimeout_event() and . generate_timeout_event(seqnr).
 * 
 * To call these two methods (for implementing timers), the "swe" object should
 * be referred as follows: swe.generate_acktimeout_event(), or
 * swe.generate_timeout_event(seqnr).
 */
