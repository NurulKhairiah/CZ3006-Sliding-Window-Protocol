import java.util.Timer;
import java.util.TimerTask;

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
	public static final int MAX_SEQ = 7; 
	//Note that sender's window size starts from 0 and grows to MAX_SEQ. 
	//Receiver's window is always fixed in size and equal to MAX_SEQ
	
	public static final int NR_BUFS = (MAX_SEQ + 1) / 2;

	// the following are protocol variables
	private int oldest_frame = 0; //Initial value is only for the simulator
	private PEvent event = new PEvent();
	private Packet out_buf[] = new Packet[NR_BUFS]; //Buffer for outgoing transmission

	// the following are used for simulation purpose only
	private SWE swe = null;
	private String sid = null;

	// Constructor
	public SWP(SWE sw, String s) {
		swe = sw;
		sid = s;
	}

	//the following methods are all protocol related
	private void init() {
		for (int i = 0; i < NR_BUFS; i++) {
			out_buf[i] = new Packet();
		}
	}

	private void wait_for_event(PEvent e) {
		swe.wait_for_event(e); //may be blocked
		oldest_frame = e.seq; //set timeout frame seq
	}

	private void enable_network_layer(int nr_of_bufs) {
		//network layer is permitted to send if credit is available
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
	 * implement your Protocol Variables and Methods below:
	 * =========================================================================
	 */

	//Declare variable to show that no negative acknowledgmeents has been sent yet
	private boolean no_nak = true; 
									
	//Checks whether the given frame number falls within the acceptable window range
	static boolean between(int a, int b, int c) {
		return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a));
	}

	//Construct a data/ack/nak frame
	private void send_frame(int frame_kind, int frame_number, int frame_expected, Packet buffer[]) {

		
		PFrame s = new PFrame(); //scratch variable s
		s.kind = frame_kind; // three types of frames - DATA, ACK, NAK
		if (frame_kind == PFrame.DATA) {
			s.info = buffer[frame_number % NR_BUFS];
		}
		
		s.seq = frame_number; //only meaningful for data frames
		s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
		
		if (frame_kind == PFrame.NAK) {
			no_nak = false; //one NAK per frame
		}
		
		to_physical_layer(s); //transmit frame to the physical layer
		
		if (frame_kind == PFrame.DATA) {
			start_timer(frame_number);
		}
		stop_ack_timer(); //no need for separate ACK frame

	}

	public void protocol6() {

		/** Declaring variables for Protocol 6**/
		int ack_expected; //Lower edge of sender's window - Signifies frame number of expected acknowledgement. 
		int next_frame_to_send; //Upper edge of sender's window + 1 - Signifies frame number of next frame to be sent.
		int frame_expected; //Lower edge of receiver's window - Signifies the expected frame number at the receiver's end
		int too_far; //Upper edge of  receiver's window + 1 - Signifies the position where the receiver can receive no more frames 
		int i; // Declare loop index into buffer pool 
		
		PFrame r = new PFrame(); //Declare scratch variable for incoming frame
		Packet in_buf[] = new Packet[NR_BUFS]; //Added buffer for inbound transmission

		//Inbound bit map - Keep track of frames arrived		boolean arrived[] = new boolean[NR_BUFS]; 
		
		enable_network_layer(NR_BUFS); //Initializing network layer to allow the layer to send NR_BUFS frames and cause a network_layer_ready event

		/** Initializing local variables for Protocol 6 **/
		ack_expected = 0; //Next acknowledgement expected on the inbound stream
		next_frame_to_send = 0; //Number of next outgoing frame
		frame_expected = 0;
		too_far = NR_BUFS;

		for (i = 0; i < NR_BUFS; i++) {
			arrived[i] = false; //initialize arrived[] to show that nothing has arrived yet.
			in_buf[i] = new Packet(); //initialize inbound buffer
		}

		init(); // for outgoing buffer, given

		while (true) { 
			//Starts listening to network layer and physical layer
			wait_for_event(event); //five different possibilities of events
			switch (event.type) {

			case (PEvent.NETWORK_LAYER_READY): 
				from_network_layer(out_buf[next_frame_to_send % NR_BUFS]); //retrieve packet from network layer
			
				send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf); //send the frame							
				next_frame_to_send = inc(next_frame_to_send); //advance upper edge of window
															
				break;
			case (PEvent.FRAME_ARRIVAL): //arrival of frame at receiver's end
				from_physical_layer(r); //fetch frame from the physical layer

				if (r.kind == PFrame.DATA) { //arrival of an undamaged frame
	
					if ((r.seq != frame_expected) && no_nak)
						//if it's not the expected frame, send a negative acknowledgement
						send_frame(PFrame.NAK, 0, frame_expected, out_buf);
					
					else
						start_ack_timer(); //start timer for ack to wait for a frame to piggyback on


					//Checks that the undamaged frame received falls within the expected frames of the sliding window
					//and it has not been received previously.
					if (between(frame_expected, r.seq, too_far) && arrived[r.seq % NR_BUFS] == false) {
						
						//Frames can be accepted in any order of arrival at the receiver's window.
						arrived[r.seq % NR_BUFS] = true; //mark buffer as full
						in_buf[r.seq % NR_BUFS] = r.info; //insert data into buffer.
						
						while (arrived[frame_expected % NR_BUFS]) {

							to_network_layer(in_buf[frame_expected % NR_BUFS]); //pass frames to advance window
							no_nak = true; //allows swp to receive NAK
							
							arrived[frame_expected % NR_BUFS] = false; 
							
							frame_expected = inc(frame_expected); //Advance lower edge of receiver's window

							too_far = inc(too_far); //Advance upper edge of receiver's window.
							
							start_ack_timer(); //Start acknowledgement timer and check for separate acknowledgement
						}

					}

				}

				//Selective Repeat retransmission strategy
				if ((r.kind == PFrame.NAK) && between(ack_expected, (r.ack + 1) % (MAX_SEQ + 1), next_frame_to_send))
					send_frame(PFrame.DATA, (r.ack + 1) % (MAX_SEQ + 1), frame_expected, out_buf);  // Resend the frame

				while (between(ack_expected, r.ack, next_frame_to_send)) {
					stop_timer(ack_expected % NR_BUFS); //frame arrived intact - stop timer if undamaged frame is received

					ack_expected = inc(ack_expected); //advance the lower edge of sender’s window

					enable_network_layer(1); //added to get credit, free buffer slot
				}
				
				break;

			case (PEvent.CKSUM_ERR):  
	
				//Arrived frame has checksum error
				if (no_nak) //if no negative acknowledgement has yet been sent for the damaged frame
					
					//send a negative acknowledgement
					send_frame(PFrame.NAK, 0, frame_expected,
							out_buf); /* damaged frame */

				break;

			case (PEvent.TIMEOUT):
				
				//When timer for an associated frame times out, retransmit the frame
				send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf); /* we timed out */
				break;

			case (PEvent.ACK_TIMEOUT):

				//When acknowledgement timer times out, send a separate acknowledgement frame. 
				//Don't need to wait for another frame to piggyback on.
				send_frame(PFrame.ACK, 0, frame_expected, out_buf); /* ack timer expired; send ack */
				break;

			default:
				//For undefined event types
				System.out.println("SWP: undefined event type = " + event.type);
				System.out.flush();
			}
		}
	}

	// Circular increment for the frame number passed to it and returns it
	private int inc(int frame_number) {
		// TODO Auto-generated method stub
		frame_number = ((frame_number + 1) % (MAX_SEQ + 1));
		return frame_number;
	}

	/*
	 * Note: when start_timer() and stop_timer() are called, the "seq" parameter
	 * must be the sequence number, rather than the index of the timer array, of
	 * the frame associated with this timer,
	 */

	/*
	 * Note: In class SWE, the following two public methods are available: .
	 * generate_acktimeout_event() and . generate_timeout_event(seqnr).
	 * 
	 * To call these two methods (for implementing timers), the "swe" object should
	 * be referred as follows: swe.generate_acktimeout_event(), or
	 * swe.generate_timeout_event(seqnr).
	 */
	
	/***Implementation of timers***/
	
	//Variables & constants for implementing frame and acknowledgement timer
	private Timer[] frame_timer = new Timer[NR_BUFS];
	private Timer acknowledgement_timer;

	//Specify timeout periods
	private static int TIMEOUT_PERIOD = 400;
	private static int ACKNOWLEDGEMENT_TIMEOUT_PERIOD = 200;

	// Start the frame timer for an associated frame
	private void start_timer(int seq) {

		try {
			// Stops timer
			stop_timer(seq); 
			
			// Create a new timer for sending of frames
			frame_timer[seq % NR_BUFS] = new Timer(); 

			// Schedule task to be executed after the indicated timeout period
			frame_timer[seq % NR_BUFS].schedule(new FrameTimerTask(swe, seq), TIMEOUT_PERIOD);
		}

		catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private void stop_timer(int seq) {

		try {
			if (frame_timer[seq % NR_BUFS] != null) {
				//Terminates the frame timer for a given frame
				frame_timer[seq % NR_BUFS].cancel();
			}
		}

		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void start_ack_timer() {
		try {
			//Stopping the acknowledgement timer
			stop_ack_timer();
			
			//Create new acknowledgement timer
			acknowledgement_timer = new Timer(); 

			//Schedule tasks to be executed after the indicated timeout period
			acknowledgement_timer.schedule(new AckTimerTask(swe), ACKNOWLEDGEMENT_TIMEOUT_PERIOD);
		}

		catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private void stop_ack_timer() {

		try {
			if (acknowledgement_timer != null)
				//Terminates the acknowledgement timer
				acknowledgement_timer.cancel(); 
		}

		catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	/* When an acknowledgement fails to be received, stop the current timer and generate a timeout event using SWE's
	 * generate_timeout_event(seqnr)
	 */
	
	public class FrameTimerTask extends TimerTask {
		private SWE swe = null;
		public int seqnr; // added attribute seq to record the specific sequence number

		public FrameTimerTask(SWE sw, int seq) {
			swe = sw;
			seqnr = seq;
		}

		// When timer times out, run()
		public void run() {
			try {
				stop_timer(seqnr);

				// Generate timeout event for the given sequence number when the task is executed
				swe.generate_timeout_event(seqnr); 
			}

			catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}

	/*
	 * Timer specifically for acknowledgements
	 * Also decides if a separate acknowledgement frame should be sent if there is no reverse traffic for piggybacking
	 * of ack frames
	 */
	public class AckTimerTask extends TimerTask {
		private SWE swe = null;

		public AckTimerTask(SWE sw) {
			swe = sw;
		}

		public void run() {
			try {
				stop_ack_timer();
				//Generate acknowledgement timeout event
				swe.generate_acktimeout_event();
			}

			catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}

}// End of class