package com.bfh.solution.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class WorkflowService {

    private final RestTemplate restTemplate;

    @Value("${app.user.name}")
    private String userName;

    @Value("${app.user.regNo}")
    private String regNo;

    @Value("${app.user.email}")
    private String userEmail;

    @Value("${app.generateWebhook.url}")
    private String generateWebhookUrl;

    public WorkflowService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void execute() {
        Map<String, String> request = new HashMap<>();
        request.put("name", userName);
        request.put("regNo", regNo);
        request.put("email", userEmail);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

        Map<String, Object> response;
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(generateWebhookUrl, entity, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                System.err.println("Failed to generate webhook");
                return;
            }
            response = resp.getBody();
        } catch (RestClientException e) {
            System.err.println("Error calling generateWebhook API: " + e.getMessage());
            return;
        }

        String webhookUrl = Objects.toString(response.get("webhook"), null);
        String accessToken = Objects.toString(response.get("accessToken"), null);
        if (webhookUrl == null || accessToken == null) {
            System.err.println("Invalid response from webhook generator");
            return;
        }

        String finalQuery = selectQuery();

        HttpHeaders submitHeaders = new HttpHeaders();
        submitHeaders.setContentType(MediaType.APPLICATION_JSON);
        submitHeaders.set("Authorization", accessToken);

        Map<String, String> body = new HashMap<>();
        body.put("finalQuery", finalQuery);

        HttpEntity<Map<String, String>> submitEntity = new HttpEntity<>(body, submitHeaders);

        try {
            ResponseEntity<String> result = restTemplate.postForEntity(webhookUrl, submitEntity, String.class);
            System.out.println("Submission status: " + result.getStatusCode());
            System.out.println("Response: " + result.getBody());
        } catch (RestClientException e) {
            System.err.println("Error submitting final query: " + e.getMessage());
        }
    }

    private String selectQuery() {
        int lastDigit = extractLastDigit(regNo);
        if (lastDigit % 2 == 1) {
            return getQuestion1Query();
        } else {
            return getQuestion2Query();
        }
    }

    private int extractLastDigit(String input) {
        for (int i = input.length() - 1; i >= 0; i--) {
            if (Character.isDigit(input.charAt(i))) {
                return Character.getNumericValue(input.charAt(i));
            }
        }
        return 0;
    }

    private String getQuestion1Query() {
        return "SELECT p.amount AS salary, " +
                "CONCAT(e.first_name, ' ', e.last_name) AS name, " +
                "EXTRACT(YEAR FROM AGE(CURRENT_DATE, e.dob)) AS age, " +
                "d.department_name " +
                "FROM payments p " +
                "JOIN employee e ON e.emp_id = p.emp_id " +
                "JOIN department d ON d.department_id = e.department " +
                "WHERE EXTRACT(DAY FROM p.payment_time) <> 1 " +
                "ORDER BY p.amount DESC " +
                "LIMIT 1;";
    }
}