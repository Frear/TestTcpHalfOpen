import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.protocol.HttpContext;

class TestTcpHalfOpen {

	private static final long STATS_INTERVAL = 2000;
	private static final long TOTAL_LIFETIME = 60000;
	private static final long DELAY_BETWEEN_REQUESTS = 17000;
	private static final long TOTAL_ITERATIONS = 2;
	private static final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	public static stdoutPrinter stdoutPrinter;

	public static void main(String[] args) throws Exception {
		new TestTcpHalfOpen(args);
		return;
	}

	/**
	 * Set up the program:
	 * - Set up HttpClient
	 * - Parse the cli args
	 * - Fire off background threads
	 * - Wait 60 seconds after all threads have finished
	 */
	TestTcpHalfOpen(String[] args) throws Exception {
		this.stdoutPrinter = new stdoutPrinter();
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		String[] urisToGet;

		// See: https://hc.apache.org/httpcomponents-client-4.5.x/tutorial/html/connmgmt.html#d5e393
		// Set max total connections to 100
		cm.setMaxTotal(100);
		// Set max total connections per route to 10
		cm.setDefaultMaxPerRoute(10);

		CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(cm)
			.build();

		// URIs to perform GETs on
		urisToGet = new String[args.length==0?2:args.length];
		if(args.length == 0) {
			urisToGet[0] = "http://www.github.com/thr0";
			urisToGet[1] = "http://www.github.com/thr1";
		} else {
			for( int i=0; i<args.length; i++ ) {
				urisToGet[i] = args[i];
			}
		}

		/* Too verbose.
		 *
		 * System.out.println("Preparing to visit sites:");
		 * for( int i=0; i<urisToGet.length; i++ ) {
		 * 	System.out.println( (i+1) + ": " + urisToGet[i] );
		 * }
		 */

		// Create a httpClientThread thread for each URI
		httpClientThread[] threads = new httpClientThread[urisToGet.length];
		for( int i=0; i<threads.length; i++ ) {
			HttpGet httpget = new HttpGet(urisToGet[i]);
			threads[i] = new httpClientThread(httpClient, httpget);
			threads[i].setName("HttpClient Thread #" + i);
		}

		// Start threads that make http requests
		for( int j=0; j<threads.length; j++ ) {
			threads[j].start();
		}

		// Create and start a background stats printing thread
		printStatsThread printStatsThread = new printStatsThread(STATS_INTERVAL, TOTAL_LIFETIME/STATS_INTERVAL, cm);
		printStatsThread.setName("printStatsThread");
		printStatsThread.start();

		// Wait until all HttpClient threads have exited
		for( int j=0; j<threads.length; j++ ) {
			threads[j].join();
		}

		this.stdoutPrinter.println(sdfDate.format(new Date()) +
			" All httpClientThreads have exited!");
		//System.out.println(cm.getTotalStats().toString());

		// Wait for a while after all http request threads have exited
		Thread.sleep(TOTAL_LIFETIME);
		return;
	}



	/**
	 * Make http requests to each of the endpoints we are configured to call
	 */
	static class httpClientThread extends Thread {

		private final CloseableHttpClient httpClient;
		private final HttpContext context;
		private final HttpGet httpget;

		public httpClientThread(CloseableHttpClient httpClient, HttpGet httpget) {
			this.httpClient = httpClient;
			this.context = HttpClientContext.create();
			this.httpget = httpget;
		}

		@Override
		public void run() {
			CloseableHttpResponse response;
			HttpEntity entity;
			InputStream instream;
			char inputByte;
			String threadName = Thread.currentThread().getName();
			String finalInput;

			try {
				for(int iteration=0; iteration<TOTAL_ITERATIONS; iteration++) {
					stdoutPrinter.println(sdfDate.format(new Date()) + 
						" httpClientThread " +
						threadName +
						" iteration " + 
						iteration + 
						" is about to call [" +
						httpget.getURI() +
						"]");
					response = httpClient.execute(
						httpget, context);
					entity = response.getEntity();
					instream = entity.getContent();
					finalInput = new String(sdfDate.format(new Date()) + 
						" httpClientThread " +
						threadName +
						" Recv'd input: [");
					while(instream.available() != 0) {
						inputByte=(char) instream.read();
						if( inputByte >= 32 && inputByte <= 126 )
							finalInput = new String(finalInput + inputByte);
							//stdoutPrinter.print(inputByte);
					}
					finalInput = new String(finalInput + "]");
					//stdoutPrinter.println("]");
					stdoutPrinter.println(finalInput);
					/*******************************************/
					/*    InputStream.close() is MANDATORY!    */
					/*******************************************/
					instream.close();
					try {
						Thread.sleep(DELAY_BETWEEN_REQUESTS);
					} catch (InterruptedException ex) {
						stdoutPrinter.println(sdfDate.format(new Date()) +
							" httpClientThread " +
							threadName +
							" had interrupted sleep " +
							ex);
						ex.printStackTrace();
					}
				}
			} catch (ClientProtocolException ex) {
				// Handle protocol errors
				stdoutPrinter.println(sdfDate.format(new Date()) +
					" httpClientThread " +
					threadName +
					" caught Protocol exception " +
					ex);
				ex.printStackTrace();
			} catch (IOException ex) {
				// Handle I/O errors
				stdoutPrinter.println(sdfDate.format(new Date()) +
					" httpClientThread " +
					threadName +
					" caught I/O exception " +
					ex);
				ex.printStackTrace();
			} finally {
				stdoutPrinter.println(sdfDate.format(new Date()) +
					" httpClientThread " +
					threadName +
					" is no more.");
			}
		}

	}



	/**
	 * Start a background thread whos only purpose is to print thread pool stats
	 * every so often.
	 */
	static class printStatsThread extends Thread {
		private final long interval;
		private final long numIterations;
		private final PoolingHttpClientConnectionManager cm;

		public printStatsThread(long interval, long numIterations, PoolingHttpClientConnectionManager cm) {
			this.interval = interval;
			this.numIterations = numIterations;
			this.cm = cm;
		}

		@Override
		public void run() {
			for(long iteration=0; iteration<numIterations; iteration++) {
				stdoutPrinter.println(sdfDate.format(new Date()) +
					" HttpClient threadpool stats: " + 
					cm.getTotalStats().toString());
				try {
					Thread.sleep(interval);
				} catch(InterruptedException ex) {
					stdoutPrinter.println(sdfDate.format(new Date()) +
						" stats thread sleep interrupted " +
						ex);
					ex.printStackTrace();
				}
			}
		}
	}


	/**
	 * Threads were stepping on themselves as they printed their output.
	 * Forcing a lock to be held to access System.out addresses that.
	 */
	class stdoutPrinter {
		private Boolean lock = new Boolean(false);

		public stdoutPrinter() {
		}

		public void print(String message) {
			synchronized(lock) {
				System.out.print(message);
			}
		}

		public void println(String message) {
			synchronized(lock) {
				System.out.println(message);
			}
		}
	}

}
