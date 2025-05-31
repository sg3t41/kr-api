package com.example.kr_platform;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
public class CalendarService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_READONLY);

    @Value("${google.calendar.credentials.file.path}")
    private String credentialsFilePath;

    @Value("${google.calendar.application.name}")
    private String applicationName;

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = new ClassPathResource(credentialsFilePath).getInputStream();
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + credentialsFilePath);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        
        // Load stored credentials instead of prompting for authorization
        Credential credential = flow.loadCredential("user");
        if (credential == null) {
            throw new IOException("認証が必要です。まず /auth/google にアクセスしてGoogleアカウントを認証してください。");
        }
        
        // Debug: Print token information
        System.out.println("Access Token: " + (credential.getAccessToken() != null ? "Present" : "Null"));
        System.out.println("Refresh Token: " + (credential.getRefreshToken() != null ? "Present" : "Null"));
        System.out.println("Expires in: " + credential.getExpiresInSeconds());
        
        // Refresh the token if it's expired
        if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
            credential.refreshToken();
        }
        
        return credential;
    }

    public String getTodaysEvents() {
        return getEventsForDate(LocalDateTime.now(), "今日");
    }
    
    public String getTomorrowsEvents() {
        return getEventsForDate(LocalDateTime.now().plusDays(1), "明日");
    }
    
    private String getEventsForDate(LocalDateTime targetDate, String dateLabel) {
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(applicationName)
                    .build();

            LocalDateTime startOfDay = targetDate.withHour(0).withMinute(0).withSecond(0);
            LocalDateTime endOfDay = targetDate.withHour(23).withMinute(59).withSecond(59);
            
            DateTime timeMin = new DateTime(startOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            DateTime timeMax = new DateTime(endOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            
            // Debug: Print date range
            System.out.println("Searching for events between:");
            System.out.println("  Start: " + startOfDay + " (TimeZone: " + ZoneId.systemDefault() + ")");
            System.out.println("  End: " + endOfDay);
            System.out.println("  TimeMin: " + timeMin);
            System.out.println("  TimeMax: " + timeMax);

            Events events = service.events().list("primary")
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            List<Event> items = events.getItems();
            
            // Debug: Print event count
            System.out.println("Found " + items.size() + " events");

            if (items.isEmpty()) {
                return dateLabel + "の予定はありません。";
            } else {
                StringBuilder result = new StringBuilder();
                for (Event event : items) {
                    DateTime start = event.getStart().getDateTime();
                    if (start == null) {
                        start = event.getStart().getDate();
                        result.append(String.format("• %s (終日)", event.getSummary()));
                    } else {
                        LocalDateTime startTime = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(start.getValue()),
                            ZoneId.systemDefault()
                        );
                        
                        result.append(String.format("• %s (%s)", 
                            event.getSummary(),
                            startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                        ));
                    }
                    
                    // Add location if available
                    if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                        result.append(" 📍" + event.getLocation());
                    }
                    
                    result.append("\n");
                }
                return result.toString().trim();
            }
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            return "カレンダーの取得に失敗: " + e.getMessage();
        }
    }

    public String getUpcomingEvents(int maxResults) {
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(applicationName)
                    .build();

            DateTime now = new DateTime(System.currentTimeMillis());
            Events events = service.events().list("primary")
                    .setTimeMin(now)
                    .setMaxResults(maxResults)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            List<Event> items = events.getItems();

            if (items.isEmpty()) {
                return "今後の予定はありません。";
            } else {
                StringBuilder result = new StringBuilder();
                for (Event event : items) {
                    DateTime start = event.getStart().getDateTime();
                    if (start == null) {
                        start = event.getStart().getDate();
                    }
                    
                    LocalDateTime startTime = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(start.getValue()),
                        ZoneId.systemDefault()
                    );
                    
                    result.append(String.format("• %s (%s)\n", 
                        event.getSummary(),
                        startTime.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                    ));
                }
                return result.toString().trim();
            }
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            return "カレンダーの取得に失敗: " + e.getMessage();
        }
    }
}