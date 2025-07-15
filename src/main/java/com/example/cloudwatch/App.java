package com.example.cloudwatch;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class App{
    public static void main(String[] args) {
        // Entering the region and vpc id
        Region region = Region.EU_NORTH_1;
        String vpcId = "vpc-07aac8a420680bc29";//change the vpc id to the one to be analyzed

        // creating cw,ec2 clients to connect the ec2 to obtain logs
        try (
            Ec2Client ec2 = Ec2Client.builder().region(region).build();
            CloudWatchLogsClient logs = CloudWatchLogsClient.builder().region(region).build()
        ) {
            // -finding the cwlog group which is linked to this vpc
            DescribeFlowLogsRequest flowLogsReq = DescribeFlowLogsRequest.builder()
                    .filter(Filter.builder().name("resource-id").values(vpcId).build())
                    .build();

            DescribeFlowLogsResponse flowLogsResp = ec2.describeFlowLogs(flowLogsReq);

            // incase of no logs found
            if (flowLogsResp.flowLogs().isEmpty()) {
                throw new RuntimeException("No flow logs found for VPC: " + vpcId);
            }

            String logGroupName = flowLogsResp.flowLogs().get(0).logGroupName();
            if (logGroupName == null || logGroupName.isEmpty()) {
                throw new RuntimeException("Log group name not set for VPC flow logs.");
            }

            // cwloginsight for the past 1 hr (only recent log flows limited to 100 ) 
            String query = "fields @timestamp, @message " +
                           "| filter @message like /^2/ " +  // only logs starting with 2 (flow logs mostly start with 2)
                           "| sort @timestamp desc " +
                           "| limit 100";

            // Qlogs only from the last 1 hr 
            long endTime = Instant.now().getEpochSecond();
            long startTime = endTime - 86400;

            StartQueryRequest startQueryReq = StartQueryRequest.builder()
                    .logGroupName(logGroupName)
                    .startTime(startTime)
                    .endTime(endTime)
                    .queryString(query)
                    .build();

            String queryId = logs.startQuery(startQueryReq).queryId();

            // Poll Cloudwatch Logs until query completes
            GetQueryResultsResponse queryResults;
            do {
                Thread.sleep(2000); // wait 2 seconds before checking again
                queryResults = logs.getQueryResults(
                        GetQueryResultsRequest.builder().queryId(queryId).build());
            } while (queryResults.status() == QueryStatus.RUNNING);

            List<List<ResultField>> results = queryResults.results();
            //incase logs are empty

            if (results.isEmpty()) {
                System.out.println("No results found.");
                return;
            }

            // count no of allowed, blocked, and error log entries
            int allowedCount = 0, blockedCount = 0, errorCount = 0;

            // process each log entry from query results
            for (List<ResultField> fields : results) {
                String timestamp = "", message = "";

                // Extract timestamp and message fields
                for (ResultField field : fields) {
                    if ("@timestamp".equals(field.field())) {
                        timestamp = field.value() != null ? field.value() : "";
                    } else if ("@message".equals(field.field())) {
                        message = field.value() != null ? field.value() : "";
                    }
                }

                if (!message.isEmpty()) {
                    try {
                        VpcLogEntry entry = parseVpcLog(message);
                        if (entry == null) {
                            throw new RuntimeException("Invalid log entry: could not parse.");
                        }

                        // Validate important fields are present
                        if (entry.srcIp.isEmpty() || entry.dstIp.isEmpty() ||
                            entry.srcPort.isEmpty() || entry.dstPort.isEmpty() ||
                            entry.protocol.isEmpty() || entry.action.isEmpty()) {
                            throw new RuntimeException("Missing required fields in log entry.");
                        }

                        //results to be displayed
                        String status;
                        if ("ACCEPT".equalsIgnoreCase(entry.action)) {
                            status = "ALLOWED";
                            allowedCount++;
                        } else if ("REJECT".equalsIgnoreCase(entry.action)) {
                            status = "BLOCKED";
                            blockedCount++;
                        } else {
                            throw new RuntimeException("Unknown action in log: " + entry.action);
                        }

                        // Print formatted log entry info
                        System.out.printf("[%s] %s: %s -> %s (Proto: %s, Ports: %s -> %s)%n",
                                timestamp, status, entry.srcIp, entry.dstIp,
                                entry.protocol, entry.srcPort, entry.dstPort);

                    } catch (Exception e) {
                        errorCount++;
                        System.err.println("Error processing log: " + e.getMessage());
                    }
                }
            }

            // Print summary of all entries processed
            System.out.printf("%d allowed, %d blocked, %d errors%n", allowedCount, blockedCount, errorCount);

            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

private static final Map<String, Integer> VPC_FIELD_INDEX = Map.ofEntries(
    Map.entry("version", 0),
    Map.entry("accountId", 1),
    Map.entry("interfaceId", 2),
    Map.entry("srcIp", 3),
    Map.entry("dstIp", 4),
    Map.entry("srcPort", 5),
    Map.entry("dstPort", 6),
    Map.entry("protocol", 7),
    Map.entry("packets", 8),
    Map.entry("bytes", 9),
    Map.entry("startTime", 10),
    Map.entry("endTime", 11),
    Map.entry("action", 12),
    Map.entry("logStatus", 13)
);


private static class VpcLogEntry {
    String version, accountId, interfaceId, srcIp, dstIp, srcPort, dstPort;
    String protocol, packets, bytes, startTime, endTime, action, logStatus;
}

private static VpcLogEntry parseVpcLog(String logMsg) {
    String[] parts = logMsg.trim().split("\\s+");
    VpcLogEntry entry = new VpcLogEntry();

    entry.version     = safeGet(parts, VPC_FIELD_INDEX.get("version"));
    entry.accountId   = safeGet(parts, VPC_FIELD_INDEX.get("accountId"));
    entry.interfaceId = safeGet(parts, VPC_FIELD_INDEX.get("interfaceId"));
    entry.srcIp       = safeGet(parts, VPC_FIELD_INDEX.get("srcIp"));
    entry.dstIp       = safeGet(parts, VPC_FIELD_INDEX.get("dstIp"));
    entry.srcPort     = safeGet(parts, VPC_FIELD_INDEX.get("srcPort"));
    entry.dstPort     = safeGet(parts, VPC_FIELD_INDEX.get("dstPort"));
    entry.protocol    = safeGet(parts, VPC_FIELD_INDEX.get("protocol"));
    entry.packets     = safeGet(parts, VPC_FIELD_INDEX.get("packets"));
    entry.bytes       = safeGet(parts, VPC_FIELD_INDEX.get("bytes"));
    entry.startTime   = safeGet(parts, VPC_FIELD_INDEX.get("startTime"));
    entry.endTime     = safeGet(parts, VPC_FIELD_INDEX.get("endTime"));
    entry.action      = safeGet(parts, VPC_FIELD_INDEX.get("action"));
    entry.logStatus   = safeGet(parts, VPC_FIELD_INDEX.get("logStatus"));

    return entry;
}

// Helper to avoid IndexOutOfBoundsException
private static String safeGet(String[] array, int index) {
    return index < array.length ? array[index] : null;
}
} 