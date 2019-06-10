package HQBot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.ClientEndpointConfig.Builder;
import javax.websocket.ClientEndpointConfig.Configurator;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClients;
import org.glassfish.tyrus.client.ClientManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
//import discord4j.core.spec.MessageCreateSpec;

public class BotMain {
	private static final String BOT_TOKEN = "";
	private static final String BEARER = "";
	
	private final static ArrayList<TextChannel> channels = new ArrayList<TextChannel>();
	private final static HashMap<Long, Question> questions = new HashMap<Long, Question>();
	private final static HashMap<Long, Question> questionVerify = new HashMap<Long, Question>();
	private final static HashSet<Long> summaryIDs = new HashSet<Long>();
	private final static HashMap<Long, Puzzle> puzzles = new HashMap<Long, Puzzle>();
	private final static HashMap<Long, Puzzle> puzzleVerify = new HashMap<Long, Puzzle>();
	private final static HashSet<Long> roundIDs = new HashSet<Long>();
	private static int ended = 0;
	private static boolean printed = false;

	public static void main(String[] args) {
		//TODO listener to restart client
		final DiscordClient client = new DiscordClientBuilder(BOT_TOKEN).build();
		
		client.getEventDispatcher().on(ReadyEvent.class)
			.subscribe(ready -> System.out.println("Logged in as " + ready.getSelf().getUsername()));
		
		channels.add((TextChannel) client.getChannelById(Snowflake.of("")).block());
		
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
        	.filter(msg -> msg.getContent().map("!ping"::equals).orElse(false))
        	.flatMap(Message::getChannel)
        	.flatMap(channel -> channel.createMessage("Yeah, yeah. I'm here."))
        	.subscribe();
		
		hqListen();
		
		client.login().block();
	}

	private static void hqListen() {
		
		for(TextChannel channel : channels) {
			channel.createMessage("Starting HQ client").block();
		}
		
		System.out.println();
		
		//just a loop to keep the tool running once it starts
		while(true) {
			//clears out question IDs for new puzzles
			questions.clear();
			summaryIDs.clear();
			puzzles.clear();
			roundIDs.clear();
			questionVerify.clear();
			puzzleVerify.clear();
			
			//Create an Http client and send a request to HQ servers using our bearer token and the hq client
			HttpClient client = HttpClients.custom().build();
			HttpUriRequest request = RequestBuilder.get()
					.setUri("https://api-quiz.hype.space/shows/now?type=")
					.setHeader(HttpHeaders.CONTENT_TYPE, "Authorization: " + BEARER + "\nx-hq-client: iOS/1.4.14b145")
					.build();
	
			HttpResponse response;
			
			//execute the request and try to parse it
			try {
				response = client.execute(request);
				HttpEntity entity = response.getEntity();
				
				if(entity != null) {
					
					//print the output to console to help with debugging
					InputStream in = entity.getContent();
					JSONObject jo = (JSONObject) new JSONParser().parse(new InputStreamReader(in));
					for(Object o : jo.keySet()) {
						System.out.println(o + " : " + jo.get(o));
					}
					
					String socketURL = null;
					JSONObject broadcast = (JSONObject) jo.get("broadcast");
					
					//if no broadcast is happening, wait 5 seconds and start the loop over
					if(broadcast == null) {
						System.out.println("No Broadcast currently, trying again in 5 seconds");
						//MessageCreateSpec mess = new MessageCreateSpec().setContent("No broadcast");
						
						CountDownLatch latch = new CountDownLatch(1);
						latch.await(5, TimeUnit.SECONDS);
						continue;					
					}
					
					
					//grab the socketUrl, replace https with wss
					socketURL = (String) broadcast.get("socketUrl");
					socketURL = socketURL.substring(5);
					socketURL = "wss" + socketURL;
					//once we have the socketurl we can connect to it
					while(ended < 10) {
						
						//create the client endpoint builder and make sure it tacks on the appropriate
						//authorization and client headers before requesting 
						final Builder configBuilder = ClientEndpointConfig.Builder.create();
						configBuilder.configurator(new Configurator() {
							@Override
							public void beforeRequest(final Map<String, List<String>> headers) {
								headers.put("Authorization", Arrays.asList(BEARER));
								headers.put("x-hq-client", Arrays.asList("iOS/1.4.14b145"));							
							}
						});						
						
						//now build the client endpoint configuration
						ClientEndpointConfig cec = configBuilder.build();
						
						//create a client manager and have it connect to the server
						ClientManager clientManager = ClientManager.createClient();
						clientManager.connectToServer(new Endpoint() {
							
							@Override
							public void onOpen(Session session, EndpointConfig config) {
								
								//add a message handler to the session
								try {								
									session.addMessageHandler(new MessageHandler.Whole<String>() {
										
										@Override
										public synchronized void onMessage(String message) {
									
											//if it's a new question, print the question and answers to console
											try {
												JSONObject data = (JSONObject) new JSONParser().parse(message);
												Long prize = (Long) jo.get("prize");
												
												if(data.get("type").equals("question")){
													if(!questionVerify.containsKey((Long) data.get("questionId"))) {
														Question q = new Question(data);
														questionVerify.put(q.getID(), q);
													}
													else if(!questions.containsKey((Long) data.get("questionId"))) {
														
														Question q = new Question(data);
														questions.put(q.getID(), q);
														
														if(questionVerify.containsKey(q.getID())
																&& questionVerify.get(q.getID()).getQuestion()
																.equals(q.getQuestion())){
															
															System.out.println();
															String tempMessage = q.getQuestion();
															
															for(int i=0; i<3; i++) {
																tempMessage += "\n" + q.getAnswer(i);
															}
															if(!printed) {
															printed = true;
																System.out.println(tempMessage);
																for(TextChannel channel : channels) {
																	String s = tempMessage;
																	channel.createMessage(spec -> spec.setEmbed(embed -> 
																	embed.setDescription(s))).block();
																}
															}
													
														}
													}
												}
												
												//if the question is over, print the answer and number
												//that picked beside each answer.
												else if(data.get("type").equals("questionSummary")) {
													if(!summaryIDs.contains((Long) data.get("questionId")) 
															&& questions.containsKey((Long) data.get("questionId"))) {
														Question q = questions.get((Long) data.get("questionId"));
														q.questionSummary((JSONArray) data.get("answerCounts"));
														summaryIDs.add(q.getID());
														System.out.println();
														
														String tempMessage = q.getQuestion();
														
														for(int i=0; i<3; i++) {
															tempMessage += "\n" + q.getAnswer(i) + " | " + q.getCount(i)
															+ " picked | " + (i == q.getCorrectIndex() 
															? "CORRECT!" : "wrong...");
														}
														tempMessage += "\nEstimated payout: ";
														tempMessage += String.format("%.2f", new Double(prize)
																/ new Double(q.getCount(q.getCorrectIndex())));
														
														if(printed) {
															printed = false;
															System.out.println(tempMessage);
															for(TextChannel channel : channels) {
																String s = tempMessage;
																channel.createMessage(spec -> spec.setEmbed(embed -> 
																embed.setDescription(s))).block();
															}
														}
														
													}													
												}
												
												else if(data.get("type").equals("startRound")) {
													if(!puzzleVerify.containsKey((Long) data.get("roundId"))) {
														Puzzle p = new Puzzle(data);
														puzzleVerify.put(p.getID(), p);
													}
													else if(!puzzles.containsKey((Long) data.get("roundId"))) {
														Puzzle p = new Puzzle(data);
														puzzles.put(p.getID(), p);
														
														System.out.println();
														
														String tempMessage = p.getHint();
														tempMessage += "\n" + p.getAnswer();
														
														if(!printed) {
															printed = true;															
															System.out.println(tempMessage);
															for(TextChannel channel : channels) {
																String s = tempMessage;
																channel.createMessage(spec -> spec.setEmbed(embed -> 
																embed.setDescription(s))).block();
															}
														}
													}
												}
												
												else if(data.get("type").equals("endRound")) {
													if(!summaryIDs.contains((Long) data.get("roundId")) 
															&& puzzles.containsKey((Long) data.get("roundId"))) {
														Puzzle p = puzzles.get((Long) data.get("roundId"));
														p.updatePuzzle(data);
														summaryIDs.add(p.getID());
														
														System.out.println();
														String tempMessage = p.getHint()
																+ "\nAnswer: " + p.getAnswer() + "\n"
																+ p.getSolved() + " are still in the game | "
																+ p.getUnsolved() + " just lost";
														
														tempMessage += "\n Estimated payout: ";
														tempMessage += String.format("%.2f", new Double(prize)/ new Double(p.getSolved()));
														
														if(printed) {
															printed = false;
															System.out.println(tempMessage);
															for(TextChannel channel : channels) {
																String s = tempMessage;
																channel.createMessage(spec -> spec.setEmbed(embed -> 
																embed.setDescription(s))).block();
															}
														}

													}
												}
												
												else if(data.get("type").equals("broadcastEnded")) {
													if(data.get("reason") == null && data.size() == 4) ended++;
													else ended = 0;
												}
											} catch (Exception e) {
										
											}
										}
									});
								} catch(Exception e) {
									e.printStackTrace();
								}								
							}
							
						}, cec, new URI(socketURL));
					}
					ended = 0;
				}
			} catch(Exception e) {
				e.printStackTrace();
			} 
		}
	}	
}