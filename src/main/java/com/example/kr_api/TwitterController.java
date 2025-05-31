package com.example.kr_api;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
import com.github.scribejava.apis.TwitterApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@RestController
// @EnableScheduling - 定期実行を無効化
public class TwitterController {

	private final TwitterService twitterService;
	private final CalendarService calendarService;

	public TwitterController(TwitterService twitterService, CalendarService calendarService) {
		this.twitterService = twitterService;
		this.calendarService = calendarService;
	}

	@PostMapping("/tweet")
	public String tweet(@RequestParam("text") String text) {
		return twitterService.postTweet(text);
	}

	@PostMapping("/schedule-tweet")
	public String scheduleTweet(@RequestParam("text") String text, @RequestParam("minutes") int minutes) {
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(minutes * 60 * 1000L);
				twitterService.postTweet(text);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		return "Tweet scheduled for " + minutes + " minutes from now";
	}

	@GetMapping("/auto-tweet/status")
	public String getAutoTweetStatus() {
		return "Auto-tweeting is enabled. Tweets are posted every hour.";
	}

	@PostMapping("/tweet-schedule")
	public String tweetTodaysSchedule() {
		return twitterService.postScheduleTweet();
	}

	@PostMapping("/tweet-upcoming")
	public String tweetUpcomingEvents(@RequestParam(defaultValue = "5") int maxResults) {
		return twitterService.postUpcomingEventsTweet(maxResults);
	}

	@PostMapping("/tweet-tomorrow")
	public String tweetTomorrowsSchedule() {
		return twitterService.postTomorrowsTweet();
	}

	@GetMapping("/test-tomorrow")
	public String testTomorrowsSchedule() {
		String schedule = calendarService.getTomorrowsEvents();
		System.out.println("明日の予定: " + schedule);
		return "明日の予定: " + schedule;
	}

	@GetMapping("/test-today")
	public String testTodaysSchedule() {
		String schedule = calendarService.getTodaysEvents();
		System.out.println("今日の予定: " + schedule);
		return "今日の予定: " + schedule;
	}
}

@Service
// @EnableScheduling - 定期実行を無効化
class TwitterService {

	private final OAuth10aService service;
	private final OAuth1AccessToken accessToken;
	private final CalendarService calendarService;

	public TwitterService(
			@Value("${twitter.consumerKey}") String consumerKey,
			@Value("${twitter.consumerSecret}") String consumerSecret,
			@Value("${twitter.accessToken}") String accessToken,
			@Value("${twitter.accessTokenSecret}") String accessTokenSecret,
			CalendarService calendarService) {
		this.service = new ServiceBuilder(consumerKey)
				.apiSecret(consumerSecret)
				.build(TwitterApi.instance());
		this.accessToken = new OAuth1AccessToken(accessToken, accessTokenSecret);
		this.calendarService = calendarService;
	}

	public String postTweet(String text) {
		try {
			OAuthRequest request = new OAuthRequest(Verb.POST, "https://api.twitter.com/2/tweets");
			
			// JSONエスケープを正しく処理
			String escapedText = text.replace("\\", "\\\\")
								   .replace("\"", "\\\"")
								   .replace("\n", "\\n")
								   .replace("\r", "\\r")
								   .replace("\t", "\\t");
			
			request.setPayload("{\"text\":\"" + escapedText + "\"}");
			request.addHeader("Content-Type", "application/json");
			
			service.signRequest(accessToken, request);
			Response response = service.execute(request);
			
			if (response.isSuccessful()) {
				return "Tweet posted successfully: " + response.getBody();
			} else {
				return "Failed to post tweet: " + response.getCode() + " - " + response.getBody();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "Failed to post tweet: " + e.getMessage();
		}
	}

	public String postScheduleTweet() {
		String schedule = calendarService.getTodaysEvents();
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
		String tweetText = "📅 今日の予定 (" + timestamp + ")\n\n" + schedule + "\n\n#予定 #カレンダー";
		return postTweet(tweetText);
	}

	public String postUpcomingEventsTweet(int maxResults) {
		String events = calendarService.getUpcomingEvents(maxResults);
		String tweetText = "📋 今後の予定\n\n" + events + "\n\n#予定 #スケジュール";
		return postTweet(tweetText);
	}

	public String postTomorrowsTweet() {
		String schedule = calendarService.getTomorrowsEvents();
		String tweetText = "📅 明日の予定\n\n" + schedule + "\n\n#明日の予定 #カレンダー";
		return postTweet(tweetText);
	}

	// @Scheduled(fixedRate = 3600000) // 1時間ごと - 一時的に無効化
	@Async
	public void autoTweet() {
		String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		String autoTweetText = "定期ツイート: " + currentTime + " #自動投稿";
		
		try {
			OAuthRequest request = new OAuthRequest(Verb.POST, "https://api.twitter.com/2/tweets");
			request.setPayload("{\"text\":\"" + autoTweetText.replace("\"", "\\\"") + "\"}");
			request.addHeader("Content-Type", "application/json");
			
			service.signRequest(accessToken, request);
			Response response = service.execute(request);
			
			if (response.isSuccessful()) {
				System.out.println("Auto tweet posted: " + autoTweetText);
			} else {
				System.err.println("Failed to post auto tweet: " + response.getCode() + " - " + response.getBody());
			}
		} catch (Exception e) {
			System.err.println("Failed to post auto tweet: " + e.getMessage());
		}
	}

	// @Scheduled(cron = "0 0 9 * * ?") // 毎日朝9時 - 一時的に無効化
	@Async
	public void autoScheduleTweet() {
		try {
			postScheduleTweet();
			System.out.println("Daily schedule tweet posted");
		} catch (Exception e) {
			System.err.println("Failed to post daily schedule tweet: " + e.getMessage());
		}
	}
}
