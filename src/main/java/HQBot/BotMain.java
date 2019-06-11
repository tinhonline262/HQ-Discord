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
import org.reactivestreams.Publisher;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.util.Snowflake;

public class BotMain {
	private static final String BOT_TOKEN = "XXX";
	private static final String BEARER = "XXX";
	
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
		//TODO make it so only one instance of hqListen can run at a time.
		final DiscordClient client = new DiscordClientBuilder(BOT_TOKEN).build();		
		
		client.getEventDispatcher().on(ReadyEvent.class)
			.subscribe(ready -> System.out.println("Logged in as " + ready.getSelf().getUsername()));
		
		channels.add((TextChannel) client.getChannelById(Snowflake.of("XXX")).block());
		
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
        	.filter(msg -> msg.getContent().map(".check"::equals).orElse(false))
        	.flatMap(Message::getChannel)
        	.flatMap(channel -> channel.createMessage("Yeah, yeah. I'm here."))
        	.subscribe();
		
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
			.filter(msg->msg.getContent().map(".start"::equals).orElse(false))
			.flatMap(msg -> hqListen())
			.subscribe();
		
		client.login().block();
		
	}

	private static Publisher<?> hqListen() {
		
		for(TextChannel channel : channels) {
			channel.createMessage("You got it, boss").block();
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
					
					for(TextChannel channel : channels) {
						channel.createMessage(spec -> spec.setEmbed(embed ->
							embed.addField("Get Ready!", "HQ " + jo.get("gameType") 
							+ " broadcast is live", false))).block();
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
										
										private String delimiter = "";

										@Override
										public void onMessage(String message) {
									
											//if it's a new question, print the question and answers to console
											try {
												JSONObject data = (JSONObject) new JSONParser().parse(message);
												Long prize = (Long) jo.get("prize");
												
												if(data.get("type").equals("question")){
													if(!questions.containsKey((Long) data.get("questionId"))) {
														
														Question q = new Question(data);
															questions.put(q.getID(), q);
															
															String tempText = q.getQuestion();
															String tempMessage = "http://www.google.com/search?q="
																+ q.getQuestion().replace(' ', '+');
															
															String tempAnswer[] = new String[3];
															int max = 0;
															for(int i=0; i<3; i++) {
																if(q.getAnswer(i).length() > max) max = q.getAnswer(i).length();
																tempAnswer[i] = q.getAnswer(i);
															}
															
															delimiter = new String(new char[max]).replace('\0', '-');
															
															System.out.println("going into the printer check");
															if(!printed) {
																System.out.println("inside printed");
																printed = true;
																for(TextChannel channel : channels) {
																	channel.createMessage(spec -> spec.setEmbed(embed -> 
																	embed.setTitle(tempText)
																	.setUrl(tempMessage)
																	.addField(tempAnswer[0], delimiter, false)
																	.addField(tempAnswer[1], delimiter, false)
																	.addField(tempAnswer[2], delimiter, false))).block();
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
														String[] tempAnswers = new String[3];
														for(int i=0; i<3; i++) {
															tempAnswers[i] = q.getAnswer(i);
														}
														String payout = "Estimated payout: $" 
																+ String.format("%.2f", new Double(prize)
																/ new Double(q.getCount(q.getCorrectIndex())));
														
														if(printed) {
															printed = false;
															for(TextChannel channel : channels) {
																channel.createMessage(spec -> spec.setEmbed(embed -> 
																embed.setTitle(tempMessage)
																.addField(tempAnswers[0], q.getCorrectIndex() == 0 ? 
																		+ q.getCount(0) + " | CORRECT" : "" + q.getCount(0), false)
																.addField(tempAnswers[1], q.getCorrectIndex() == 1 ? 
																		q.getCount(1) + " | CORRECT" : "" + q.getCount(1), false)
																.addField(tempAnswers[2], q.getCorrectIndex() == 2 ? 
																		q.getCount(2) + " | CORRECT": "" + q.getCount(2), false)
																.setDescription(payout))).block();
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
														String tempAnswer = p.getAnswer().replace("\0", " \\_");
														delimiter = new String(new char[p.getAnswer().length()])
																.replace("\0", "\\_");
														
														if(!printed) {
															printed = true;															
															for(TextChannel channel : channels) {
																channel.createMessage(spec -> spec.setEmbed(embed -> 
																embed.setTitle(tempMessage)
																.addField(tempAnswer, delimiter, false))).block();
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
														
														String tempMessage = p.getHint();
														String tempAnswer = p.getAnswer();
														delimiter = new String(new char[p.getAnswer().length()])
																.replace('\0', '-');
														
														String payout = "Estimated Payout: $"
																+ String.format("%.2f", new Double(prize)
																/new Double(p.getSolved()));
														
														if(printed) {
															printed = false;
															for(TextChannel channel : channels) {
																channel.createMessage(spec -> spec.setEmbed(embed -> 
																embed.setTitle(tempMessage)
																.setDescription(payout + " | "
																+ p.getSolved() + " are still in the game")
																.addField(tempAnswer, delimiter, false))).block();
															}
														}

													}
												}
												
												else if(data.get("type").equals("broadcastEnded")) {
													if(data.get("reason") == null && data.size() == 4) ended++;
													else ended = 0;
												}
											} catch (Exception e) {
												e.printStackTrace();
												return;
											}
										}
									});
								} catch(Exception e) {
									e.printStackTrace();
								}								
							}
							
						}, cec, new URI(socketURL));
					}
					CountDownLatch latch = new CountDownLatch(1);
					latch.await(100, TimeUnit.SECONDS);
					ended = 0;
				}
			} catch(Exception e) {
				e.printStackTrace();
				return null;
			} 
			
		}
	}	
}