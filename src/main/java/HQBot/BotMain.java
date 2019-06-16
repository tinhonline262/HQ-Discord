package HQBot;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
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
import javax.websocket.DeploymentException;

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
import discord4j.core.object.entity.TextChannel;
import reactor.core.publisher.Mono;

public class BotMain {
	private static final String BOT_TOKEN = "XXX";
	private static final String BEARER = "Bearer XXX";
	
	private final static ArrayList<TextChannel> triviaChannels = new ArrayList<TextChannel>();
	private final static ArrayList<TextChannel> wordsChannels = new ArrayList<TextChannel>();
	private final static HashMap<Long, Question> questions = new HashMap<Long, Question>();
	private final static HashMap<Long, Question> questionVerify = new HashMap<Long, Question>();
	private final static HashSet<Long> summaryIDs = new HashSet<Long>();
	private final static HashMap<Long, Puzzle> puzzles = new HashMap<Long, Puzzle>();
	private final static HashMap<Long, Puzzle> puzzleVerify = new HashMap<Long, Puzzle>();
	private final static HashSet<Long> roundIDs = new HashSet<Long>();
	private static int ended = 0;
	private static boolean printed = false;

	public static void main(String[] args) {
		final DiscordClient client = new DiscordClientBuilder(BOT_TOKEN).build();		
		
		client.getEventDispatcher().on(ReadyEvent.class)
			.subscribe(ready -> System.out.println("Logged in as " + ready.getSelf().getUsername()));
		
		
		//these two lines will allow you to hardcode channels that you always want in the lists at runtime
		
		//triviaChannels.add((TextChannel) client.getChannelById(Snowflake.of("XXX")).block());
		//wordsChannels.add((TextChannel) client.getChannelById(Snowflake.of("XXX")).block());
		
		// .check command checks that the bot is running
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
        	.filter(msg -> msg.getContent().map(".check"::equals).orElse(false))
        	.flatMap(Message::getChannel)
        	.flatMap(channel -> channel.createMessage("Yeah, yeah. I'm here."))
        	.subscribe();
		
		// .addTrivia command adds the channel to the list of trivia channels to output on
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
    		.filter(msg -> msg.getContent().map(".addTrivia"::equals).orElse(false))
    		.flatMap(Message::getChannel)
    		.flatMap(channel -> addTriviaChannel((TextChannel) channel))
    		.subscribe();
		
		// .addWords command adds the channel to the list of words channels to output on.
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
			.filter(msg -> msg.getContent().map(".addWords"::equals).orElse(false))
			.flatMap(Message::getChannel)
			.flatMap(channel -> addWordsChannel((TextChannel) channel))
			.subscribe();
		
		// .removeTrivia removes this channel from the list of channels that receive trivia output
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
			.filter(msg -> msg.getContent().map(".removeTrivia"::equals).orElse(false))
			.flatMap(Message::getChannel)
			.flatMap(channel -> removeTriviaChannel((TextChannel) channel))
			.subscribe();
		
		// .removeWords removes this channel from the list of channels that receive words output
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
			.filter(msg -> msg.getContent().map(".removeWords"::equals).orElse(false))
			.flatMap(Message::getChannel)
			.flatMap(channel -> removeWordsChannel((TextChannel) channel))
			.subscribe();
		
		// .checkGames command checks to see which game types you are subscribed to
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
			.filter(msg -> msg.getContent().map(".checkGames"::equals).orElse(false))
		    .flatMap(Message::getChannel)
		    .flatMap(channel -> checkChannelMembership((TextChannel) channel))
		    .subscribe();
		
		// .start tells the bot to start scanning for hq broadcasts.
		client.getEventDispatcher().on(MessageCreateEvent.class)
			.map(MessageCreateEvent::getMessage)
			.filter(msg->msg.getContent().map(".start"::equals).orElse(false))
			.flatMap(Message::getChannel)
			.flatMap(channel -> BotMain.hqListen((TextChannel) channel))
			.subscribe();
		
		client.login().block();
		
	}

	/**
	 * Tries to add the channel to the list of trivia channels to output on
	 * @param channel the TextChannel to add to the list
	 * @return an empty Mono<String>
	 */
	private static Mono<?> addTriviaChannel(TextChannel channel){
		if(triviaChannels.contains(channel)) {
			channel.createMessage("This channel is already outputting HQ Trivia questions").block();
			return Mono.just("");
		}
		triviaChannels.add(channel);
		channel.createMessage("This channel will now print HQ Trivia questions").block();
		return Mono.just("");
	}
	
	/**
	 * Tries to add the channel to the list of words channels to output on
	 * @param channel a TextChannel to add to the list
	 * @return a blank Mono<String>
	 */
	private static Mono<?> addWordsChannel(TextChannel channel){
		if(wordsChannels.contains(channel)) {
			channel.createMessage("This channel is already outputting HQ Words puzzles").block();
			return Mono.just("");
		}		
		wordsChannels.add(channel);
		channel.createMessage("This channel will now print HQ Words puzzles").block();
		return Mono.just("");
	}
	
	/**
	 * Tries to remove the channel from the list of trivia channels
	 * @param channel a TextChannel to remove from the list
	 * @return a blank Mono<String>
	 */
	private static Mono<?> removeTriviaChannel(TextChannel channel){
		for(TextChannel c : triviaChannels) {
			if(c.equals(channel)) {
				triviaChannels.remove(c);
				c.createMessage("This channel will no longer output HQ Trivia questions").block();
				return Mono.just("");
			}
		}
		channel.createMessage("This channel was not on the list of HQ Trivia channels").block();
		return Mono.just("");
	}
	
	/**
	 * Tries to remove the channel from the list of words channels
	 * @param channel a TextChannel to remove from the list
	 * @return a blank Mono<String>
	 */
	private static Mono<?> removeWordsChannel(TextChannel channel){
		for(TextChannel c : wordsChannels) {
			if(c.equals(channel)) {
				wordsChannels.remove(c);
				c.createMessage("This channel will no longer output HQ Words puzzles").block();
				return Mono.just("");
			}
		
		}
		channel.createMessage("This channel was not on the list of HQ Words channels").block();
		return Mono.just("");
	}
	
	/**
	 * Check to see which game types you are subscribed to
	 * @param channel
	 * @return
	 */
	private static Mono<?> checkChannelMembership(TextChannel channel){
		if(!triviaChannels.contains(channel) && !wordsChannels.contains(channel)) {
			channel.createMessage("You aren't currently getting any game messages on this channel").block();
			return Mono.just("");
		}
		channel.createMessage("You are getting messages from "
				+ (triviaChannels.contains(channel) ? " TRIVIA " : "") + (wordsChannels.contains(channel) ? " WORDS" : ""))
				.block();
		return Mono.just("");
	}
	
	/**
	 * This method runs indefinitely once started and listens to HQs API address for broadcasts
	 * When a broadcast begins, notifies the appropriate Discord channels that a game is live
	 * and prints game info to channels as it becomes available.
	 * @return
	 */
	private static Mono<?> hqListen(TextChannel c) {
		
		if(!triviaChannels.contains(c) && !wordsChannels.contains(c)) {
			c.createMessage("I'm starting up the HQ engine, but be warned that this channel isn't"
					+ " currently receiving Trivia OR Words output").block();
		}
		else c.createMessage("Starting up the HQ engine").block();		
			
		//just a loop to keep the tool running once it starts
		while(true) {
			//clears out question IDs for new puzzles
			questions.clear();
			summaryIDs.clear();
			puzzles.clear();
			roundIDs.clear();
			questionVerify.clear();
			puzzleVerify.clear();
			
			//Create an Http client and send a request to HQ servers
			//using our bearer token and the hq client
			HttpClient client = HttpClients.custom().build();
			HttpUriRequest request = RequestBuilder.get()
					.setUri("https://api-quiz.hype.space/shows/now?type=")
					.setHeader(HttpHeaders.CONTENT_TYPE, "Authorization: "
					+ BEARER + "\nx-hq-client: iOS/1.4.14b145")
					.build();
	
			HttpResponse response;
			
			//execute the request and try to parse it
			try {
				response = client.execute(request);
				HttpEntity entity = response.getEntity();
				
				if(entity != null) {
					
					//print the output to console to help with debugging
					InputStream in = entity.getContent();
					JSONObject jo = (JSONObject) new JSONParser()
							.parse(new InputStreamReader(in));
					for(Object o : jo.keySet()) {
						System.out.println(o + " : " + jo.get(o));
					}
					
					String socketURL = null;
					JSONObject broadcast = (JSONObject) jo.get("broadcast");
					
					//if no broadcast is happening, wait 5 seconds and start the loop over
					if(broadcast == null) {
						System.out.println("No Broadcast currently, trying again in 5 seconds");
						
						CountDownLatch latch = new CountDownLatch(1);
						latch.await(5, TimeUnit.SECONDS);
						continue;					
					}
					
					if(((String) jo.get("gameType")).equals("trivia")) {
						for(TextChannel channel : triviaChannels) {
							channel.createMessage(spec -> spec.setEmbed(embed ->
								embed.addField("Get Ready!", "HQ " + jo.get("gameType") 
								+ " broadcast is live", false))).block();
						}
					}
					else if (((String) jo.get("gameType")).equals("words")) {
						for(TextChannel channel : triviaChannels) {
							channel.createMessage(spec -> spec.setEmbed(embed ->
								embed.addField("Get Ready!", "HQ " + jo.get("gameType") 
								+ " broadcast is live", false))).block();
						}
					}
					
					//grab the socketUrl, replace https with wss
					socketURL = (String) broadcast.get("socketUrl");
					hqGame(socketURL, jo);					
				}
			} catch(Exception e) {
				e.printStackTrace();
			} 
			
		}
	}

	/**
	 * Given the API JSON and the websocket to connect to, connects to the websocket
	 * and then calls methods to parse the incoming JSON objects
	 * @param socketURL a string representing the websocket given by the API's JSON
	 * @param jo the API's JSON
	 * @throws DeploymentException
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	private static void hqGame(String socketURL, JSONObject jo) 
			throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		
		socketURL = socketURL.substring(5);
		socketURL = "wss" + socketURL;
		//once we have the socketurl we can connect to it
		
		//this bit just ensures that we won't try to disconnect entirely until we have
		//received enough consecutive "broadcastEnded" type transmissions to ensure
		//that the broadcast is over rather than just corrupted data coming through.
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
							public void onMessage(String message) {
								
								//if it's a new question, print the question and answers to console
								try {
									JSONObject data = (JSONObject) new JSONParser()
											.parse(message);
									Long prize = (Long) jo.get("prize");									
									
									hqParse(data, prize);
									
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
		
		//after the broadcast has ended, this 100 second timer is short enough that
		//it won't miss part of any immediately subsequent games but long enough for api
		//to change the websocket so we don't try to reconnect or connect to a websocket
		//that no longer exists. We then take ended back to 0 and start listening
		//for broadcasts again.
		CountDownLatch latch = new CountDownLatch(1);
		latch.await(100, TimeUnit.SECONDS);
		ended = 0;		
	}
	
	/**
	 * Parses the JSON object and outputs words/trivia data to the appropriate channels
	 * @param data the incoming JSON 
	 * @param prize the total prize amount for the puzzle
	 */
	public static void hqParse(JSONObject data, Long prize) {
		
		//This bit handles parsing and output formatting for initial question data for HQ Trivia
		if(data.get("type").equals("question")){
			if(!questions.containsKey((Long) data.get("questionId"))) {
				
				Question q = new Question(data);
					questions.put(q.getID(), q);
					
					//gets the question and formats a google link for easy
					//searching
					String tempText = q.getQuestion();
					String tempMessage = "http://www.google.com/search?q="
						+ q.getQuestion().replace(' ', '+');
					
					String tempAnswer[] = new String[3];
					int max = 0;
					for(int i=0; i<3; i++) {
						if(q.getAnswer(i).length() > max) max = q.getAnswer(i).length();
						tempAnswer[i] = q.getAnswer(i);
					}
					
					String delimiter = new String(new char[max]).replace('\0', '-');
					
					//This just helps control possible double printing
					if(!printed) {
						printed = true;
						
						//formats the data into an embed object to be sent to the correct channels
						for(TextChannel channel : triviaChannels) {
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
					for(TextChannel channel : triviaChannels) {
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
				
				
				String tempMessage = p.getHint();
				String tempAnswer = p.getAnswer();
				//TODO figure out better formatting for the words output.
				//tempAnswer = tempAnswer.replace("-", "  \\_\\_");
				String delimiter = new String(new char[p.getAnswer().length()])
						.replace("\0", "\\_");
				
				if(!printed) {
					printed = true;															
					for(TextChannel channel : wordsChannels) {
						String s = tempAnswer;
						channel.createMessage(spec -> spec.setEmbed(embed -> 
						embed.setTitle(tempMessage)
						.addField(s, delimiter, false))).block();
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
				String delimiter = new String(new char[p.getAnswer().length()])
						.replace('\0', '-');
				
				String payout = "Estimated Payout: $"
						+ String.format("%.2f", new Double(prize)
						/new Double(p.getSolved()));
				
				if(printed) {
					printed = false;
					for(TextChannel channel : wordsChannels) {
						channel.createMessage(spec -> spec.setEmbed(embed -> 
						embed.setTitle(tempMessage)
						.setDescription(payout + " | "
						+ p.getSolved() + " are still in the game")
						.addField(tempAnswer, delimiter, false))).block();
					}
				}

			}
		}
		
		//when we receive the right type of broadcastEnded transmission,
		//we increment the ended count.
		else if(data.get("type").equals("broadcastEnded")) {
			if(data.get("reason") == null && data.size() == 4) ended++;
			else ended = 0;
		}
		
	}
}
