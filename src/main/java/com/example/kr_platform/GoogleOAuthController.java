package com.example.kr_platform;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;

@RestController
public class GoogleOAuthController {

    @Value("${google.calendar.credentials.file.path}")
    private String credentialsFilePath;
    
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String REDIRECT_URI = "http://localhost:8888/auth/google/callback";

    @GetMapping("/auth/google")
    public RedirectView initiateGoogleAuth(HttpSession session) throws Exception {
        GoogleAuthorizationCodeFlow flow = getGoogleAuthorizationFlow();
        
        String authUrl = flow.newAuthorizationUrl()
            .setRedirectUri(REDIRECT_URI)
            .build();
        
        return new RedirectView(authUrl);
    }

    @GetMapping("/auth/google/callback")
    public String handleGoogleCallback(
            @RequestParam("code") String authCode,
            @RequestParam(value = "error", required = false) String error,
            HttpSession session) {
        
        if (error != null) {
            return "認証がキャンセルされました: " + error;
        }
        
        try {
            GoogleAuthorizationCodeFlow flow = getGoogleAuthorizationFlow();
            
            GoogleTokenResponse tokenResponse = flow.newTokenRequest(authCode)
                .setRedirectUri(REDIRECT_URI)
                .execute();
            
            // トークンを保存
            flow.createAndStoreCredential(tokenResponse, "user");
            
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>認証成功</title>
                    <style>
                        body { font-family: Arial, sans-serif; padding: 20px; }
                        .success { color: green; }
                    </style>
                </head>
                <body>
                    <h1 class="success">Google認証成功！</h1>
                    <p>カレンダーへのアクセスが許可されました。</p>
                    <p>これでTwitterスケジュール機能が使えるようになりました。</p>
                    <p>以下のエンドポイントを試してください：</p>
                    <ul>
                        <li>POST /tweet-schedule - カレンダーから予定を取得してツイート</li>
                    </ul>
                    <p><a href="/">ホームに戻る</a></p>
                </body>
                </html>
                """;
            
        } catch (Exception e) {
            return "認証失敗: " + e.getMessage();
        }
    }
    
    private GoogleAuthorizationCodeFlow getGoogleAuthorizationFlow() throws Exception {
        var in = new ClassPathResource(credentialsFilePath).getInputStream();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
            GsonFactory.getDefaultInstance(), 
            new InputStreamReader(in)
        );
        
        File tokensDirectory = new File(TOKENS_DIRECTORY_PATH);
        
        return new GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(), 
            GsonFactory.getDefaultInstance(), 
            clientSecrets, 
            Collections.singletonList(CalendarScopes.CALENDAR_READONLY))
            .setDataStoreFactory(new FileDataStoreFactory(tokensDirectory))
            .setAccessType("offline")
            .build();
    }
}