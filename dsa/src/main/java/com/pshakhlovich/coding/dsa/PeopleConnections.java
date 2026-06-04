package com.pshakhlovich.coding.dsa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// You are analyzing data for Aquaintly, a hot new social network.

// On Aquaintly, connections are always symmetrical. If a user Alice is connected to Bob, then Bob is also connected to Alice.

// You are given a sequential log of CONNECT and DISCONNECT events of the following form:
// - This event connects users Alice and Bob: ["CONNECT", "Alice", "Bob"]
// - This event disconnects the same users: ["DISCONNECT", "Bob", "Alice"] (order of users does not matter)

// We want to separate users based on their popularity (number of connections). To do this, write a function that takes in the event log and a number N and returns two collections:
// [Users with fewer than N connections], [Users with N or more connections]

// Example:
// events = [
//     ["CONNECT","Alice 0","Bob 0"],
//     ["DISCONNECT","Bob","Alice"],
//     ["CONNECT","Alice 1","Charlie 1"],
//     ["CONNECT","Dennis 2","Bob 1"],
//     ["CONNECT","Pam","Dennis"],
//     ["DISCONNECT","Pam","Dennis"],
//     ["CONNECT","Pam","Dennis"],
//     ["CONNECT","Edward","Bob"],
//     ["CONNECT","Dennis","Charlie"],
//     ["CONNECT","Alice","Nicole"],
//     ["CONNECT","Pam","Edward"],
//     ["DISCONNECT","Dennis","Charlie"],
//     ["CONNECT","Dennis","Edward"],
//     ["CONNECT","Charlie","Bob"]
// ]

// Using a target of 3 connections, the expected results are:
// Users with less than 3 connections: ["Alice", "Charlie", "Pam", "Nicole"]
// Users with 3 or more connections: ["Dennis", "Bob", "Edward"]

// All test cases:
// grouping(events, 3) => [["Alice", "Charlie", "Pam", "Nicole"], ["Dennis", "Bob", "Edward"]]
// grouping(events, 1) => [[], ["Alice", "Charlie", "Dennis", "Bob", "Pam", "Edward", "Nicole"]]
// grouping(events, 10) => [["Alice", "Charlie", "Dennis", "Bob", "Pam", "Edward", "Nicole"], []]
// Complexity Variable:
// E = number of events

// O(E + U) - time Complexity
// O(U) - space Complexity


public class PeopleConnections {

    private static final String[][] EVENTS = {
        {"CONNECT", "Alice", "Bob"},
        {"DISCONNECT", "Bob", "Alice"},
        {"CONNECT", "Alice", "Charlie"},
        {"CONNECT", "Dennis", "Bob"},
        {"CONNECT", "Pam", "Dennis"},
        {"DISCONNECT", "Pam", "Dennis"},
        {"CONNECT", "Pam", "Dennis"},
        {"CONNECT", "Edward", "Bob"},
        {"CONNECT", "Dennis", "Charlie"},
        {"CONNECT", "Alice", "Nicole"},
        {"CONNECT", "Pam", "Edward"},
        {"DISCONNECT", "Dennis", "Charlie"},
        {"CONNECT", "Dennis", "Edward"},
        {"CONNECT", "Charlie", "Bob"}
    };

    public static void main(String[] argv) {
        //  grouping(events, 3) => [["Alice", "Charlie", "Pam", "Nicole"], ["Dennis", "Bob", "Edward"]]
        // grouping(events, 1) => [[], ["Alice", "Charlie", "Dennis", "Bob", "Pam", "Edward", "Nicole"]]
        // grouping(events, 10) => [["Alice", "Charlie", "Dennis", "Bob", "Pam", "Edward", "Nicole"], []]
        System.out.print(grouping(EVENTS, 3));
    }

    public static List<List<String>> grouping(String[][] events, int n) {
        List<String> lt = new ArrayList<>();
        List<String> gte = new ArrayList<>();


        Map<String, Integer> connections = new HashMap<>();


        for (String[] event : events) {
            var action = event[0];
            var user1 = event[1];
            var user2 = event[2];

            if (action.equals("CONNECT")) {
                connections.merge(user1, 1, Integer::sum);
                connections.merge(user2, 1, Integer::sum);
            }

            if (action.equals("DISCONNECT")) {
                connections.merge(user1, -1, Integer::sum);
                connections.merge(user2, -1, Integer::sum);
            }
        }


        for (Map.Entry<String, Integer> entry : connections.entrySet()) {
            if (entry.getValue() < n) {
                lt.add(entry.getKey());
            } else {
                gte.add(entry.getKey());
            }
        }
        return List.of(lt, gte);
    }
}
