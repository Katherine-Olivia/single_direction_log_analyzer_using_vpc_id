AWS VPC Flow Log Status Checker Using VPC ID (Single Directional)

This project retrieves and analyzes VPC Flow Logs from AWS CloudWatch Logs for a specific VPC, based on its VPC ID. It connects to AWS services using the AWS SDK for Java v2, fetches recent log entries via CloudWatch Logs Insights, parses the records, and categorizes traffic as ALLOWED (ACCEPT) or BLOCKED (REJECT). It is implemented in Java and structured as a Maven project.

Features:

Connects to AWS EC2 and CloudWatch Logs using AWS SDK v2

Retrieves the log group linked to a given VPC’s flow logs

Queries the last 24 hours of logs using CloudWatch Logs Insights

Parses and formats logs with relevant details (IPs, ports, protocol, action)

Classifies traffic entries as ALLOWED, BLOCKED, or ERROR

Summarizes results with counts of each category

Technologies Used:

Java 11 or later

AWS SDK for Java v2

Maven

CloudWatch Logs Insights

Project Structure:

single_direction_log_analyzer_using_vpc_id/
├── src/
│ └── main/
│ └── java/
│ └── com/example/cloudwatch/
│ └── App.java
├── pom.xml
├── sample_output_1.png
├── sample_output_2.png
└── README.txt

Flow Overview:

Set AWS region and hardcoded VPC ID.

Use EC2 client to describe flow logs and locate the log group.

Use CloudWatch Logs Insights to query the last 24 hours (up to 100 log events).

Wait for query completion and retrieve results.

Parse each flow log message to extract fields like version, source IP, destination IP, ports, protocol, and action.

Classify logs as ALLOWED (ACCEPT), BLOCKED (REJECT), or ERROR (if format is invalid).

Display formatted logs with timestamps.

Print final summary with counts of each category.

Expected Output:

[2025-06-13T12:00:45Z] ALLOWED: 10.0.0.1 -> 10.0.0.2 (Proto: 6, Ports: 443 -> 55000)
[2025-06-13T12:01:30Z] BLOCKED: 10.0.0.3 -> 10.0.0.5 (Proto: 17, Ports: 53 -> 56000)
...
20 allowed, 5 blocked, 2 errors

How to Run (via Maven):

Step 1: Compile the project
mvn clean compile

Step 2: Package the project
mvn clean install

Step 3: Run the main class
mvn exec:java -Dexec.mainClass="com.example.cloudwatch.App"

Note: Ensure the following plugin exists in your pom.xml:

<plugin> <groupId>org.codehaus.mojo</groupId> <artifactId>exec-maven-plugin</artifactId> <version>3.1.0</version> <configuration> <mainClass>com.example.cloudwatch.App</mainClass> </configuration> </plugin>
Assumptions:

Flow logs are already enabled for the VPC.

The VPC logs are being sent to a valid CloudWatch Logs group.

AWS credentials are configured and have permissions to access EC2 and CloudWatch Logs.

Log entries start with a version field (usually "2").

Dependencies:

Java 11 or later

Maven

AWS SDK for Java v2

Author:

Katherine Olivia
SDE Intern – Site24x7 (Zoho Corp)
Date: 13-06-2025

License:

This project is for educational and internal demonstration purposes. All AWS interactions are based on publicly documented APIs.