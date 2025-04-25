package com.bajajhealth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@SpringBootApplication
public class Application implements CommandLineRunner {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INIT_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Step 1: Initial request to get webhook, token, and user data
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("name", "John Doe");
        requestPayload.put("regNo", "REG12347");
        requestPayload.put("email", "john@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(INIT_URL, requestEntity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());

        String webhookUrl = root.get("webhook").asText();
        String accessToken = root.get("accessToken").asText();
        JsonNode usersNode = root.get("data").get("users");

        // Step 2: Build follow map
        Map<Integer, Set<Integer>> followMap = new HashMap<>();
        for (JsonNode user : usersNode) {
            int id = user.get("id").asInt();
            Set<Integer> follows = new HashSet<>();
            for (JsonNode follow : user.get("follows")) {
                follows.add(follow.asInt());
            }
            followMap.put(id, follows);
        }

        // Step 3: Find mutual followers
        Set<List<Integer>> mutualPairs = new HashSet<>();
        for (int userId : followMap.keySet()) {
            for (int followedId : followMap.get(userId)) {
                if (followMap.containsKey(followedId) && followMap.get(followedId).contains(userId)) {
                    int min = Math.min(userId, followedId);
                    int max = Math.max(userId, followedId);
                    mutualPairs.add(Arrays.asList(min, max));
                }
            }
        }

        // Step 4: Prepare final result JSON
        List<List<Integer>> finalResult = new ArrayList<>(mutualPairs);
        finalResult.sort(Comparator.comparingInt(a -> a.get(0)));

        Map<String, Object> result = new HashMap<>();
        result.put("regNo", "REG12347");
        result.put("outcome", finalResult);

        // Step 5: Send to webhook with retry (max 4 times)
        HttpHeaders webhookHeaders = new HttpHeaders();
        webhookHeaders.setContentType(MediaType.APPLICATION_JSON);
        webhookHeaders.set("Authorization", accessToken);

        HttpEntity<Map<String, Object>> webhookRequest = new HttpEntity<>(result, webhookHeaders);

        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                ResponseEntity<String> webhookResponse = restTemplate.postForEntity(webhookUrl, webhookRequest, String.class);
                if (webhookResponse.getStatusCode().is2xxSuccessful()) {
                    System.out.println("✅ Webhook POST successful on attempt " + attempt);
                    break;
                }
            } catch (Exception e) {
                System.out.println("⚠️ Webhook POST failed on attempt " + attempt + ", retrying...");
                Thread.sleep(1000);
            }
        }
    }
}
